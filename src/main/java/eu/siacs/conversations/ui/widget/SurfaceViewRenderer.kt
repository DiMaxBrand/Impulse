package eu.siacs.conversations.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.util.Rational
import eu.siacs.conversations.Config

class SurfaceViewRenderer : org.webrtc.SurfaceViewRenderer {

    private var aspectRatio: Rational = Rational(1, 1)
    private var onAspectRatioChanged: OnAspectRatioChanged? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
        super.onFrameResolutionChanged(videoWidth, videoHeight, rotation)
        val rotatedWidth = if (rotation != 0 && rotation != 180) videoHeight else videoWidth
        val rotatedHeight = if (rotation != 0 && rotation != 180) videoWidth else videoHeight
        val currentRational = this.aspectRatio
        this.aspectRatio = Rational(rotatedWidth, rotatedHeight)
        Log.d(Config.LOGTAG, "onFrameResolutionChanged($rotatedWidth,$rotatedHeight,$aspectRatio)")
        if (currentRational == this.aspectRatio || onAspectRatioChanged == null) {
            return
        }
        onAspectRatioChanged!!.onAspectRatioChanged(this.aspectRatio)
    }

    fun setOnAspectRatioChanged(onAspectRatioChanged: OnAspectRatioChanged?) {
        this.onAspectRatioChanged = onAspectRatioChanged
    }

    fun getAspectRatio(): Rational {
        return this.aspectRatio
    }

    fun interface OnAspectRatioChanged {
        fun onAspectRatioChanged(rational: Rational)
    }
}
