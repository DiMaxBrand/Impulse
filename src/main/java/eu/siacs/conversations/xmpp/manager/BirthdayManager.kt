package eu.siacs.conversations.xmpp.manager

import android.content.Context
import com.google.common.util.concurrent.ListenableFuture
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.utils.BirthdayDate
import eu.siacs.conversations.utils.BirthdayStore
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.NodeConfiguration
import im.conversations.android.xmpp.model.birthday.Birthday
import im.conversations.android.xmpp.model.pubsub.Items

/**
 * Birthdays over PEP. We publish our own with the "presence" access model (roster only) and are
 * told about our contacts' (the node is in our entity capabilities with +notify, so the server
 * pushes the latest value on login and whenever it changes).
 */
class BirthdayManager(private val appContext: Context, connection: XmppConnection) :
    AbstractManager(appContext, connection) {

    private val store = BirthdayStore(appContext)

    fun handleItems(from: Jid?, items: Items) {
        if (from == null) return
        val value = items.getFirstItem(Birthday::class.java)?.getContent()
        remember(from, BirthdayDate.parse(value))
    }

    fun handleDelete(from: Jid) = remember(from, null)

    private fun remember(user: Jid, birthday: BirthdayDate?) {
        store.set(getAccount().getUuid() ?: return, user.asBareJid().toString(), birthday)
        // Anything showing a contact (chat banner, contact details) refreshes through this.
        (appContext as? XmppConnectionService)?.updateConversationUi()
    }

    /** Publishes our birthday, or removes it when [birthday] is null. */
    fun publish(birthday: BirthdayDate?): ListenableFuture<Void?> {
        getAccount().getUuid()?.let {
            store.set(it, getAccount().getJid().asBareJid().toString(), birthday)
        }
        return if (birthday == null) {
            getManager(PepManager::class.java).delete(Namespace.BIRTHDAY)
        } else {
            getManager(PepManager::class.java)
                .publishSingleton(Birthday(birthday.encode()), NodeConfiguration.PRESENCE)
        }
    }
}
