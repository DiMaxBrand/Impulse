package eu.siacs.conversations.xmpp.manager

import eu.siacs.conversations.entities.ReceiptRequest
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import eu.siacs.conversations.xmpp.jingle.AbstractJingleConnection
import im.conversations.android.xmpp.model.hints.Store
import im.conversations.android.xmpp.model.receipts.Received
import im.conversations.android.xmpp.model.receipts.Request
import im.conversations.android.xmpp.model.stanza.Message

class DeliveryReceiptManager(
    private val service: XmppConnectionService,
    connection: XmppConnection
) : AbstractManager(service.applicationContext, connection) {

    fun processReceived(message: Message, query: MessageArchiveManager.Query?) {
        val received = message.getExtension(Received::class.java)
        val to = message.getTo()
        val from = message.getFrom()
        val account = this.getAccount()
        val id = received.getId()
        if (message.fromAccount(account)) {
            if (query != null && id != null && to != null) {
                query.removePendingReceiptRequest(ReceiptRequest(to, id))
            }
        }

        if (from == null || id == null) return

        if (id.startsWith(AbstractJingleConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX)) {
            val sessionId =
                id.substring(AbstractJingleConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX.length)
            getManager(JingleManager::class.java)
                .updateProposedSessionDiscovered(
                    from, sessionId, JingleManager.DeviceDiscoveryState.DISCOVERED
                )
        } else {
            this.service.markMessage(
                account,
                from.asBareJid(),
                id,
                eu.siacs.conversations.entities.Message.STATUS_SEND_RECEIVED
            )
        }
    }

    fun processRequest(packet: Message, query: MessageArchiveManager.Query?) {
        val remoteMsgId = packet.getId()
        val request = packet.hasExtension(Request::class.java)
        if (query == null) {
            if (request) {
                received(packet.getFrom(), remoteMsgId, packet.getType())
            }
        } else if (query.isCatchup()) { // TODO only for non group chat?
            if (request) {
                query.addPendingReceiptRequest(ReceiptRequest(packet.getFrom(), remoteMsgId))
            }
        }
    }

    fun received(to: Jid, id: String) {
        received(to, id, Message.Type.NORMAL)
    }

    private fun received(to: Jid, id: String?, type: Message.Type) {
        val message = Message()
        message.setType(type)
        message.setTo(to)
        message.addExtension(Received(id))
        message.addExtension(Store())
        this.connection.sendMessagePacket(message)
    }
}
