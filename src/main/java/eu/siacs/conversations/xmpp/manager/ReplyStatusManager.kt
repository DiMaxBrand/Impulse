package eu.siacs.conversations.xmpp.manager

import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.ui.ReplyStatuses
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.hints.Store
import im.conversations.android.xmpp.model.support.ReplyStatus
import im.conversations.android.xmpp.model.stanza.Message

class ReplyStatusManager(private val service: XmppConnectionService, connection: XmppConnection) :
    AbstractManager(service.applicationContext, connection) {
    fun send(peer: Jid, code: String, clearOnSend: Boolean): Boolean {
        val account = getAccount().getUuid() ?: return false
        if (code !in ReplyStatuses.codes || !getAccount().isOnlineAndConnected) return false
        val bare = peer.asBareJid()
        val previous = ReplyStatuses.get(service, account, bare.toString(), true)
        val stamp = maxOf(System.currentTimeMillis(), (previous?.stamp ?: 0) + 1)
        val status = ReplyStatuses.Status(code, stamp, if (code == "clear") 0 else stamp + ReplyStatuses.TTL_MS, clearOnSend)
        fun packet(to: Jid, ownCopy: Boolean): Message = Message().apply {
            setType(Message.Type.CHAT); setTo(to)
            addExtension(ReplyStatus.create(code, status.stamp, status.expires, clearOnSend, if (ownCopy) bare.toString() else null))
            addExtension(Store())
        }
        val targets = getManager(PresenceManager::class.java).getPresences(bare).mapNotNull { it.from }
            .filter { !it.isBareJid }.distinct().ifEmpty { listOf(bare) }
        targets.forEach { connection.sendMessagePacket(packet(it, false)) }
        val self = getAccount().jid.asBareJid()
        val devices = getManager(PresenceManager::class.java).getPresences(self).mapNotNull { it.from }
            .filter { !it.isBareJid && it != getAccount().jid }.distinct().ifEmpty { listOf(self) }
        devices.forEach { connection.sendMessagePacket(packet(it, true)) }
        ReplyStatuses.adopt(service, account, bare.toString(), true, status)
        return true
    }

    fun clearAfterSend(peer: Jid) {
        val account = getAccount().getUuid() ?: return
        val own = ReplyStatuses.get(service, account, peer.asBareJid().toString(), true)
        if (own?.active(System.currentTimeMillis()) == true && own.clearOnSend && !send(peer, "clear", true)) {
            // The outgoing message may be queued offline. Its eventual receipt also clears
            // the peer's indicator; clear our own chip immediately without waiting for transport.
            ReplyStatuses.adopt(service, account, peer.asBareJid().toString(), true,
                ReplyStatuses.Status("clear", maxOf(System.currentTimeMillis(), own.stamp + 1), 0, true))
        }
    }

    fun process(packet: Message, counterpart: Jid) {
        val account = getAccount().getUuid() ?: return
        if (packet.getType() != Message.Type.CHAT) return
        val element = packet.getExtension(ReplyStatus::class.java) ?: return
        val own = packet.fromAccount(getAccount())
        val peer = if (own) {
            element.getAttribute("peer")?.let { try { Jid.of(it).asBareJid() } catch (_: Exception) { null } }
                ?: if (counterpart.asBareJid() != getAccount().jid.asBareJid()) counterpart.asBareJid() else return
        } else counterpart.asBareJid()
        if (peer == getAccount().jid.asBareJid()) return
        val code = element.getAttribute("state") ?: return
        val stamp = element.getAttribute("updated")?.toLongOrNull() ?: return
        val expires = element.getAttribute("expires")?.toLongOrNull() ?: return
        ReplyStatuses.adopt(service, account, peer.toString(), own,
            ReplyStatuses.Status(code, stamp, expires, element.getAttribute("clear-on-send") != "false"))
    }
}
