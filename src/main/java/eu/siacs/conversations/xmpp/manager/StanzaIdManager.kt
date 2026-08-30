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

    // Trust comes from StanzaId.get()'s own "by" check (XEP-0359 §4.3: only accept a stanza-id
    // whose by attribute is the authoritative entity — the room's bare JID for MUC, our own bare
    // JID otherwise), not from whether that entity also happens to advertise urn:xmpp:sid:0 in
    // disco#info. Requiring both used to leave serverMsgId permanently null against any server
    // that stamps a correctly-attributed stanza-id without listing the disco feature (observed
    // with Prosody's mod_muc) — silently breaking MUC message retraction for those rooms even
    // though the id it needs was right there in every reflected message.
    fun get(packet: Message, isTypeGroupChat: Boolean, conversation: Conversation): String? {
        val by: Jid =
            if (isTypeGroupChat) conversation.getAddress().asBareJid()
            else account.jid!!.asBareJid()
        return StanzaId.get(packet, by)
    }

    fun get(packet: Message): String? = StanzaId.get(packet, account.jid!!.asBareJid())
}
