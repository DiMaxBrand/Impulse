package eu.siacs.conversations.ui.text

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.LeadingMarginSpan
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.annotation.ColorInt

class QuoteSpan(@ColorInt val color: Int, metrics: DisplayMetrics) :
    CharacterStyle(), LeadingMarginSpan {

    private val width: Int
    private val paddingLeft: Int
    private val paddingRight: Int

    init {
        this.width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, WIDTH_SP, metrics).toInt()
        this.paddingLeft = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, PADDING_LEFT_SP, metrics).toInt()
        this.paddingRight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, PADDING_RIGHT_SP, metrics).toInt()
    }

    override fun updateDrawState(tp: TextPaint) {
        tp.color = this.color
    }

    override fun getLeadingMargin(first: Boolean): Int {
        return paddingLeft + width + paddingRight
    }

    override fun drawLeadingMargin(
        c: Canvas,
        p: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout
    ) {
        val style = p.style
        val color = p.color
        p.style = Paint.Style.FILL
        p.color = this.color
        c.drawRect(
            (x + dir * paddingLeft).toFloat(),
            top.toFloat(),
            (x + dir * (paddingLeft + width)).toFloat(),
            bottom.toFloat(),
            p
        )
        p.style = style
        p.color = color
    }

    companion object {
        private const val WIDTH_SP = 2f
        private const val PADDING_LEFT_SP = 1.5f
        private const val PADDING_RIGHT_SP = 8f
    }
}
