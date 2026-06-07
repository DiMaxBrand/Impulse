package eu.siacs.conversations.ui

import android.widget.Toast

object Toasts {
    @JvmStatic
    fun hide(toast: Toast?) {
        toast?.cancel()
    }
}
