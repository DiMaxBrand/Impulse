package eu.siacs.conversations.ui.widget

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.collect.Collections2
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.ui.XmppActivity
import eu.siacs.conversations.xmpp.XmppConnection
import eu.siacs.conversations.xmpp.manager.EasyOnboardingManager
import java.util.Collections
import java.util.Objects
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.function.Function

open class AccountPickerDialog(
    private val xmppActivity: XmppActivity,
    private val criteria: Function<XmppConnection, Boolean>
) {

    private fun getFilteredAccounts(): List<Account> {
        val service = this.xmppActivity.xmppConnectionService
            ?: return Collections.emptyList()
        val unfiltered = service.getAccounts()
        val enabled = Collections2.filter(unfiltered) { a -> Objects.requireNonNull(a).isEnabled }
        return ImmutableList.copyOf(
            Collections2.filter(enabled) { a ->
                val c = a?.xmppConnection
                c != null && criteria.apply(c)
            }
        )
    }

    fun hasAnyAccounts(): Boolean = !this.getFilteredAccounts().isEmpty()

    fun pick(picked: Consumer<Account>) {
        val accounts = getFilteredAccounts()
        if (accounts.isEmpty()) {
            // TODO show toast
            return
        }
        if (accounts.size == 1) {
            picked.accept(Iterables.getOnlyElement(accounts))
            return
        }
        val selectedAccount = AtomicReference(accounts[0])
        val alertDialogBuilder = MaterialAlertDialogBuilder(xmppActivity)
        alertDialogBuilder.setTitle(R.string.choose_account)
        val asStrings = asStrings(accounts)
        alertDialogBuilder.setSingleChoiceItems(asStrings, 0) { _, which ->
            selectedAccount.set(accounts[which])
        }
        alertDialogBuilder.setNegativeButton(R.string.cancel, null)
        alertDialogBuilder.setPositiveButton(R.string.ok) { _, _ -> picked.accept(selectedAccount.get()) }
        alertDialogBuilder.create().show()
    }

    class EasyInvite(xmppActivity: XmppActivity) : AccountPickerDialog(
        xmppActivity,
        Function { xmppConnection ->
            xmppConnection.getManager(EasyOnboardingManager::class.java).hasCreateAccountFeature()
        }
    )

    class Enabled(xmppActivity: XmppActivity) : AccountPickerDialog(
        xmppActivity,
        Function { _ -> true }
    )

    companion object {
        private fun asStrings(accounts: Collection<Account>): Array<String> =
            Collections2.transform(accounts) { a -> a!!.jid.asBareJid().toString() }
                .toTypedArray()
    }
}
