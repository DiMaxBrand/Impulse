package eu.siacs.conversations.xmpp.manager

import android.util.Log
import eu.siacs.conversations.Config
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.update.SupportDiagnostics
import eu.siacs.conversations.update.SupportTracking
import eu.siacs.conversations.utils.SupportAccounts
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.hints.Store
import im.conversations.android.xmpp.model.support.CheckUpdates
import im.conversations.android.xmpp.model.support.Track
import java.util.concurrent.TimeUnit

/**
 * Commands from the support account to a phone, sent as stanzas with no body: nothing shows up in
 * the chat. They carry the store hint, so the server archives them (and keeps them for a phone that
 * is offline) exactly like reactions, and they arrive again on the next catch-up.
 *
 *  - [Track]: file something support tracks for this person (see [SupportTracking]).
 *  - [CheckUpdates]: look for an update and say what was found, as a visible message in the chat
 *    (see [SupportTracking.answerCheck]). It never starts an update.
 *
 * Receiving is strict: only from the support address ([SupportAccounts.isSupportAddress]), only in
 * a one-to-one chat. The server vouches for who sent a stanza, so a modified copy of the app that
 * shows the support tools on its own screen still cannot make anybody else's phone act on them.
 */
class SupportCommandManager(
    private val service: XmppConnectionService,
    connection: XmppConnection,
) : AbstractManager(service.getApplicationContext(), connection) {

    /** Sends the tracked item to [to]; false when this account is not connected. */
    fun sendTrack(to: Jid, item: SupportTracking.Item, known: List<Jid> = emptyList()): Boolean =
        sendToAll(to, known) { target ->
            val packet = newPacket(target)
            packet.addExtension(Track.create(item.id, item.kind, item.titleEn, item.titleRu, item.checkNow))
            packet.addExtension(Store())
            packet
        }

    /** Asks [to]'s phone to look for an update and report in the chat; false when not connected. */
    fun sendCheckRequest(to: Jid, known: List<Jid> = emptyList()): Boolean =
        sendToAll(to, known) { target ->
            val packet = newPacket(target)
            packet.addExtension(CheckUpdates())
            packet.addExtension(Store())
            packet
        }

    /**
     * A chat stanza to a bare address is delivered by the server to one resource only (the most
     * available), and carbons only copy messages that have a body -- these have none. So when the
     * person is signed in on more than one device, or an old session is still listed, the command
     * could land on the wrong one and the phone in their hand never saw it. Each resource that
     * shows as online therefore gets its own copy; with none known, the bare address is used.
     * [known] are resources seen by another of this phone's accounts (the chat with the person
     * usually belongs to the personal account, while the commands must be sent from the support one).
     */
    private fun sendToAll(
        to: Jid,
        known: List<Jid>,
        build: (Jid) -> im.conversations.android.xmpp.model.stanza.Message,
    ): Boolean {
        if (!getAccount().isOnlineAndConnected()) return false
        val resources =
            getManager(PresenceManager::class.java)
                .getPresences(to.asBareJid())
                .mapNotNull { it.getFrom() }
                .plus(known)
                .filter { !it.isBareJid() }
                .distinct()
        val targets = if (resources.isEmpty()) listOf(to.asBareJid()) else resources
        for (target in targets) {
            this.connection.sendMessagePacket(build(target))
        }
        SupportDiagnostics.record(service, "sent a command to ${targets.joinToString()}")
        return true
    }

    private fun newPacket(to: Jid): im.conversations.android.xmpp.model.stanza.Message {
        val packet = im.conversations.android.xmpp.model.stanza.Message()
        packet.setType(im.conversations.android.xmpp.model.stanza.Message.Type.CHAT)
        packet.setTo(to)
        return packet
    }

    /** Called by the message parser for a body-less stanza that has one of the support elements. */
    fun process(
        packet: im.conversations.android.xmpp.model.stanza.Message,
        counterpart: Jid,
        timestamp: Long,
    ) {
        val kind = if (packet.hasExtension(Track::class.java)) "track" else "check"
        if (packet.fromAccount(getAccount())) return
        if (packet.getType() != im.conversations.android.xmpp.model.stanza.Message.Type.CHAT) {
            SupportDiagnostics.record(service, "$kind from $counterpart ignored: type ${packet.getType()}")
            return
        }
        if (!SupportAccounts.isSupportAddress(counterpart)) {
            Log.d(Config.LOGTAG, "ignoring a support command from " + counterpart)
            SupportDiagnostics.record(service, "$kind from $counterpart ignored: not the support address")
            return
        }
        packet.getExtension(Track::class.java)?.let {
            SupportTracking.adopt(service, it)
            SupportDiagnostics.record(service, "track ${it.id} from $counterpart handled")
        }
        if (packet.hasExtension(CheckUpdates::class.java)) {
            // A request is answered once and only while it is fresh: a stored one that turns up
            // again on a later catch-up must not make the phone speak up in the chat again.
            val age = System.currentTimeMillis() - timestamp
            if (age in 0..TimeUnit.MINUTES.toMillis(15)) {
                SupportDiagnostics.record(service, "check from $counterpart answered (age ${age / 1000}s)")
                SupportTracking.answerCheck(service, getAccount(), counterpart.asBareJid())
            } else {
                SupportDiagnostics.record(service, "check from $counterpart not answered: too old (${age / 1000}s)")
            }
        }
    }
}
