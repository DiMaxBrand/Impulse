package eu.siacs.conversations.ui.adapter

import android.app.Activity
import android.text.TextUtils
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.ItemChannelDiscoveryBinding
import eu.siacs.conversations.entities.Room
import eu.siacs.conversations.ui.XmppActivity
import eu.siacs.conversations.ui.util.AvatarWorkerTask
import eu.siacs.conversations.xmpp.Jid
import java.util.Locale

class ChannelSearchResultAdapter :
    ListAdapter<Room, ChannelSearchResultAdapter.ViewHolder>(DIFF),
    View.OnCreateContextMenuListener {

    private var listener: OnChannelSearchResultSelected? = null
    var current: Room? = null
        private set

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ViewHolder =
        ViewHolder(
            DataBindingUtil.inflate(
                LayoutInflater.from(viewGroup.context),
                R.layout.item_channel_discovery,
                viewGroup,
                false
            )
        )

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val searchResult = getItem(position)
        viewHolder.binding.name.text = searchResult.getName()
        val description = searchResult.getDescription()
        val language = searchResult.getLanguage()
        if (TextUtils.isEmpty(description)) {
            viewHolder.binding.description.visibility = View.GONE
        } else {
            viewHolder.binding.description.text = description
            viewHolder.binding.description.visibility = View.VISIBLE
        }
        if (language == null || language.length != 2) {
            viewHolder.binding.language.visibility = View.GONE
        } else {
            viewHolder.binding.language.text = language.uppercase(Locale.ENGLISH)
            viewHolder.binding.language.visibility = View.VISIBLE
        }
        val room: Jid? = searchResult.getRoom()
        viewHolder.binding.room.text = if (room != null) room.asBareJid().toString() else ""
        AvatarWorkerTask.loadAvatar(searchResult, viewHolder.binding.avatar, R.dimen.avatar)
        val root = viewHolder.binding.root
        root.tag = searchResult
        root.setOnClickListener { listener?.onChannelSearchResult(searchResult) }
        root.setOnCreateContextMenuListener(this)
    }

    fun setOnChannelSearchResultSelectedListener(listener: OnChannelSearchResultSelected) {
        this.listener = listener
    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        val activity: Activity? = XmppActivity.find(v)
        val tag = v.tag
        if (activity != null && tag is Room) {
            activity.menuInflater.inflate(R.menu.channel_item_context, menu)
            this.current = tag
        }
    }

    interface OnChannelSearchResultSelected {
        fun onChannelSearchResult(result: Room)
    }

    class ViewHolder(val binding: ItemChannelDiscoveryBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Room>() {
            override fun areItemsTheSame(a: Room, b: Room): Boolean =
                a.address != null && a.address == b.address

            override fun areContentsTheSame(a: Room, b: Room): Boolean = a == b
        }
    }
}
