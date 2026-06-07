package eu.siacs.conversations.ui.util

import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.google.android.material.appbar.MaterialToolbar
import java.util.Collections.max
import java.util.Collections.min
import java.util.ArrayList
import java.util.Comparator

object ToolbarUtils {

    private val VIEW_TOP_COMPARATOR: Comparator<View> = Comparator { view1, view2 ->
        view1.top - view2.top
    }

    @JvmStatic
    fun resetActionBarOnClickListeners(view: MaterialToolbar) {
        val title = getTitleTextView(view)
        val subtitle = getSubtitleTextView(view)
        title?.setOnClickListener(null)
        subtitle?.setOnClickListener(null)
    }

    @JvmStatic
    fun setActionBarOnClickListener(view: MaterialToolbar, onClickListener: View.OnClickListener) {
        val title = getTitleTextView(view)
        val subtitle = getSubtitleTextView(view)
        title?.setOnClickListener(onClickListener)
        subtitle?.setOnClickListener(onClickListener)
    }

    @JvmStatic
    fun getTitleTextView(toolbar: Toolbar): TextView? {
        val textViews = getTextViewsWithText(toolbar, toolbar.title)
        return if (textViews.isEmpty()) null else min(textViews, VIEW_TOP_COMPARATOR)
    }

    @JvmStatic
    fun getSubtitleTextView(toolbar: Toolbar): TextView? {
        val textViews = getTextViewsWithText(toolbar, toolbar.subtitle)
        return if (textViews.isEmpty()) null else max(textViews, VIEW_TOP_COMPARATOR)
    }

    private fun getTextViewsWithText(toolbar: Toolbar, text: CharSequence?): List<TextView> {
        val textViews = ArrayList<TextView>()
        for (i in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(i)
            if (child is TextView) {
                if (TextUtils.equals(child.text, text)) {
                    textViews.add(child)
                }
            }
        }
        return textViews
    }

    @JvmStatic
    fun adjustToolbarHeight(toolbar: MaterialToolbar, adjustToSearchBar: Boolean) {
        val context = toolbar.context
        val params: ViewGroup.LayoutParams = toolbar.layoutParams

        if (adjustToSearchBar) {
            params.height = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                88f,
                context.resources.displayMetrics
            ).toInt()
        } else {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        toolbar.layoutParams = params
    }
}
