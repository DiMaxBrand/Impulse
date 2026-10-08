package eu.siacs.conversations.ui.widget

import org.webrtc.VideoFrame
import org.webrtc.VideoSink

/**
 * Notices when the other side has switched their camera off.
 *
 * Turning a camera off in WebRTC only disables the track, which then sends solid black frames;
 * nothing in the call says so. This watches the incoming picture a couple of times a second, and
 * after two black samples in a row (about a second) reports "camera off". A single normal frame
 * reports "camera on" again. A camera pointed at something truly pitch black looks the same,
 * which is an accepted trade-off.
 */
class BlackFrameDetector(private val listener: Listener) : VideoSink {

    fun interface Listener {
        fun onChange(black: Boolean)
    }

    private var lastSampleNs = 0L
    private var blackSamples = 0
    private var black = false

    override fun onFrame(frame: VideoFrame) {
        val now = System.nanoTime()
        if (now - lastSampleNs < SAMPLE_INTERVAL_NS) return
        lastSampleNs = now
        val i420 = frame.buffer.toI420() ?: return
        val brightest: Int
        try {
            val data = i420.dataY
            val stride = i420.strideY
            var max = 0
            for (row in 1..7) {
                for (column in 1..7) {
                    val y = i420.height * row / 8
                    val x = i420.width * column / 8
                    val value = data.get(y * stride + x).toInt() and 0xFF
                    if (value > max) max = value
                }
            }
            brightest = max
        } finally {
            i420.release()
        }
        blackSamples = if (brightest <= BLACK_LUMA) blackSamples + 1 else 0
        val nowBlack = blackSamples >= 2
        if (nowBlack != black) {
            black = nowBlack
            listener.onChange(black)
        }
    }

    /** Forget everything, e.g. when the remote track goes away. */
    fun reset() {
        blackSamples = 0
        black = false
        lastSampleNs = 0L
    }

    private companion object {
        const val SAMPLE_INTERVAL_NS = 500_000_000L
        // Limited-range video black is 16; leave a little room for encoder noise.
        const val BLACK_LUMA = 28
    }
}
