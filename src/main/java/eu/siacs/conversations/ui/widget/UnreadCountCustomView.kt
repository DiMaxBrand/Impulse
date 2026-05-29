package eu.siacs.conversations.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import eu.siacs.conversations.R

class UnreadCountCustomView : View {

    private var unreadCount: Int = 0
    private lateinit var paint: Paint
    private lateinit var textPaint: Paint
    private var backgroundColor: Int = 0xff326130.toInt()
    private var textColor: Int = Color.WHITE

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initXMLAttrs(context, attrs)
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initXMLAttrs(context, attrs)
        init()
    }

    private fun initXMLAttrs(context: Context, attrs: AttributeSet) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.UnreadCountCustomView)
        setBackgroundColor(a.getColor(a.getIndex(0), ContextCompat.getColor(context, R.color.md_theme_light_tertiaryContainer)))
        this.textColor = a.getColor(a.getIndex(1), ContextCompat.getColor(context, R.color.md_theme_light_onTertiaryContainer))
        a.recycle()
    }

    fun init() {
        paint = Paint()
        paint.color = backgroundColor
        paint.isAntiAlias = true
        textPaint = Paint()
        textPaint.color = textColor
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isAntiAlias = true
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val midx = canvas.width / 2.0f
        val midy = canvas.height / 2.0f
        val radius = minOf(canvas.width, canvas.height) / 2.0f
        val textOffset = canvas.width / 6.0f
        textPaint.textSize = 0.95f * radius
        canvas.drawCircle(midx, midy, radius * 0.94f, paint)
        canvas.drawText(
            if (unreadCount > 999) "∞" else unreadCount.toString(),
            midx,
            midy + textOffset,
            textPaint
        )
    }

    fun setUnreadCount(unreadCount: Int) {
        this.unreadCount = unreadCount
        invalidate()
    }

    override fun setBackgroundColor(backgroundColor: Int) {
        this.backgroundColor = backgroundColor
    }
}
