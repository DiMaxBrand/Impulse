package eu.siacs.conversations.ui.util

import android.view.View
import android.widget.ListView

object ListViewUtils {

    @JvmStatic
    fun scrollToBottom(listView: ListView) {
        val count = listView.adapter.count
        if (count > 0) {
            setSelection(listView, count - 1, true)
        }
    }

    @JvmStatic
    fun setSelection(listView: ListView, pos: Int, jumpToBottom: Boolean) {
        if (jumpToBottom) {
            val lastChild: View? = listView.getChildAt(listView.childCount - 1)
            if (lastChild != null) {
                listView.setSelectionFromTop(pos, -lastChild.height)
                return
            }
        }
        listView.setSelection(pos)
    }
}
