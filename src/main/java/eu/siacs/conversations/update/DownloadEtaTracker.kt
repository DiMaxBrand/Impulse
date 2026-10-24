package eu.siacs.conversations.update

import android.content.Context
import android.text.format.Formatter
import eu.siacs.conversations.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/** What the tracker has to say about a running download right now. */
class DownloadEstimate(
    val bytesPerSecond: Double,
    val etaSeconds: Double,
    val state: State,
    /** Based mostly on what earlier downloads did, not yet on this one. */
    val rough: Boolean,
) {
    enum class State { CALCULATING, RUNNING, STALLED }
}

/**
 * Tracks download throughput and a calm ETA across repeated polls of the same download.
 * (Shared by every screen that polls [UpdateDownloader.queryProgress], so they cannot drift apart.)
 *
 *  - **Nothing too early**: until a couple of seconds and a little data have gone by the speed is
 *    still ramping up; the estimate then rests on [priorBps] (what earlier downloads did), blending
 *    to the measured speed as it proves itself, or says "calculating".
 *  - **Two speeds**: a short one (about 4 s) for reaction and a long one (about 20 s) for steadiness,
 *    leaning on the long one when the short one swings.
 *  - **Hysteresis**: the shown countdown ticks down by itself, follows an estimate that is lower at
 *    once, and only follows a higher one after it has stayed higher for several polls.
 *  - **Stalls**: when no bytes arrive for a few seconds it says so and keeps the last good speed, so
 *    the estimate carries on smoothly when data resumes.
 */
class DownloadEtaTracker(private val priorBps: Double? = null) {
    private val samples = ArrayDeque<Pair<Long, Long>>() // elapsedRealtime ms to downloadedBytes
    private var startedAt = 0L
    private var startBytes = 0L
    private var lastProgressAt = 0L
    private var lastBytes = -1L
    private var lastGoodBps: Double? = null
    private var displayedEtaSeconds: Double? = null
    private var lastPollAt = 0L
    private var higherPolls = 0
    private var inactiveSince = 0L

    /** The speed over the whole run so far (bytes/s), for the history; null when it is too short to mean anything. */
    fun averageBps(nowMs: Long, downloadedBytes: Long): Double? {
        if (startedAt == 0L) return null
        val seconds = (nowMs - startedAt) / 1000.0
        val bytes = downloadedBytes - startBytes
        return if (seconds >= 3.0 && bytes > 0) bytes / seconds else null
    }

    fun sample(
        nowElapsedRealtimeMs: Long,
        downloadedBytes: Long,
        totalBytes: Long,
        activelyRunning: Boolean,
    ): DownloadEstimate? {
        val now = nowElapsedRealtimeMs
        if (!activelyRunning || totalBytes <= 0) {
            // Queued, paused or waiting for the network: nothing to show; what was measured is kept
            // for a short pause and dropped after a long one (the connection may be a different one).
            if (inactiveSince == 0L) inactiveSince = now
            if (now - inactiveSince > 10_000L) restart()
            return null
        }
        if (inactiveSince != 0L && now - inactiveSince > 10_000L) restart()
        inactiveSince = 0L

        if (startedAt == 0L) {
            startedAt = now
            startBytes = downloadedBytes
            lastProgressAt = now
        }
        if (downloadedBytes != lastBytes) {
            lastProgressAt = now
            lastBytes = downloadedBytes
        }
        samples.addLast(now to downloadedBytes)
        while (samples.isNotEmpty() && now - samples.first().first > LONG_WINDOW_MS) samples.removeFirst()

        val elapsedSincePoll = if (lastPollAt != 0L) (now - lastPollAt) / 1000.0 else 0.0
        lastPollAt = now
        val remaining = (totalBytes - downloadedBytes).coerceAtLeast(0)

        // Stalled: bytes have stopped. The last good speed is kept, the countdown holds.
        if (now - lastProgressAt >= STALL_MS) {
            val bps = lastGoodBps ?: priorBps ?: return DownloadEstimate(0.0, 0.0, DownloadEstimate.State.STALLED, true)
            val held = displayedEtaSeconds ?: (remaining / bps)
            return DownloadEstimate(bps, held, DownloadEstimate.State.STALLED, lastGoodBps == null)
        }

        val shortBps = rateOver(now, downloadedBytes, SHORT_WINDOW_MS)
        val longBps = rateOver(now, downloadedBytes, LONG_WINDOW_MS)
        val runSeconds = (now - startedAt) / 1000.0
        val warm = runSeconds >= WARM_UP_SECONDS && downloadedBytes - startBytes >= WARM_UP_BYTES

        val measured: Double? = when {
            longBps == null -> shortBps
            shortBps == null -> longBps
            else -> {
                // Leans on the long speed, more so when the two disagree.
                val swing = abs(shortBps - longBps) / max(longBps, 1.0)
                val wShort = if (swing < 0.2) 0.5 else 0.25
                wShort * shortBps + (1 - wShort) * longBps
            }
        }

        val bps: Double
        val rough: Boolean
        if (!warm || measured == null || measured <= 0.0) {
            // Still ramping up: a guess from earlier downloads, or nothing at all.
            val prior = priorBps
            if (prior == null || prior <= 0.0) {
                return DownloadEstimate(0.0, 0.0, DownloadEstimate.State.CALCULATING, true)
            }
            bps = prior
            rough = true
        } else {
            // The prior fades out over the first stretch of the run as the measurement proves itself.
            val prior = priorBps
            val trust = (runSeconds / PRIOR_FADE_SECONDS).coerceIn(0.0, 1.0)
            bps = if (prior != null && prior > 0.0) trust * measured + (1 - trust) * prior else measured
            rough = prior != null && trust < 0.5
            lastGoodBps = measured
        }

        val raw = remaining / bps
        val current = displayedEtaSeconds
        val eta = if (current == null) {
            raw
        } else {
            val ticked = (current - elapsedSincePoll).coerceAtLeast(0.0)
            when {
                // Lower than the countdown: follow it at once (quickly, but not in a jump).
                raw <= ticked -> {
                    higherPolls = 0
                    ticked + (raw - ticked) * 0.35
                }
                // Clearly higher: only after it has stayed higher for several polls in a row.
                raw > ticked * 1.18 + 1.0 -> {
                    higherPolls++
                    if (higherPolls >= HIGHER_POLLS_NEEDED) ticked + (raw - ticked) * 0.25 else ticked
                }
                else -> {
                    higherPolls = 0
                    ticked + (raw - ticked) * 0.25
                }
            }
        }
        displayedEtaSeconds = eta
        return DownloadEstimate(bps, eta, DownloadEstimate.State.RUNNING, rough)
    }

    private fun rateOver(now: Long, bytes: Long, windowMs: Long): Double? {
        val oldest = samples.firstOrNull { now - it.first <= windowMs } ?: return null
        val deltaMs = now - oldest.first
        val deltaBytes = bytes - oldest.second
        return if (deltaMs > 500 && deltaBytes >= 0) deltaBytes * 1000.0 / deltaMs else null
    }

    private fun restart() {
        samples.clear()
        startedAt = 0L
        lastBytes = -1L
        displayedEtaSeconds = null
        lastPollAt = 0L
        higherPolls = 0
        inactiveSince = 0L
        // lastGoodBps stays: it is the best guess for the next stretch.
    }

    fun reset() {
        restart()
        lastGoodBps = null
    }

    private companion object {
        const val SHORT_WINDOW_MS = 4_000L
        const val LONG_WINDOW_MS = 20_000L
        const val STALL_MS = 3_000L
        const val WARM_UP_SECONDS = 2.5
        const val WARM_UP_BYTES = 128 * 1024L
        const val PRIOR_FADE_SECONDS = 8.0
        const val HIGHER_POLLS_NEEDED = 4
    }
}

/**
 * "2.1 MB/s · about 45 s left · done in about 2 min": the speed, the time to download rounded the way
 * its size deserves (exact when short, to 5 s, to 10 s, then to whole minutes, so it does not claim
 * more than it knows), and, when this phone knows how long installs take here, the whole way to done.
 */
fun formatSpeedAndEta(context: Context, estimate: DownloadEstimate, installSeconds: Double? = null): String? {
    return when (estimate.state) {
        DownloadEstimate.State.CALCULATING -> context.getString(R.string.download_eta_calculating)
        DownloadEstimate.State.STALLED -> context.getString(R.string.download_eta_stalled)
        DownloadEstimate.State.RUNNING -> {
            if (estimate.bytesPerSecond <= 0.0) return null
            val speed = Formatter.formatShortFileSize(context, estimate.bytesPerSecond.toLong()) + "/s"
            val eta = estimate.etaSeconds
            val left = formatLeft(context, eta, estimate.rough)
            val whole = installSeconds?.let { formatDuration(context, eta + it) }
            buildString {
                append(speed).append(" · ").append(left)
                if (whole != null) append(" · ").append(context.getString(R.string.download_eta_done_in, whole))
            }
        }
    }
}

/** "12s left", "about 45s left", "about 1m 30s left", "about 4 min left", "about 1h 20m left". */
private fun formatLeft(context: Context, etaSecondsRaw: Double, rough: Boolean): String {
    val s = etaSecondsRaw.roundToLong().coerceAtLeast(0)
    return when {
        s < 20 && !rough -> context.getString(R.string.download_eta_seconds, s)
        s < 60 -> context.getString(R.string.download_eta_about_seconds, roundTo(s, 5))
        s < 300 -> {
            val r = roundTo(s, 10)
            context.getString(R.string.download_eta_about_minutes_seconds, r / 60, r % 60)
        }
        s < 3600 -> context.getString(R.string.download_eta_about_minutes, ((s + 30) / 60))
        else -> context.getString(R.string.download_eta_hours_minutes, s / 3600, (s % 3600) / 60)
    }
}

/** A bare duration for "done in ...": "40 s", "about 2 min", "about 1 h 20 m". */
private fun formatDuration(context: Context, secondsRaw: Double): String {
    val s = secondsRaw.roundToLong().coerceAtLeast(0)
    return when {
        s < 60 -> context.getString(R.string.download_duration_seconds, roundTo(s, 5).coerceAtLeast(5))
        s < 3600 -> context.getString(R.string.download_duration_minutes, ((s + 30) / 60))
        else -> context.getString(R.string.download_duration_hours_minutes, s / 3600, (s % 3600) / 60)
    }
}

private fun roundTo(value: Long, step: Long): Long = ((value + step / 2) / step) * step
