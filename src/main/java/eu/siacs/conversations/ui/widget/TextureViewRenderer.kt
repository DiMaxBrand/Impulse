package eu.siacs.conversations.ui.widget

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.util.Rational
import android.view.TextureView
import eu.siacs.conversations.Config
import java.util.concurrent.CountDownLatch
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.RendererCommon
import org.webrtc.ThreadUtils
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

/**
 * A WebRTC video renderer built on a [TextureView] instead of a SurfaceView. A TextureView is an
 * ordinary view as far as drawing goes, so it can be clipped to rounded corners, faded, scaled
 * and moved around by the Compose call screen -- none of which a SurfaceView does reliably.
 *
 * Mirrors the parts of org.webrtc.SurfaceViewRenderer the call screen relies on.
 */
class TextureViewRenderer : TextureView, VideoSink, TextureView.SurfaceTextureListener {

    private val eglRenderer = EglRenderer("TextureViewRenderer")
    private val videoLayoutMeasure = RendererCommon.VideoLayoutMeasure()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var initialized = false
    private var frameWidth = 0
    private var frameHeight = 0
    private var frameRotation = 0
    private var rotatedFrameWidth = 0
    private var rotatedFrameHeight = 0
    private var aspectRatio: Rational = Rational(1, 1)
    private var onAspectRatioChanged: OnAspectRatioChanged? = null

    constructor(context: Context) : super(context) {
        surfaceTextureListener = this
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        surfaceTextureListener = this
    }

    /** Same shape as SurfaceViewRenderer.init, so the activity can treat them alike. */
    fun init(sharedContext: EglBase.Context?, @Suppress("UNUSED_PARAMETER") events: Any?) {
        // Throws IllegalStateException when already initialised, which the caller expects.
        eglRenderer.init(sharedContext, EglBase.CONFIG_PLAIN, GlRectDrawer())
        initialized = true
        val texture = surfaceTexture
        if (isAvailable && texture != null) {
            eglRenderer.createEglSurface(texture)
        }
    }

    fun release() {
        initialized = false
        eglRenderer.release()
        frameWidth = 0
        frameHeight = 0
        rotatedFrameWidth = 0
        rotatedFrameHeight = 0
    }

    fun setMirror(mirror: Boolean) {
        eglRenderer.setMirror(mirror)
    }

    @Suppress("UNUSED_PARAMETER") fun setEnableHardwareScaler(enabled: Boolean) {}

    fun setScalingType(scalingType: RendererCommon.ScalingType) {
        videoLayoutMeasure.setScalingType(scalingType)
        requestLayout()
    }

    fun setScalingType(
        matchOrientation: RendererCommon.ScalingType,
        mismatchOrientation: RendererCommon.ScalingType,
    ) {
        videoLayoutMeasure.setScalingType(matchOrientation, mismatchOrientation)
        requestLayout()
    }

    override fun onFrame(frame: VideoFrame) {
        val width = frame.buffer.width
        val height = frame.buffer.height
        val rotation = frame.rotation
        if (width != frameWidth || height != frameHeight || rotation != frameRotation) {
            frameWidth = width
            frameHeight = height
            frameRotation = rotation
            val rotatedWidth = if (rotation == 0 || rotation == 180) width else height
            val rotatedHeight = if (rotation == 0 || rotation == 180) height else width
            mainHandler.post {
                rotatedFrameWidth = rotatedWidth
                rotatedFrameHeight = rotatedHeight
                val previous = aspectRatio
                aspectRatio = Rational(rotatedWidth, rotatedHeight)
                Log.d(Config.LOGTAG, "TextureViewRenderer frame size $rotatedWidth x $rotatedHeight")
                requestLayout()
                if (previous != aspectRatio) {
                    onAspectRatioChanged?.onAspectRatioChanged(aspectRatio)
                }
            }
        }
        eglRenderer.onFrame(frame)
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val size =
            videoLayoutMeasure.measure(widthSpec, heightSpec, rotatedFrameWidth, rotatedFrameHeight)
        setMeasuredDimension(size.x, size.y)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        if (width > 0 && height > 0) {
            eglRenderer.setLayoutAspectRatio(width.toFloat() / height.toFloat())
        }
        super.onLayout(changed, left, top, right, bottom)
    }

    fun setOnAspectRatioChanged(listener: OnAspectRatioChanged?) {
        onAspectRatioChanged = listener
    }

    fun getAspectRatio(): Rational = aspectRatio

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        if (initialized) {
            eglRenderer.createEglSurface(texture)
        }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        val latch = CountDownLatch(1)
        eglRenderer.releaseEglSurface { latch.countDown() }
        ThreadUtils.awaitUninterruptibly(latch)
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}

    fun interface OnAspectRatioChanged {
        fun onAspectRatioChanged(rational: Rational)
    }
}
