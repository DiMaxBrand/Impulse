package eu.siacs.conversations.xmpp.manager

import com.google.common.base.Strings
import com.google.common.util.concurrent.ListenableFuture
import eu.siacs.conversations.entities.Contact
import eu.siacs.conversations.services.QuickConversationsService
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.NodeConfiguration
import im.conversations.android.xmpp.model.nick.Nick
import im.conversations.android.xmpp.model.pubsub.Items

class NickManager(private val service: XmppConnectionService, connection: XmppConnection) :
    AbstractManager(service.applicationContext, connection) {

    fun handleItems(from: Jid?, items: Items) {
        val item = items.getFirstItem(Nick::class.java)
        val nick = if (item == null) null else item.getContent()
        if (from == null || Strings.isNullOrEmpty(nick)) {
            return
        }
        setNick(from, nick)
    }

    private fun setNick(user: Jid, nick: String?) {
        val account = getAccount()
        if (user.asBareJid() == account.getJid().asBareJid()) {
            account.setDisplayName(nick)
            if (QuickConversationsService.isQuicksy()) {
                service.getAvatarService().clear(account)
            }
            service.checkMucRequiresRename()
        } else {
            val contact: Contact = account.getRoster().getContact(user)
            if (contact.setPresenceName(nick)) {
                connection.getManager(RosterManager::class.java).writeToDatabaseAsync()
                service.getAvatarService().clear(contact)
            }
        }
        service.updateConversationUi()
        service.updateAccountUi()
    }

    fun publish(name: String?): ListenableFuture<Void?> {
        return if (Strings.isNullOrEmpty(name)) {
            getManager(PepManager::class.java).delete(Namespace.NICK)
        } else {
            getManager(PepManager::class.java)
                .publishSingleton(Nick(name), NodeConfiguration.PRESENCE)
        }
    }

    fun handleDelete(from: Jid) {
        this.setNick(from, null)
    }
}
