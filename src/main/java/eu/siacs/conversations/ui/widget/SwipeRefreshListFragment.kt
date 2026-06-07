/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.siacs.conversations.ui.widget

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.fragment.app.ListFragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Subclass of [ListFragment] which provides automatic support for
 * providing the 'swipe-to-refresh' UX gesture by wrapping the the content view in a
 * [SwipeRefreshLayout].
 */
open class SwipeRefreshListFragment : ListFragment() {

    private var enabled = false
    private var refreshing = false

    private var onRefreshListener: SwipeRefreshLayout.OnRefreshListener? = null

    private var mSwipeRefreshLayout: SwipeRefreshLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Create the list fragment's content view by calling the super method
        val listFragmentView = super.onCreateView(inflater, container, savedInstanceState)

        // Now create a SwipeRefreshLayout to wrap the fragment's content view
        mSwipeRefreshLayout = ListFragmentSwipeRefreshLayout(container!!.context)
        mSwipeRefreshLayout!!.isEnabled = enabled
        mSwipeRefreshLayout!!.isRefreshing = refreshing

        if (onRefreshListener != null) {
            mSwipeRefreshLayout!!.setOnRefreshListener(onRefreshListener)
        }

        // Add the list fragment's content view to the SwipeRefreshLayout, making sure that it fills
        // the SwipeRefreshLayout
        mSwipeRefreshLayout!!.addView(
            listFragmentView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Make sure that the SwipeRefreshLayout will fill the fragment
        mSwipeRefreshLayout!!.layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

        // Now return the SwipeRefreshLayout as this fragment's content view
        return mSwipeRefreshLayout
    }

    fun setOnRefreshListener(listener: SwipeRefreshLayout.OnRefreshListener) {
        onRefreshListener = listener
        enabled = true
        if (mSwipeRefreshLayout != null) {
            mSwipeRefreshLayout!!.isEnabled = true
            mSwipeRefreshLayout!!.setOnRefreshListener(listener)
        }
    }

    fun setRefreshing(refreshing: Boolean) {
        this.refreshing = refreshing
        if (mSwipeRefreshLayout != null) {
            mSwipeRefreshLayout!!.isRefreshing = refreshing
        }
    }

    private inner class ListFragmentSwipeRefreshLayout(context: Context) :
        SwipeRefreshLayout(context) {

        override fun canChildScrollUp(): Boolean {
            val listView = getListView()
            return if (listView.visibility == View.VISIBLE) {
                listView.canScrollVertically(-1)
            } else {
                false
            }
        }
    }
}
