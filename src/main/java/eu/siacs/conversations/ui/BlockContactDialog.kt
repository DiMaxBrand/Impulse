package eu.siacs.conversations.ui

import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.databinding.DataBindingUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.DialogBlockContactBinding
import eu.siacs.conversations.entities.Blockable
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.ui.util.JidDialog
import eu.siacs.conversations.xmpp.manager.BlockingManager

object BlockContactDialog {

    @JvmStatic
    fun show(xmppActivity: XmppActivity, blockable: Blockable) {
        show(xmppActivity, blockable, null)
    }

    @JvmStatic
    fun show(xmppActivity: XmppActivity, blockable: Blockable, serverMsgId: String?) {
        val builder = MaterialAlertDialogBuilder(xmppActivity)
        val isBlocked = blockable.isBlocked()
        builder.setNegativeButton(R.string.cancel, null)
        val binding: DialogBlockContactBinding =
            DataBindingUtil.inflate(
                xmppActivity.layoutInflater,
                R.layout.dialog_block_contact,
                null,
                false
            )
        val reporting = blockable.getAccount()
            .xmppConnection
            .getManager(BlockingManager::class.java)
            .hasSpamReporting()
        if (reporting && !isBlocked) {
            binding.reportSpam.visibility = View.VISIBLE
            if (serverMsgId != null) {
                binding.reportSpam.isChecked = true
                binding.reportSpam.isEnabled = false
            } else {
                binding.reportSpam.isEnabled = true
            }
        } else {
            binding.reportSpam.visibility = View.GONE
        }
        builder.setView(binding.root)

        val account = blockable.getAccount()
        val manager = account.xmppConnection.getManager(BlockingManager::class.java)

        val value: String
        @StringRes val res: Int
        if (blockable.getAddress()!!.isFullJid) {
            builder.setTitle(
                if (isBlocked) R.string.action_unblock_participant else R.string.action_block_participant
            )
            value = blockable.getAddress()!!.toString()
            res = if (isBlocked) R.string.unblock_contact_text else R.string.block_contact_text
        } else if (blockable.getAddress()!!.local == null
            || manager.isBlocked(blockable.getAddress()!!.domain)
        ) {
            builder.setTitle(
                if (isBlocked) R.string.action_unblock_domain else R.string.action_block_domain
            )
            value = blockable.getAddress()!!.domain.toString()
            res = if (isBlocked) R.string.unblock_domain_text else R.string.block_domain_text
        } else {
            if (isBlocked) {
                builder.setTitle(R.string.action_unblock_contact)
            } else if (serverMsgId != null) {
                builder.setTitle(R.string.report_spam_and_block)
            } else {
                val resBlockAction =
                    if (blockable is Conversation && blockable.isWithStranger)
                        R.string.block_stranger
                    else
                        R.string.action_block_contact
                builder.setTitle(resBlockAction)
            }
            value = blockable.getAddress()!!.asBareJid().toString()
            res = if (isBlocked) R.string.unblock_contact_text else R.string.block_contact_text
        }
        binding.text.setText(JidDialog.style(xmppActivity, res, value))
        builder.setPositiveButton(if (isBlocked) R.string.unblock else R.string.block) { _, _ ->
            if (isBlocked) {
                xmppActivity.xmppConnectionService.sendUnblockRequest(blockable)
            } else {
                var toastShown = false
                if (xmppActivity.xmppConnectionService.sendBlockRequest(
                        blockable, binding.reportSpam.isChecked, serverMsgId
                    )
                ) {
                    Toast.makeText(
                        xmppActivity,
                        R.string.corresponding_chats_closed,
                        Toast.LENGTH_SHORT
                    ).show()
                    toastShown = true
                }
                if (xmppActivity is ContactDetailsActivity) {
                    if (!toastShown) {
                        Toast.makeText(
                            xmppActivity,
                            R.string.contact_blocked_past_tense,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    xmppActivity.finish()
                }
            }
        }
        builder.create().show()
    }
}
