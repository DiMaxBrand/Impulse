package eu.siacs.conversations.ui

import android.os.Bundle
import android.text.Editable
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.entities.Blockable
import eu.siacs.conversations.entities.ListItem
import eu.siacs.conversations.entities.RawBlockable
import eu.siacs.conversations.ui.interfaces.OnBackendConnected
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.OnUpdateBlocklist
import eu.siacs.conversations.xmpp.manager.BlockingManager
import java.util.Collections

class BlocklistActivity : AbstractSearchableListItemActivity(), OnUpdateBlocklist {

    private var account: Account? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listView.setOnItemLongClickListener { _, _, position, _ ->
            BlockContactDialog.show(
                this@BlocklistActivity,
                getListItems()[position] as Blockable
            )
            true
        }
        this.binding.fab.show()
        this.binding.fab.setOnClickListener { showEnterJidDialog() }
    }

    override fun onBackendConnected() {
        for (account in xmppConnectionService.getAccounts()) {
            if (account.jid.toString() == intent.getStringExtra(EXTRA_ACCOUNT)) {
                this.account = account
                break
            }
        }
        filterContacts()
        val fragment: Fragment? = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG_DIALOG)
        if (fragment is OnBackendConnected) {
            fragment.onBackendConnected()
        }
    }

    override fun filterContacts(needle: String?) {
        getListItems().clear()
        val account = this.account
        if (account != null) {
            // TODO create getBlocklistAsListItems
            for (jid in account.xmppConnection.getManager(BlockingManager::class.java).blocklist) {
                val item: ListItem = if (jid.isFullJid) {
                    RawBlockable(account, jid)
                } else {
                    account.roster.getContact(jid)
                }
                if (item.match(needle)) {
                    getListItems().add(item)
                }
            }
            Collections.sort(getListItems())
        }
        listItemAdapter.notifyDataSetChanged()
    }

    protected fun showEnterJidDialog() {
        val ft: FragmentTransaction = supportFragmentManager.beginTransaction()
        val prev: Fragment? = supportFragmentManager.findFragmentByTag("dialog")
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)
        val dialog = EnterJidDialog.newInstance(
            null,
            getString(R.string.block_jabber_id),
            getString(R.string.block),
            null,
            account!!.jid.asBareJid().toString(),
            true,
            false
        )

        dialog.setOnEnterJidDialogPositiveListener { _, contactJid ->
            val blockable: Blockable = RawBlockable(account!!, contactJid)
            if (xmppConnectionService.sendBlockRequest(blockable, false, null)) {
                Toast.makeText(
                    this@BlocklistActivity,
                    R.string.corresponding_chats_closed,
                    Toast.LENGTH_SHORT
                ).show()
            }
            true
        }

        dialog.show(ft, "dialog")
    }

    override fun refreshUiReal() {
        val editable: Editable? = searchEditText?.text
        if (editable != null) {
            filterContacts(editable.toString())
        } else {
            filterContacts()
        }
    }

    override fun OnUpdateBlocklist(status: OnUpdateBlocklist.Status) {
        refreshUi()
    }
}
