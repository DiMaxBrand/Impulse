package eu.siacs.conversations.xmpp.manager

import android.content.Context
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.stanza.Message
import im.conversations.android.xmpp.model.unique.StanzaId

class StanzaIdManager(context: Context, connection: XmppConnection) :
    AbstractManager(context, connection) {

    fun hasFeature(): Boolean =
        getManager(DiscoManager::class.java).hasAccountFeature(Namespace.STANZA_IDS)

    fun get(packet: Message, isTypeGroupChat: Boolean, conversation: Conversation): String? {
        val by: Jid
        val safeToExtract: Boolean
        if (isTypeGroupChat) {
            by = conversation.address.asBareJid()
            val state = getManager(MultiUserChatManager::class.java).getState(by)
            safeToExtract = state != null && state.hasFeature(Namespace.STANZA_IDS)
        } else {
            by = account.jid!!.asBareJid()
            safeToExtract = hasFeature()
        }
        return if (safeToExtract) StanzaId.get(packet, by) else null
    }

    fun get(packet: Message): String? =
        if (hasFeature()) StanzaId.get(packet, account.jid!!.asBareJid()) else null
}
