package eu.siacs.conversations.ui

/** Sending becomes available after one full second of active recording. */
object VoiceRecordingPolicy {
    const val MIN_DURATION_MS = 1_000L

    @JvmStatic
    fun canSend(durationMs: Long): Boolean = durationMs >= MIN_DURATION_MS

    fun activeDuration(startMs: Long, nowMs: Long, pausedMs: Long, pauseStartMs: Long): Long =
        (nowMs - startMs - pausedMs - if (pauseStartMs > 0L) nowMs - pauseStartMs else 0L).coerceAtLeast(0L)
}
