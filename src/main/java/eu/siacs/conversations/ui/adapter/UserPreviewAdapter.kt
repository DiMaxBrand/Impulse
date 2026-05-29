package eu.siacs.conversations.ui.adapter

import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.ItemUserPreviewBinding
import eu.siacs.conversations.entities.MucOptions
import eu.siacs.conversations.ui.XmppActivity
import eu.siacs.conversations.ui.util.AvatarWorkerTask
import eu.siacs.conversations.ui.util.MucDetailsContextMenuHelper

class UserPreviewAdapter :
    ListAdapter<MucOptions.User, UserPreviewAdapter.ViewHolder>(UserAdapter.DIFF),
    View.OnCreateContextMenuListener {

    private var selectedUser: MucOptions.User? = null

    override fun onCreateViewHolder(viewGroup: ViewGroup, position: Int): ViewHolder {
        return ViewHolder(
            DataBindingUtil.inflate(
                LayoutInflater.from(viewGroup.context),
                R.layout.item_user_preview,
                viewGroup,
                false
            )
        )
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val user = getItem(position)
        AvatarWorkerTask.loadAvatar(user, viewHolder.binding.avatar, R.dimen.media_size)
        viewHolder.binding.root.setOnClickListener { v ->
            val activity = XmppActivity.find(v)
            if (activity == null) {
                return@setOnClickListener
            }
            if (user.resource() == null) {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.user_has_left_conference, user.displayName),
                    Toast.LENGTH_SHORT
                ).show()
            }
            activity.highlightInMuc(user.conversation, user.resource())
        }
        viewHolder.binding.root.setOnCreateContextMenuListener(this)
        viewHolder.binding.root.tag = user
        viewHolder.binding.root.setOnLongClickListener { _ ->
            selectedUser = user
            false
        }
    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        MucDetailsContextMenuHelper.onCreateContextMenu(menu, v)
    }

    fun getSelectedUser(): MucOptions.User? = selectedUser

    class ViewHolder(val binding: ItemUserPreviewBinding) : RecyclerView.ViewHolder(binding.root)
}
