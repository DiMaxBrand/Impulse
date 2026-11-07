package eu.siacs.conversations.ui

import android.media.MediaPlayer
import android.util.Log
import eu.siacs.conversations.Config
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Single shared audio player for voice-message bubbles. Ensures only one message
 * plays at a time, and distinguishes a scroll-triggered pause (resumes automatically
 * when the bubble re-enters view) from a user/background pause (stays paused, but
 * keeps its position).
 */
object AudioPlaybackController {
    var activeUuid by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    val positions = mutableStateMapOf<String, Int>()
    val durations = mutableStateMapOf<String, Int>()
    val failedMessages = mutableStateMapOf<String, Boolean>()

    private val resumableOnReturn = mutableSetOf<String>()
    private var player: MediaPlayer? = null
    internal var playerFactory: () -> MediaPlayer = { MediaPlayer() }

    /** Invoked on the main thread when a message finishes playing naturally (not paused/stopped). */
    var onCompletion: ((completedUuid: String) -> Unit)? = null

    /** Playback state transitions (wire tokens from [ListenStatusManager]): "listening" on
     * start/resume, "paused" on any pause, "listened" on natural completion. The fragment
     * decides whether the message qualifies for a listen-status stanza (incoming, audio, 1:1)
     * — this controller just reports raw transitions. */
    var onTransition: ((uuid: String, wireState: String) -> Unit)? = null

    fun positionFor(uuid: String): Int {
        val saved = positions[uuid] ?: 0
        if (activeUuid != uuid) return saved
        return try { player?.currentPosition ?: saved } catch (_: IllegalStateException) {
            fail(uuid)
            saved
        }
    }

    fun toggle(uuid: String, file: File) {
        if (activeUuid == uuid && isPlaying) {
            pause(uuid, userInitiated = true)
        } else {
            play(uuid, file)
        }
    }

    fun seekTo(uuid: String, file: File, positionMs: Int) {
        val mp = ensurePlayer(uuid, file) ?: return
        try {
            val target = positionMs.coerceIn(0, durations[uuid] ?: Int.MAX_VALUE)
            mp.seekTo(target)
            positions[uuid] = target
        } catch (_: IllegalStateException) { fail(uuid) }
    }

    private fun ensurePlayer(uuid: String, file: File): MediaPlayer? {
        if (activeUuid == uuid && player != null) {
            return player!!
        }
        if (activeUuid != null && activeUuid != uuid) {
            pause(activeUuid!!, userInitiated = true)
        }
        releasePlayer()
        failedMessages.remove(uuid)
        var candidate: MediaPlayer? = null
        val mp = try {
            playerFactory().also {
                candidate = it
                it.setDataSource(file.absolutePath)
                it.prepare()
                durations[uuid] = it.duration
                val savedPosition = positions[uuid] ?: 0
                if (savedPosition > 0) it.seekTo(savedPosition.coerceAtMost(durations[uuid]!!))
            }
        } catch (e: Exception) {
            candidate?.release()
            Log.w(Config.LOGTAG, "Unable to prepare audio playback", e)
            failedMessages[uuid] = true
            return null
        }
        mp.setOnErrorListener { failedPlayer, what, extra ->
            Log.w(Config.LOGTAG, "Audio playback error: $what/$extra")
            if (player === failedPlayer) fail(uuid)
            // An error must not fall through to onCompletion/auto-advance or "listened".
            true
        }
        mp.setOnCompletionListener {
            if (player !== it || activeUuid != uuid) return@setOnCompletionListener
            isPlaying = false
            positions[uuid] = 0
            // Clear activeUuid so positionFor() reads positions[uuid]=0 instead of
            // player.currentPosition (which sits at the end of the file after completion).
            releasePlayer()
            onTransition?.invoke(uuid, ListenStatusManager.WIRE_LISTENED)
            onCompletion?.invoke(uuid)
        }
        player = mp
        activeUuid = uuid
        return mp
    }

    fun play(uuid: String, file: File) {
        resumableOnReturn.remove(uuid)
        val wasPlayingThis = activeUuid == uuid && isPlaying
        val mp = ensurePlayer(uuid, file) ?: return
        try { mp.start() } catch (_: IllegalStateException) {
            fail(uuid)
            return
        }
        if (player !== mp) return
        activeUuid = uuid
        isPlaying = true
        if (!wasPlayingThis) {
            onTransition?.invoke(uuid, ListenStatusManager.WIRE_LISTENING)
        }
    }

    private fun releasePlayer() {
        val old = player
        player = null
        activeUuid = null
        isPlaying = false
        old?.release()
    }

    private fun fail(uuid: String) {
        val wasPlaying = activeUuid == uuid && isPlaying
        releasePlayer()
        resumableOnReturn.remove(uuid)
        failedMessages[uuid] = true
        if (wasPlaying) onTransition?.invoke(uuid, ListenStatusManager.WIRE_PAUSED)
    }

    private fun pause(uuid: String, userInitiated: Boolean) {
        if (activeUuid != uuid) return
        val mp = player ?: return
        val wasPlaying = isPlaying
        try {
            positions[uuid] = mp.currentPosition
            mp.pause()
        } catch (_: IllegalStateException) {
            fail(uuid)
            return
        }
        isPlaying = false
        if (wasPlaying) {
            onTransition?.invoke(uuid, ListenStatusManager.WIRE_PAUSED)
        }
        if (userInitiated) {
            resumableOnReturn.remove(uuid)
        } else {
            resumableOnReturn.add(uuid)
        }
    }

    /** Called when a playing bubble is scrolled out of view. Playback continues uninterrupted. */
    fun onRowLeftComposition(uuid: String) {
        // Intentionally a no-op: audio keeps playing when the bubble scrolls out of view.
    }

    /** Called when a bubble enters view. No-op since scroll-out no longer pauses playback. */
    fun onRowEnteredComposition(uuid: String, file: File) {
    }

    /** Called when the conversation screen stops (app backgrounded or navigated away). */
    fun pauseForBackground() {
        val uuid = activeUuid ?: return
        pause(uuid, userInitiated = true)
    }
}
