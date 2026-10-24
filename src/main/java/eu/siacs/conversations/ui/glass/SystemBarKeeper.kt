package eu.siacs.conversations.ui.glass

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.MaterialColors

/**
 * Experiment for the liquid glass flag: the status bar flickering to black and white noise.
 *
 * The strips behind the status bar and the gesture bar are not part of any screen of ours (the
 * host layout stops short of them), so nothing paints them but the window background, and nothing
 * touches them from one frame to the next. On some graphics drivers, once the glass effects keep
 * the renderer busy with offscreen passes, such untouched strips can show stale buffers. These two
 * thin views sit over exactly those strips, paint them in the window background colour (the same
 * look as before) and are invalidated about thirty times a second, so the renderer owns those pixels
 * on every frame. It costs a little power while the screen is on; it only runs with the flag on.
 */
object SystemBarKeeper {
    private const val TAG_TOP = "impulse_bar_keeper_top"
    private const val TAG_BOTTOM = "impulse_bar_keeper_bottom"

    fun attach(activity: Activity) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        if (decor.findViewWithTag<View>(TAG_TOP) != null) return
        val color = MaterialColors.getColor(activity, android.R.attr.colorBackground, 0xFF000000.toInt())
        decor.addView(Strip(activity, color, top = true).also { it.tag = TAG_TOP })
        decor.addView(Strip(activity, color, top = false).also { it.tag = TAG_BOTTOM })
    }

    fun detach(activity: Activity) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        decor.findViewWithTag<View>(TAG_TOP)?.let { decor.removeView(it) }
        decor.findViewWithTag<View>(TAG_BOTTOM)?.let { decor.removeView(it) }
    }

    private class Strip(context: Context, private val color: Int, private val top: Boolean) : View(context) {
        init {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                // One pixel to start with: a view with no height is never drawn, and it is the
                // draw that sets the real height from the insets.
                1,
                if (top) Gravity.TOP else Gravity.BOTTOM,
            )
            // Never in the way of a touch.
            isClickable = false
            isFocusable = false
        }

        override fun onDraw(canvas: Canvas) {
            // The top strip draws nothing now (the screen runs under the status bar, see
            // GlassEdgeToEdge); it only keeps that area being redrawn on every frame.
            if (!top) canvas.drawColor(color)
            val insets = ViewCompat.getRootWindowInsets(this)
                ?.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            val wanted = if (top) insets?.top ?: 0 else insets?.bottom ?: 0
            val params = layoutParams
            if (params != null && params.height != wanted) {
                params.height = wanted
                layoutParams = params
            }
            postInvalidateDelayed(33)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            postInvalidateDelayed(33)
        }

        override fun onTouchEvent(event: android.view.MotionEvent?): Boolean = false
    }
}
