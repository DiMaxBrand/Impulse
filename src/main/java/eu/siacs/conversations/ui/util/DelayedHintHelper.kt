package eu.siacs.conversations.ui.util

import android.os.Handler
import android.widget.EditText
import androidx.annotation.StringRes

object DelayedHintHelper {

    @JvmStatic
    fun setHint(@StringRes res: Int, editText: EditText) {
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                Handler().postDelayed({ editText.setHint(res) }, 200)
            } else {
                editText.hint = null
            }
        }
    }
}
