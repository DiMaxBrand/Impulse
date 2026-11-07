package eu.siacs.conversations.ui

import android.media.MediaPlayer
import java.io.File
import java.io.IOException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = android.app.Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class AudioPlaybackControllerTest {
    private val audio = File("/tmp/test-voice.ogg")
    private val transitions = mutableListOf<String>()
    private var completed = false

    @Before fun setup() {
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(audio.absolutePath), ShadowMediaPlayer.MediaInfo(5000, 0))
        AudioPlaybackController.onTransition = { _, state -> transitions += state }
        AudioPlaybackController.onCompletion = { completed = true }
    }

    @After fun cleanup() {
        AudioPlaybackController.onTransition = null
        AudioPlaybackController.onCompletion = null
        AudioPlaybackController.playerFactory = { object : MediaPlayer() {
            override fun prepare() { throw IOException("cleanup") }
        } }
        AudioPlaybackController.play("cleanup", audio)
        AudioPlaybackController.playerFactory = { MediaPlayer() }
        AudioPlaybackController.positions.clear()
        AudioPlaybackController.durations.clear()
        AudioPlaybackController.failedMessages.clear()
    }

    @Test fun prepareFailureIsReleasedAndSameMessageCanBeRetried() {
        lateinit var failed: MediaPlayer
        AudioPlaybackController.playerFactory = {
            object : MediaPlayer() {
                override fun prepare() { throw IOException("Prepare failed.: status=0x1") }
            }.also { failed = it }
        }
        AudioPlaybackController.play("voice", audio)
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(failed).state)
        assertNull(AudioPlaybackController.activeUuid)
        assertFalse(AudioPlaybackController.isPlaying)
        assertTrue(AudioPlaybackController.failedMessages["voice"] == true)
        assertTrue(transitions.isEmpty())
        assertFalse(completed)
        AudioPlaybackController.playerFactory = { MediaPlayer() }
        AudioPlaybackController.play("voice", audio)
        assertTrue(AudioPlaybackController.isPlaying)
        assertEquals("voice", AudioPlaybackController.activeUuid)
        assertNull(AudioPlaybackController.failedMessages["voice"])
        assertEquals(listOf(ListenStatusManager.WIRE_LISTENING), transitions)
    }

    @Test fun seekPreparationFailureDoesNotCrashOrPublishRequestedPosition() {
        AudioPlaybackController.playerFactory = { object : MediaPlayer() {
            override fun prepare() { throw IOException("bad audio") }
        } }
        AudioPlaybackController.seekTo("voice", audio, 3000)
        assertNull(AudioPlaybackController.positions["voice"])
        assertNull(AudioPlaybackController.activeUuid)
        assertTrue(AudioPlaybackController.failedMessages["voice"] == true)
    }

    @Test fun asynchronousPlaybackErrorDoesNotCompleteOrMarkListened() {
        lateinit var mp: MediaPlayer
        AudioPlaybackController.playerFactory = { MediaPlayer().also { mp = it } }
        AudioPlaybackController.play("voice", audio)
        shadowOf(mp).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(mp).state)
        assertNull(AudioPlaybackController.activeUuid)
        assertFalse(AudioPlaybackController.isPlaying)
        assertFalse(completed)
        assertFalse(transitions.contains(ListenStatusManager.WIRE_LISTENED))
        assertEquals(ListenStatusManager.WIRE_PAUSED, transitions.last())
    }

    @Test fun failingNewMessageDoesNotReuseReleasedPreviousPlayer() {
        lateinit var old: MediaPlayer
        AudioPlaybackController.playerFactory = { MediaPlayer().also { old = it } }
        AudioPlaybackController.play("first", audio)
        AudioPlaybackController.playerFactory = { object : MediaPlayer() {
            override fun prepare() { throw IOException("second message invalid") }
        } }
        AudioPlaybackController.play("second", audio)
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(old).state)
        assertNull(AudioPlaybackController.activeUuid)
        assertEquals(0, AudioPlaybackController.positionFor("first"))
        assertTrue(AudioPlaybackController.failedMessages["second"] == true)
    }

    @Test fun normalCompletionStillResetsPositionAndAllowsAutoAdvance() {
        lateinit var mp: MediaPlayer
        AudioPlaybackController.playerFactory = { MediaPlayer().also { mp = it } }
        AudioPlaybackController.play("voice", audio)
        shadowOf(mp).invokeCompletionListener()
        assertTrue(completed)
        assertEquals(ListenStatusManager.WIRE_LISTENED, transitions.last())
        assertEquals(0, AudioPlaybackController.positionFor("voice"))
        assertNull(AudioPlaybackController.activeUuid)
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(mp).state)
    }
}
