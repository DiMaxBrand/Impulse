package eu.siacs.conversations.ui.adapter

import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.helper.widget.Flow
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import com.google.android.material.listitem.ListItemLayout
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.ItemContactBinding
import eu.siacs.conversations.entities.Contact
import eu.siacs.conversations.entities.ListItem
import eu.siacs.conversations.ui.BlocklistActivity
import eu.siacs.conversations.ui.XmppActivity
import eu.siacs.conversations.ui.util.AvatarWorkerTask
import eu.siacs.conversations.utils.IrregularUnicodeDetector
import eu.siacs.conversations.xmpp.Jid
import im.conversations.android.model.DynamicTag
import java.util.function.Consumer

open class ListItemAdapter : ArrayAdapter<ListItem> {

    protected var activity: XmppActivity
    private var showDynamicTags = false
    private val mOnTagClickedListener: Consumer<DynamicTag>?
    private val isBlockNoteworthy: Boolean

    constructor(activity: XmppActivity, objects: kotlin.collections.List<ListItem>) :
            this(activity, objects, null)

    constructor(
        activity: XmppActivity,
        objects: kotlin.collections.List<ListItem>,
        onTagClickedListener: Consumer<DynamicTag>?
    ) : super(activity, 0, objects) {
        this.activity = activity
        this.mOnTagClickedListener = onTagClickedListener
        this.isBlockNoteworthy = activity !is BlocklistActivity
    }

    fun refreshSettings() {
        val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        this.showDynamicTags = preferences.getBoolean(AppSettings.SHOW_DYNAMIC_TAGS, false)
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        val inflater: LayoutInflater = activity.layoutInflater
        val item: ListItem? = getItem(position)
        val viewHolder: ViewHolder
        val resultView: View
        if (view == null) {
            val binding: ItemContactBinding =
                DataBindingUtil.inflate(inflater, R.layout.item_contact, parent, false)
            viewHolder = ViewHolder.get(binding)
            resultView = binding.root
        } else {
            viewHolder = view.tag as ViewHolder
            resultView = view
        }

        val tags = item?.getTags()
        if (tags != null && ((isBlockNoteworthy && Contact.isNoteworthy(tags)) || this.showDynamicTags)) {
            UserAdapter.setHats(viewHolder.tags, tags, mOnTagClickedListener)
        } else {
            viewHolder.tags.visibility = View.GONE
        }
        val jid: Jid? = item?.getAddress()
        if (jid != null) {
            viewHolder.jid.visibility = View.VISIBLE
            viewHolder.jid.text = IrregularUnicodeDetector.style(activity, jid)
        } else {
            viewHolder.jid.visibility = View.GONE
        }
        viewHolder.name.text = item?.displayName
        if (item != null) {
            AvatarWorkerTask.loadAvatar(item, viewHolder.avatar, R.dimen.avatar)
        }
        (resultView as ListItemLayout).updateAppearance(position, count)
        return resultView
    }

    private class ViewHolder private constructor() {
        lateinit var name: TextView
        lateinit var jid: TextView
        lateinit var avatar: ImageView
        lateinit var tags: ConstraintLayout
        lateinit var flowWidget: Flow

        companion object {
            fun get(binding: ItemContactBinding): ViewHolder {
                val viewHolder = ViewHolder()
                viewHolder.name = binding.contactDisplayName
                viewHolder.jid = binding.contactJid
                viewHolder.avatar = binding.contactPhoto
                viewHolder.tags = binding.tags
                viewHolder.flowWidget = binding.flowWidget
                binding.root.tag = viewHolder
                return viewHolder
            }
        }
    }
}
