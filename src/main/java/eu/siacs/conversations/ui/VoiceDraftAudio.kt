package eu.siacs.conversations.ui

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** Joins finalized pause/resume segments by copying encoded samples, without re-encoding. */
object VoiceDraftAudio {
    fun join(segments: List<File>, output: File, opus: Boolean): File {
        require(segments.isNotEmpty()) { "No recorded audio" }
        if (segments.size == 1) {
            segments.single().copyTo(output, overwrite = true)
            return output
        }
        val muxer = MediaMuxer(output.absolutePath, if (opus) MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
            else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        var outputTrack = -1
        var offsetUs = 0L
        try {
            for (file in segments) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(file.absolutePath)
                    val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it)
                        .getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                    val format = extractor.getTrackFormat(track)
                    if (!started) {
                        outputTrack = muxer.addTrack(format)
                        muxer.start(); started = true
                    }
                    extractor.selectTrack(track)
                    val buffer = ByteBuffer.allocateDirect(1024 * 1024)
                    val info = MediaCodec.BufferInfo()
                    val firstTime = extractor.sampleTime.coerceAtLeast(0L)
                    var lastTime = offsetUs
                    var wrote = false
                    val frameUs = if (opus) 20_000L else 1024_000_000L / format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    while (true) {
                        buffer.clear()
                        val length = extractor.readSampleData(buffer, 0)
                        if (length < 0) break
                        lastTime = offsetUs + (extractor.sampleTime - firstTime).coerceAtLeast(0)
                        info.set(0, length, lastTime, extractor.sampleFlags)
                        muxer.writeSampleData(outputTrack, buffer, info)
                        wrote = true
                        extractor.advance()
                    }
                    if (wrote) offsetUs = lastTime + frameUs
                } finally { extractor.release() }
            }
            check(started && offsetUs > 0) { "Recorded audio is empty" }
            muxer.stop()
            return output
        } catch (e: Exception) {
            output.delete()
            throw e
        } finally { muxer.release() }
    }
}
