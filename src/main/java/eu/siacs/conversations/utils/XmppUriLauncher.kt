package eu.siacs.conversations.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.google.common.collect.Iterables
import de.gultsch.common.MiniUri
import eu.siacs.conversations.Config
import eu.siacs.conversations.Conversations
import eu.siacs.conversations.R
import eu.siacs.conversations.persistance.DatabaseBackend
import eu.siacs.conversations.ui.EditAccountActivity
import eu.siacs.conversations.ui.ShareWithActivity
import eu.siacs.conversations.ui.StartConversationActivity
import java.util.Objects

class XmppUriLauncher {

    private val context: Context
    private val scanned: Boolean

    constructor(context: AppCompatActivity) : this(context, false)

    constructor(context: Context, scanned: Boolean) {
        this.context = context
        this.scanned = scanned
    }

    fun launch(xmppUri: MiniUri.Xmpp) {
        val accounts = Conversations.getInstance(context).getAccounts()
        launch(accounts, xmppUri)
    }

    private fun launch(
        accounts: Collection<DatabaseBackend.AccountWithOptions>,
        uri: MiniUri.Xmpp
    ) {
        val addresses = DatabaseBackend.AccountWithOptions.getAddresses(accounts)
        Log.d(Config.LOGTAG, "trying to launch: $uri")
        val intent: Intent
        val jid = uri.asJid()
        if (SignupUtils.isSupportTokenRegistry() && uri.isAddress && jid != null) {
            val preAuth = uri.getParameter(MiniUri.Xmpp.PARAMETER_PRE_AUTH)
            if (uri.isAction(MiniUri.Xmpp.ACTION_REGISTER)) {
                if (jid.local != null && addresses.contains(jid.asBareJid())) {
                    showError(R.string.account_already_exists)
                    return
                }
                intent = SignupUtils.getTokenRegistrationIntent(context, jid, preAuth)
                this.context.startActivity(intent)
                return
            }
            if (!DatabaseBackend.AccountWithOptions.hasEnabledAccount(accounts)
                && uri.isAction(MiniUri.Xmpp.ACTION_ROSTER)
                && uri.isYesIbr
            ) {
                intent = SignupUtils.getTokenRegistrationIntent(context, jid.domain, preAuth)
                intent.putExtra(StartConversationActivity.EXTRA_INVITE_URI, uri.asUri().toString())
                this.context.startActivity(intent)
                return
            }
        } else if (uri.isAction(MiniUri.Xmpp.ACTION_REGISTER)) {
            showError(R.string.account_registrations_are_not_supported)
            return
        }

        if (accounts.isEmpty()) {
            if (uri.isAddress) {
                intent = SignupUtils.getSignUpIntent(context)
                intent.putExtra(StartConversationActivity.EXTRA_INVITE_URI, uri.asUri().toString())
                this.context.startActivity(intent)
            } else {
                showError(R.string.invalid_jid)
            }
            return
        }

        if (uri.isAction(MiniUri.Xmpp.ACTION_MESSAGE)) {
            val body = uri.getParameter("body")
            if (jid != null) {
                val clazz = findShareViaAccountClass()
                if (clazz != null) {
                    intent = Intent(context, clazz)
                    intent.putExtra("contact", jid.toString())
                    intent.putExtra("body", body)
                } else {
                    intent = Intent(context, StartConversationActivity::class.java)
                    intent.action = Intent.ACTION_VIEW
                    intent.data = uri.asUri()
                    intent.putExtra(
                        "account",
                        Objects.requireNonNull(Iterables.getFirst(addresses, null)).toString()
                    )
                }
            } else {
                intent = Intent(context, ShareWithActivity::class.java)
                intent.action = Intent.ACTION_SEND
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, body)
            }
        } else if (jid != null && addresses.contains(jid)) {
            intent = Intent(context, EditAccountActivity::class.java)
            intent.action = Intent.ACTION_VIEW
            intent.putExtra("jid", jid.asBareJid().toString())
            intent.data = uri.asUri()
            intent.putExtra("scanned", scanned)
        } else if (uri.isAddress) {
            intent = Intent(context, StartConversationActivity::class.java)
            intent.action = Intent.ACTION_VIEW
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            intent.putExtra("scanned", scanned)
            intent.data = uri.asUri()
        } else {
            showError(R.string.invalid_jid)
            return
        }
        this.context.startActivity(intent)
    }

    private fun showError(@StringRes error: Int) {
        Toast.makeText(this.context, error, Toast.LENGTH_LONG).show()
    }

    companion object {
        private fun findShareViaAccountClass(): Class<*>? {
            return try {
                Class.forName("eu.siacs.conversations.ui.ShareViaAccountActivity")
            } catch (e: ClassNotFoundException) {
                null
            }
        }
    }
}
