package eu.siacs.conversations.ui.widget

import android.text.Layout
import android.text.Spannable
import android.text.method.ArrowKeyMovementMethod
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.widget.TextView

class ClickableMovementMethod : ArrowKeyMovementMethod() {

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        // Just copied from android.text.method.LinkMovementMethod
        if (event.action == MotionEvent.ACTION_UP) {
            var x = event.x.toInt()
            var y = event.y.toInt()
            x -= widget.totalPaddingLeft
            y -= widget.totalPaddingTop
            x += widget.scrollX
            y += widget.scrollY
            val layout: Layout = widget.layout
            val line = layout.getLineForVertical(y)
            val off = layout.getOffsetForHorizontal(line, x.toFloat())
            val link = buffer.getSpans(off, off, ClickableSpan::class.java)
            if (link.isNotEmpty()) {
                link[0].onClick(widget)
                return true
            }
        }
        return super.onTouchEvent(widget, buffer, event)
    }

    companion object {
        private var sInstance: ClickableMovementMethod? = null

        @JvmStatic
        fun getInstance(): ClickableMovementMethod {
            if (sInstance == null) {
                sInstance = ClickableMovementMethod()
            }
            return sInstance!!
        }
    }
}
