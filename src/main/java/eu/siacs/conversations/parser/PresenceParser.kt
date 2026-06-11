package eu.siacs.conversations.parser

import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xmpp.XmppConnection
import eu.siacs.conversations.xmpp.manager.MultiUserChatManager
import eu.siacs.conversations.xmpp.manager.PresenceManager
import im.conversations.android.xmpp.model.muc.MultiUserChat
import im.conversations.android.xmpp.model.muc.user.MucUser
import im.conversations.android.xmpp.model.stanza.Presence
import java.util.function.Consumer

class PresenceParser(service: XmppConnectionService, connection: XmppConnection) :
    AbstractParser(service, connection), Consumer<Presence> {

    override fun accept(presence: Presence) {
        val multiUserChatManager = getManager(MultiUserChatManager::class.java)
        val type = presence.getType()
        if ((type == null || type == Presence.Type.UNAVAILABLE) && presence.hasExtension(MucUser::class.java)) {
            multiUserChatManager.handlePresence(presence)
        } else if (type == Presence.Type.ERROR &&
            (presence.hasExtension(MultiUserChat::class.java) || multiUserChatManager.isMuc(presence.getFrom()))
        ) {
            multiUserChatManager.handleErrorPresence(presence)
        } else {
            getManager(PresenceManager::class.java).handlePresence(presence)
        }
    }
}
