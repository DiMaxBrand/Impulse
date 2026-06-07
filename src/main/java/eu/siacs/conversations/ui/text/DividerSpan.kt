package eu.siacs.conversations.ui.text

import android.text.TextPaint
import android.text.style.MetricAffectingSpan

class DividerSpan(val isLarge: Boolean) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) {
        tp.textSize *= PROPORTION
    }

    override fun updateMeasureState(p: TextPaint) {
        p.textSize *= PROPORTION
    }

    companion object {
        private const val PROPORTION = 0.3f
    }
}
