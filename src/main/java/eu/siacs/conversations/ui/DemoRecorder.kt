package eu.siacs.conversations.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import eu.siacs.conversations.R
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Records only to session-owned cache files; it never invokes message or transfer services. */
internal class DemoRecorder(
    private val context: Context,
    private val session: DemoSession,
    private val chat: DemoConversation,
    private val state: ConversationScreenState,
    private val scope: CoroutineScope,
    private val refresh: () -> Unit,
) {
    private var recorder: MediaRecorder? = null
    private var current: File? = null
    private val segments = mutableListOf<File>()
    private var timer: Job? = null
    private var preview: MediaPlayer? = null
    private var previewTimer: Job? = null
    private var segmentStarted = 0L
    private var accumulated = 0L
    private val envelope = RecordingWaveform()
    private val opus = eu.siacs.conversations.Config.USE_OPUS_VOICE_MESSAGES && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    private fun newFile(): File {
        val directory = File(context.cacheDir, "impulse_demo_recordings")
        check(directory.exists() || directory.mkdirs())
        return session.own(File.createTempFile("voice_", if (opus) ".ogg" else ".m4a", directory))
    }
    private fun elapsed() = accumulated + if (recorder != null) SystemClock.elapsedRealtime() - segmentStarted else 0L
    private fun error(id: Int) = Toast.makeText(context, id, Toast.LENGTH_SHORT).show()

    fun start() {
        if (recorder != null) return
        stopPreview()
        val file = try { newFile() } catch (_: Exception) { error(R.string.unable_to_start_recording); return }
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) r.setPrivacySensitive(true)
            if (opus) {
                r.setOutputFormat(MediaRecorder.OutputFormat.OGG); r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                r.setAudioEncodingBitRate(32_000)
            } else {
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); r.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC)
                r.setAudioSamplingRate(24_000); r.setAudioEncodingBitRate(28_000)
            }
            r.setOutputFile(file.absolutePath); r.prepare(); r.start()
        } catch (_: Exception) { r.release(); session.discard(file); error(R.string.unable_to_start_recording); return }
        recorder = r; current = file; segmentStarted = SystemClock.elapsedRealtime()
        state.recordingState.value = RecordingUiState.Active(accumulated, false)
        timer?.cancel()
        timer = scope.launch {
            while (recorder != null) {
                envelope.add(try { recorder?.maxAmplitude ?: 0 } catch (_: RuntimeException) { 0 })
                state.recordingBars.value = envelope.bars()
                state.recordingState.value = RecordingUiState.Active(elapsed(), false)
                delay(100)
            }
        }
    }

    private fun stopSegment(save: Boolean) {
        val r = recorder ?: return
        val duration = elapsed()
        recorder = null; timer?.cancel(); timer = null
        var valid = false
        try { r.stop(); valid = save } catch (_: RuntimeException) { } finally { r.release() }
        current?.let { if (valid) segments.add(it) else session.discard(it) }
        current = null
        if (valid) accumulated = duration
    }
    fun pause() {
        val active = state.recordingState.value as? RecordingUiState.Active ?: return
        if (active.paused) start() else {
            stopSegment(true)
            state.recordingState.value = RecordingUiState.Active(accumulated, true)
        }
    }
    private fun stopPreview() {
        previewTimer?.cancel(); previewTimer = null
        preview?.release(); preview = null
        state.recordingPreviewPlaying.value = false
        state.recordingPreviewFraction.floatValue = 0f
    }
    fun preview() {
        if (!(state.recordingState.value as? RecordingUiState.Active)?.paused.orFalse() || segments.isEmpty()) return
        if (preview != null) { stopPreview(); return }
        fun play(index: Int, before: Int) {
            if (index >= segments.size) { stopPreview(); return }
            val p = MediaPlayer(); preview = p
            try {
                p.setDataSource(segments[index].absolutePath); p.prepare()
                p.setOnCompletionListener { val d = p.duration; p.release(); preview = null; play(index + 1, before + d) }
                p.setOnErrorListener { _, _, _ -> stopPreview(); true }
                p.start(); state.recordingPreviewPlaying.value = true
                previewTimer?.cancel(); previewTimer = scope.launch {
                    while (preview === p) {
                        state.recordingPreviewFraction.floatValue = ((before + p.currentPosition).toFloat() / accumulated.coerceAtLeast(1)).coerceIn(0f, 1f)
                        delay(50)
                    }
                }
            } catch (_: Exception) { stopPreview() }
        }
        play(0, 0)
    }
    fun send() {
        if (state.recordingState.value !is RecordingUiState.Active || !VoiceRecordingPolicy.canSend(elapsed())) return
        stopPreview(); stopSegment(true)
        val duration = accumulated
        val parts = segments.toList(); segments.clear()
        state.recordingState.value = RecordingUiState.Idle
        accumulated = 0; envelope.clear(); state.recordingBars.value = emptyList()
        if (parts.isEmpty()) return
        session.work.launch {
            var output: File? = null
            try {
                val target = newFile()
                output = target
                withContext(Dispatchers.IO) { VoiceDraftAudio.join(parts, target, opus) }
                session.audio(chat, target, duration)
                if (chat.demoStatuses[true]?.clearOnSend == true) chat.demoStatuses.remove(true)
                refresh()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                output?.let(session::discard)
                throw cancelled
            } catch (_: Exception) { output?.let(session::discard); error(R.string.unable_to_save_recording) }
            finally { parts.forEach(session::discard) }
        }
    }
    fun cancel() {
        stopPreview(); stopSegment(false); segments.forEach(session::discard); segments.clear()
        accumulated = 0; envelope.clear(); state.recordingBars.value = emptyList()
        state.recordingState.value = RecordingUiState.Idle
    }
}
private fun Boolean?.orFalse() = this == true
