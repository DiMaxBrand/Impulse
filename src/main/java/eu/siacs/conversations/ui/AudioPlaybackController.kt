package eu.siacs.conversations.ui

import android.media.MediaPlayer
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

    private val resumableOnReturn = mutableSetOf<String>()
    private var player: MediaPlayer? = null

    /** Invoked on the main thread when a message finishes playing naturally (not paused/stopped). */
    var onCompletion: ((completedUuid: String) -> Unit)? = null

    fun positionFor(uuid: String): Int =
        if (activeUuid == uuid) player?.currentPosition ?: (positions[uuid] ?: 0) else positions[uuid] ?: 0

    fun toggle(uuid: String, file: File) {
        if (activeUuid == uuid && isPlaying) {
            pause(uuid, userInitiated = true)
        } else {
            play(uuid, file)
        }
    }

    fun seekTo(uuid: String, file: File, positionMs: Int) {
        val mp = ensurePlayer(uuid, file)
        mp.seekTo(positionMs)
        positions[uuid] = positionMs
    }

    private fun ensurePlayer(uuid: String, file: File): MediaPlayer {
        if (activeUuid == uuid && player != null) {
            return player!!
        }
        if (activeUuid != null && activeUuid != uuid) {
            pause(activeUuid!!, userInitiated = true)
        }
        player?.release()
        val mp = MediaPlayer()
        mp.setDataSource(file.absolutePath)
        mp.prepare()
        mp.setOnCompletionListener {
            isPlaying = false
            positions[uuid] = 0
            onCompletion?.invoke(uuid)
        }
        val savedPosition = positions[uuid] ?: 0
        if (savedPosition > 0) {
            mp.seekTo(savedPosition)
        }
        durations[uuid] = mp.duration
        player = mp
        activeUuid = uuid
        return mp
    }

    fun play(uuid: String, file: File) {
        resumableOnReturn.remove(uuid)
        val mp = ensurePlayer(uuid, file)
        mp.start()
        activeUuid = uuid
        isPlaying = true
    }

    private fun pause(uuid: String, userInitiated: Boolean) {
        if (activeUuid != uuid) return
        val mp = player ?: return
        try {
            positions[uuid] = mp.currentPosition
            mp.pause()
        } catch (_: Exception) {
        }
        isPlaying = false
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
