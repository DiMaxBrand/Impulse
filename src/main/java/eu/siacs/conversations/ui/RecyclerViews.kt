package eu.siacs.conversations.ui

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

object RecyclerViews {
    @JvmStatic
    fun scrolledToTop(recyclerView: RecyclerView): Boolean {
        val lm = recyclerView.layoutManager
        return lm is LinearLayoutManager && lm.findFirstCompletelyVisibleItemPosition() == 0
    }

    @JvmStatic
    fun findFirstVisibleItemPosition(recyclerView: RecyclerView): Int {
        val lm = recyclerView.layoutManager
        return if (lm is LinearLayoutManager) lm.findFirstVisibleItemPosition()
        else RecyclerView.NO_POSITION
    }
}
