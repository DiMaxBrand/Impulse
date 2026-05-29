package eu.siacs.conversations.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.databinding.DataBindingUtil
import com.google.android.material.color.MaterialColors
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.ItemAccountBinding
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.ui.XmppActivity
import eu.siacs.conversations.ui.util.AvatarWorkerTask
class AccountAdapter : ArrayAdapter<Account> {

    private val activity: XmppActivity
    private val showStateButton: Boolean

    constructor(activity: XmppActivity, objects: kotlin.collections.List<Account>, showStateButton: Boolean) :
            super(activity, 0, objects) {
        this.activity = activity
        this.showStateButton = showStateButton
    }

    constructor(activity: XmppActivity, objects: kotlin.collections.List<Account>) :
            super(activity, 0, objects) {
        this.activity = activity
        this.showStateButton = true
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        val account: Account? = getItem(position)
        val viewHolder: ViewHolder
        val resultView: View
        if (view == null) {
            val binding: ItemAccountBinding =
                DataBindingUtil.inflate(
                    LayoutInflater.from(parent.context),
                    R.layout.item_account,
                    parent,
                    false
                )
            resultView = binding.root
            viewHolder = ViewHolder(binding)
            resultView.tag = viewHolder
        } else {
            viewHolder = view.tag as ViewHolder
            resultView = view
        }
        if (account == null) {
            return resultView
        }
        viewHolder.binding.accountJid.text = account.jid.asBareJid().toString()
        AvatarWorkerTask.loadAvatar(account, viewHolder.binding.accountImage, R.dimen.avatar)
        val status = account.status
        if (account.isServiceOutage) {
            val sos = account.serviceOutageStatus
            if (sos != null && sos.isPlanned()) {
                viewHolder.binding.accountStatus.setText(R.string.account_status_service_outage_scheduled)
            } else {
                viewHolder.binding.accountStatus.setText(R.string.account_status_service_outage_known)
            }
        } else {
            viewHolder.binding.accountStatus.setText(status.readableId)
        }
        when (status) {
            Account.State.ONLINE ->
                viewHolder.binding.accountStatus.setTextColor(
                    MaterialColors.getColor(
                        viewHolder.binding.accountStatus,
                        androidx.appcompat.R.attr.colorPrimary
                    )
                )
            Account.State.DISABLED,
            Account.State.LOGGED_OUT,
            Account.State.CONNECTING ->
                viewHolder.binding.accountStatus.setTextColor(
                    MaterialColors.getColor(
                        viewHolder.binding.accountStatus,
                        com.google.android.material.R.attr.colorOnSurfaceVariant
                    )
                )
            else ->
                viewHolder.binding.accountStatus.setTextColor(
                    MaterialColors.getColor(
                        viewHolder.binding.accountStatus,
                        androidx.appcompat.R.attr.colorError
                    )
                )
        }
        val isDisabled = account.status == Account.State.DISABLED
        viewHolder.binding.tglAccountStatus.setOnCheckedChangeListener(null)
        viewHolder.binding.tglAccountStatus.isChecked = !isDisabled
        if (this.showStateButton) {
            viewHolder.binding.tglAccountStatus.visibility = View.VISIBLE
        } else {
            viewHolder.binding.tglAccountStatus.visibility = View.GONE
        }
        viewHolder.binding.tglAccountStatus.setOnCheckedChangeListener { _, b ->
            if (b == isDisabled && activity is OnTglAccountState) {
                activity.onClickTglAccountState(account, b)
            }
        }
        return resultView
    }

    private class ViewHolder(val binding: ItemAccountBinding)

    interface OnTglAccountState {
        fun onClickTglAccountState(account: Account, state: Boolean)
    }
}
