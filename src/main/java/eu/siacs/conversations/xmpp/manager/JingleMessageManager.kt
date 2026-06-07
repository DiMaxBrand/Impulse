package eu.siacs.conversations.xmpp.manager

import android.util.Log
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Contact
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.RtpSessionStatus
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xml.Element
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection
import eu.siacs.conversations.xmpp.jingle.Media
import im.conversations.android.xmpp.model.hints.Store
import im.conversations.android.xmpp.model.jingle.Jingle
import im.conversations.android.xmpp.model.jingle.Reason
import im.conversations.android.xmpp.model.jingle.apps.rtp.Description
import im.conversations.android.xmpp.model.jmi.Accept
import im.conversations.android.xmpp.model.jmi.Device
import im.conversations.android.xmpp.model.jmi.Finish
import im.conversations.android.xmpp.model.jmi.JingleMessage
import im.conversations.android.xmpp.model.jmi.Proceed
import im.conversations.android.xmpp.model.jmi.Propose
import im.conversations.android.xmpp.model.jmi.Reject
import im.conversations.android.xmpp.model.jmi.Retract
import im.conversations.android.xmpp.model.jmi.Ringing
import im.conversations.android.xmpp.model.receipts.Request
import im.conversations.android.xmpp.model.stanza.Message

class JingleMessageManager(
    private val service: XmppConnectionService,
    connection: XmppConnection
) : AbstractManager(service.applicationContext, connection) {

    fun processJingleMessage(
        packet: Message,
        counterpart: Jid,
        query: MessageArchiveManager.Query?,
        offlineMessagesRetrieved: Boolean,
        serverMsgId: String?,
        timestamp: Long?,
        status: Int
    ) {
        if (getManager(MultiUserChatManager::class.java).isMuc(packet)) {
            Log.d(Config.LOGTAG, "ignore JMI from MUC")
            return
        }
        val jingleMessage = packet.getExtension(JingleMessage::class.java)
        val sessionId: String? = jingleMessage.sessionId
        if (sessionId == null) {
            return
        }
        val remoteMsgId = packet.id
        val from = packet.from
        if (query == null && offlineMessagesRetrieved) {
            getManager(JingleManager::class.java).deliverMessage(packet, timestamp!!)
            val contact: Contact = account.roster.getContact(from)
            val sendReceipts =
                contact.showInContactList() || Config.JINGLE_MESSAGE_INIT_STRICT_OFFLINE_CHECK
            if (remoteMsgId != null && !contact.isSelf && sendReceipts) {
                getManager(DeliveryReceiptManager::class.java).processRequest(packet, null)
            }
        } else if ((query != null && query.isCatchup()) || !offlineMessagesRetrieved) {
            if (jingleMessage is Propose) {
                val description: Element? = jingleMessage.findChild("description")
                val namespace: String? = description?.namespace
                if (Namespace.JINGLE_APPS_RTP == namespace) {
                    val c =
                        this.service.findOrCreateConversation(
                            account, counterpart.asBareJid(), false, false
                        )
                    val preExistingMessage =
                        c.findRtpSession(sessionId, status)
                    if (preExistingMessage != null) {
                        preExistingMessage.setServerMsgId(serverMsgId)
                        database.updateMessage(preExistingMessage, true)
                        this.service.updateConversationUi()
                        return
                    }
                    val message =
                        eu.siacs.conversations.entities.Message(
                            c,
                            status,
                            eu.siacs.conversations.entities.Message.TYPE_RTP_SESSION,
                            sessionId
                        )
                    message.setServerMsgId(serverMsgId)
                    message.setTime(timestamp!!)
                    message.setBody(RtpSessionStatus(false, 0).toString())
                    c.add(message)
                    database.createMessage(message)
                }
            } else if (jingleMessage is Proceed) {
                val c =
                    this.service.findOrCreateConversation(
                        account, counterpart.asBareJid(), false, false
                    )
                val s =
                    if (packet.fromAccount(account))
                        eu.siacs.conversations.entities.Message.STATUS_RECEIVED
                    else
                        eu.siacs.conversations.entities.Message.STATUS_SEND
                val message = c.findRtpSession(sessionId, s)
                if (message != null) {
                    message.setBody(RtpSessionStatus(true, 0).toString())
                    if (serverMsgId != null) {
                        message.setServerMsgId(serverMsgId)
                    }
                    message.setTime(timestamp!!)
                    database.updateMessage(message, true)
                    this.service.updateConversationUi()
                } else {
                    Log.d(
                        Config.LOGTAG,
                        "unable to find original rtp session message for received propose"
                    )
                }
            } else if (jingleMessage is Finish) {
                Log.d(
                    Config.LOGTAG,
                    "received JMI 'finish' during MAM catch-up. Can be used to"
                            + " update success/failure and duration"
                )
            }
        } else {
            if (jingleMessage is Propose) {
                val description: Element? = jingleMessage.findChild("description")
                val namespace: String? = description?.namespace
                if (Namespace.JINGLE_APPS_RTP == namespace) {
                    val c =
                        this.service.findOrCreateConversation(
                            account, counterpart.asBareJid(), false, false
                        )
                    val preExistingMessage = c.findRtpSession(sessionId, status)
                    if (preExistingMessage != null) {
                        preExistingMessage.setServerMsgId(serverMsgId)
                        database.updateMessage(preExistingMessage, true)
                        this.service.updateConversationUi()
                        return
                    }
                    val message =
                        eu.siacs.conversations.entities.Message(
                            c,
                            status,
                            eu.siacs.conversations.entities.Message.TYPE_RTP_SESSION,
                            sessionId
                        )
                    message.setServerMsgId(serverMsgId)
                    message.setTime(timestamp!!)
                    message.setBody(RtpSessionStatus(true, 0).toString())
                    if (query!!.pagingOrder == MessageArchiveManager.PagingOrder.REVERSE) {
                        c.prepend(query.getActualInThisQuery(), message)
                    } else {
                        c.add(message)
                    }
                    query.incrementActualMessageCount()
                    database.createMessage(message)
                }
            }
        }
    }

    fun propose(with: Jid, sessionId: String, media: Set<Media>) {
        val packet = Message(Message.Type.CHAT)
        packet.setTo(with)
        packet.setId(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX + sessionId)
        val propose = packet.addExtension(Propose(sessionId))
        for (m in media) {
            val description = propose.addExtension(Description())
            description.setMedia(m)
        }
        packet.addExtension(Request())
        packet.addExtension(Store())
        this.connection.sendMessagePacket(packet)
    }

    fun retract(with: Jid, sessionId: String) {
        val packet = Message(Message.Type.CHAT)
        packet.setTo(with)
        packet.addExtension(Retract(sessionId))
        packet.addExtension(Store())
        this.connection.sendMessagePacket(packet)
    }

    fun reject(with: Jid, sessionId: String) {
        val packet = Message(Message.Type.CHAT)
        packet.setTo(with)
        packet.addExtension(Reject(sessionId))
        packet.addExtension(Store())
        this.connection.sendMessagePacket(packet)
    }

    fun ringing(with: Jid, sessionId: String) {
        val packet = Message(Message.Type.CHAT)
        packet.setTo(with)
        packet.addExtension(Ringing(sessionId))
        packet.addExtension(Store())
        this.connection.sendMessagePacket(packet)
    }

    fun accept(sessionId: String) {
        val packet = Message(Message.Type.CHAT)
        packet.setTo(account.jid.asBareJid())
        packet.addExtension(Accept(sessionId))
        packet.addExtension(Store())
        this.connection.sendMessagePacket(packet)
    }

    fun proceed(with: Jid, sessionId: String, deviceId: Int?) {
        val packet = Message(Message.Type.CHAT)
        packet.setId(JingleRtpConnection.JINGLE_MESSAGE_PROCEED_ID_PREFIX + sessionId)
        packet.setTo(with)
        val proceed = packet.addExtension(Proceed(sessionId))
        if (deviceId != null) {
            val device = proceed.addExtension(Device())
            device.setId(deviceId)
        }
        packet.addExtension(Store())
        this.connection.sendMessagePacket(packet)
    }

    fun finish(with: Jid, sessionId: String, reason: Reason) {
        val packet = Message()
        packet.setType(Message.Type.CHAT)
        packet.setTo(with)
        val finish = packet.addExtension(Finish(sessionId))
        val wrapper = finish.addExtension(Jingle.Reason())
        wrapper.addExtension(reason)
        packet.addExtension(Store())
        this.connection.sendMessagePacket(packet)
    }
}
