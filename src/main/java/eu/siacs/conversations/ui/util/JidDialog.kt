package eu.siacs.conversations.ui.util

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TypefaceSpan
import androidx.annotation.StringRes

object JidDialog {
    @JvmStatic
    fun style(context: Context, @StringRes res: Int, vararg args: String): SpannableString {
        val spannable = SpannableString(context.getString(res, *args))
        if (args.isNotEmpty()) {
            val value = args[0]
            val start = spannable.toString().indexOf(value)
            if (start >= 0) {
                spannable.setSpan(
                    TypefaceSpan("monospace"),
                    start,
                    start + value.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        return spannable
    }
}
