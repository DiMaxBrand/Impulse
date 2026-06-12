package eu.siacs.conversations.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/**
 * ImageView clipped to an n=5 superellipse (squircle).
 *
 * Unlike a rounded rectangle, a squircle has no flat sides — every point on
 * the perimeter has non-zero curvature. The shape is approximated with four
 * cubic bezier arcs, one per quadrant. The control-point factor k=0.9883
 * is derived by matching the diagonal midpoint of the n=5 superellipse:
 *   x = y = r * (sin π/4)^(2/5) ≈ r * 0.8706
 * which satisfies B(0.5).x = r * (0.5 + 0.375 * k) → k ≈ 0.9883.
 */
class SquircleImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageView(context, attrs) {

    private val clipPath = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) buildPath(w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        if (!clipPath.isEmpty) canvas.clipPath(clipPath)
        super.onDraw(canvas)
    }

    private fun buildPath(w: Float, h: Float) {
        val rx = w / 2f
        val ry = h / 2f
        val cx = w / 2f
        val cy = h / 2f
        val kx = rx * 0.9883f
        val ky = ry * 0.9883f
        clipPath.reset()
        clipPath.moveTo(cx + rx, cy)
        // right → bottom
        clipPath.cubicTo(cx + rx, cy + ky,  cx + kx, cy + ry,  cx,       cy + ry)
        // bottom → left
        clipPath.cubicTo(cx - kx, cy + ry,  cx - rx, cy + ky,  cx - rx,  cy)
        // left → top
        clipPath.cubicTo(cx - rx, cy - ky,  cx - kx, cy - ry,  cx,       cy - ry)
        // top → right
        clipPath.cubicTo(cx + kx, cy - ry,  cx + rx, cy - ky,  cx + rx,  cy)
        clipPath.close()
    }
}
