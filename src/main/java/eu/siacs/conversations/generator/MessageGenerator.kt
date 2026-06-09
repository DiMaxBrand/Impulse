package eu.siacs.conversations.generator

import eu.siacs.conversations.crypto.axolotl.AxolotlService
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlMessage
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import im.conversations.android.xmpp.model.correction.Replace
import im.conversations.android.xmpp.model.hints.Store
import im.conversations.android.xmpp.model.markers.Markable
import im.conversations.android.xmpp.model.fallback.Fallback
import im.conversations.android.xmpp.model.reply.Reply
import im.conversations.android.xmpp.model.retraction.Retract
import im.conversations.android.xmpp.model.unique.OriginId

class MessageGenerator(service: XmppConnectionService) : AbstractGenerator(service) {

    private fun preparePacket(message: Message): im.conversations.android.xmpp.model.stanza.Message {
        val conversation = message.conversation as Conversation
        val account: Account = conversation.getAccount()
        val packet = im.conversations.android.xmpp.model.stanza.Message()
        val isWithSelf = conversation.getContact().isSelf
        if (conversation.getMode() == Conversational.MODE_SINGLE) {
            packet.setTo(message.counterpart)
            packet.setType(im.conversations.android.xmpp.model.stanza.Message.Type.CHAT)
            if (!isWithSelf) {
                packet.addChild("request", "urn:xmpp:receipts")
            }
        } else if (message.isPrivateMessage) {
            packet.setTo(message.counterpart)
            packet.setType(im.conversations.android.xmpp.model.stanza.Message.Type.CHAT)
            packet.addChild("x", "http://jabber.org/protocol/muc#user")
            packet.addChild("request", "urn:xmpp:receipts")
        } else {
            packet.setTo(message.counterpart.asBareJid())
            packet.setType(im.conversations.android.xmpp.model.stanza.Message.Type.GROUPCHAT)
        }
        if (conversation.isSingleOrPrivateAndNonAnonymous && !message.isPrivateMessage) {
            packet.addExtension(Markable())
        }
        packet.setFrom(account.jid)
        packet.setId(message.getUuid())
        if (conversation.getMode() == Conversational.MODE_MULTI
            && !message.isPrivateMessage
            && !conversation.getMucOptions().stableId()
        ) {
            packet.addExtension(OriginId(message.getUuid()))
        }
        if (message.edited()) {
            packet.addExtension(Replace(message.editedIdWireFormat))
        }
        val repliedTo = message.repliedTo
        if (repliedTo != null) {
            val to = conversation.getContact().getAddress().asBareJid().toString()
            packet.addExtension(Reply.create(to, repliedTo))
        }
        return packet
    }

    fun generateAxolotlChat(
        message: Message,
        axolotlMessage: XmppAxolotlMessage?
    ): im.conversations.android.xmpp.model.stanza.Message? {
        val packet = preparePacket(message)
        if (axolotlMessage == null) {
            return null
        }
        packet.setAxolotlMessage(axolotlMessage.toElement())
        packet.setBody(OMEMO_FALLBACK_MESSAGE)
        packet.addExtension(Store())
        packet.addChild("encryption", "urn:xmpp:eme:0")
            .setAttribute("name", "OMEMO")
            .setAttribute("namespace", AxolotlService.PEP_PREFIX)
        return packet
    }

    fun generateKeyTransportMessage(
        to: Jid,
        axolotlMessage: XmppAxolotlMessage
    ): im.conversations.android.xmpp.model.stanza.Message {
        val packet = im.conversations.android.xmpp.model.stanza.Message()
        packet.setType(im.conversations.android.xmpp.model.stanza.Message.Type.CHAT)
        packet.setTo(to)
        packet.setAxolotlMessage(axolotlMessage.toElement())
        packet.addChild(Store())
        return packet
    }

    fun generateChat(message: Message): im.conversations.android.xmpp.model.stanza.Message {
        val packet = preparePacket(message)
        val content: String
        if (message.hasFileOnRemoteHost()) {
            val fileParams: Message.FileParams = message.fileParams
            content = fileParams.url
            packet.addChild("x", Namespace.OOB).addChild("url").setContent(content)
        } else {
            content = message.body
        }
        packet.setBody(content)
        return packet
    }

    fun generatePgpChat(message: Message): im.conversations.android.xmpp.model.stanza.Message {
        val packet = preparePacket(message)
        if (message.hasFileOnRemoteHost()) {
            val fileParams: Message.FileParams = message.fileParams
            val url = fileParams.url
            packet.setBody(url)
            packet.addChild("x", Namespace.OOB).addChild("url").setContent(url)
        } else {
            packet.setBody(PGP_FALLBACK_MESSAGE)
            if (message.encryption == Message.ENCRYPTION_DECRYPTED) {
                packet.addChild("x", "jabber:x:encrypted").setContent(message.encryptedBody)
            } else if (message.encryption == Message.ENCRYPTION_PGP) {
                packet.addChild("x", "jabber:x:encrypted").setContent(message.body)
            }
            packet.addChild("encryption", "urn:xmpp:eme:0")
                .setAttribute("namespace", "jabber:x:encrypted")
        }
        return packet
    }

    fun generateRetraction(message: Message): im.conversations.android.xmpp.model.stanza.Message {
        val conversation = message.conversation as Conversation
        val account: Account = conversation.getAccount()
        val packet = im.conversations.android.xmpp.model.stanza.Message()
        packet.setFrom(account.jid)
        if (conversation.getMode() == Conversational.MODE_SINGLE || message.isPrivateMessage) {
            packet.setTo(message.counterpart)
            packet.setType(im.conversations.android.xmpp.model.stanza.Message.Type.CHAT)
        } else {
            packet.setTo(message.counterpart.asBareJid())
            packet.setType(im.conversations.android.xmpp.model.stanza.Message.Type.GROUPCHAT)
        }
        val retract = Retract()
        retract.setAttribute("id", message.serverMsgId ?: message.getUuid())
        packet.addExtension(retract)
        val fallback = Fallback()
        fallback.setAttribute("for", Namespace.RETRACTION)
        packet.addExtension(fallback)
        packet.setBody(RETRACTION_FALLBACK_MESSAGE)
        return packet
    }

    companion object {
        private const val OMEMO_FALLBACK_MESSAGE =
            "I sent you an OMEMO encrypted message but your client doesn't seem to support that." +
                    " Find more information on https://conversations.im/omemo"
        private const val PGP_FALLBACK_MESSAGE =
            "I sent you a PGP encrypted message but your client doesn't seem to support that."
        private const val RETRACTION_FALLBACK_MESSAGE =
            "This person attempted to retract a previous message, but it's not supported by your client."
    }
}
