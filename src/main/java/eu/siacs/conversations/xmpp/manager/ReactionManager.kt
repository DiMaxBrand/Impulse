package eu.siacs.conversations.xmpp.manager

import android.util.Log
import com.google.common.base.Strings
import com.google.common.collect.Collections2
import com.google.common.collect.ImmutableSet
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.entities.Reaction
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.utils.Emoticons
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.hints.Store
import im.conversations.android.xmpp.model.occupant.OccupantId
import im.conversations.android.xmpp.model.reactions.Reactions

class ReactionManager(private val service: XmppConnectionService, connection: XmppConnection) :
    AbstractManager(service.getApplicationContext(), connection) {

    fun processReactions(
        packet: im.conversations.android.xmpp.model.stanza.Message,
        counterpart: Jid,
        query: MessageArchiveManager.Query
    ) {
        val isTypeGroupChat =
            packet.getType() ==
                im.conversations.android.xmpp.model.stanza.Message.Type.GROUPCHAT
        val reactions =
            packet.getExtension(Reactions::class.java)
                ?: throw IllegalStateException(
                    "Called processReactions w/o checking if packet has any"
                )
        val account = getAccount()
        val conversation = this.service.find(account, counterpart.asBareJid())
        val user = getManager(MultiUserChatManager::class.java).getMucUser(packet, query)
        val reactingTo = reactions.getId()
        if (conversation == null || reactingTo == null) {
            return
        }
        if (isTypeGroupChat && conversation.getMode() == Conversational.MODE_MULTI) {
            val mucOptions =
                getManager(MultiUserChatManager::class.java).getOrCreateState(conversation)
            val occupant =
                if (mucOptions.occupantId()) packet.getOnlyExtension(OccupantId::class.java)
                else null
            val occupantId = occupant?.getId()
            if (occupantId != null) {
                val isReceived = user == null || !mucOptions.isOurAccount(user)
                val message: Message?
                val inMemoryMessage = conversation.findMessageWithServerMsgId(reactingTo)
                message =
                    inMemoryMessage
                        ?: this.getDatabase().getMessageWithServerMsgId(conversation, reactingTo)
                if (message != null) {
                    val combinedReactions =
                        Reaction.withOccupantId(
                            message.getReactions(),
                            reactions.getReactions(),
                            isReceived,
                            counterpart,
                            user?.getRealJid(),
                            occupantId
                        )
                    message.setReactions(combinedReactions)
                    this.getDatabase().updateMessage(message, false)
                    this.service.updateConversationUi()
                } else {
                    Log.d(Config.LOGTAG, "message with id $reactingTo not found")
                }
            } else {
                Log.d(Config.LOGTAG, "received reaction in channel w/o occupant ids. ignoring")
            }
        } else {
            val message: Message?
            val inMemoryMessage = conversation.findMessageWithUuidOrRemoteId(reactingTo)
            message =
                inMemoryMessage
                    ?: this.getDatabase().getMessageWithUuidOrRemoteId(conversation, reactingTo)
            if (message == null) {
                Log.d(Config.LOGTAG, "message with id $reactingTo not found")
                return
            }
            val isReceived: Boolean
            val reactionFrom: Jid
            if (conversation.getMode() == Conversational.MODE_MULTI) {
                Log.d(Config.LOGTAG, "received reaction as MUC PM. triggering validation")
                val mucOptions =
                    getManager(MultiUserChatManager::class.java).getOrCreateState(conversation)
                val occupant =
                    if (mucOptions.occupantId())
                        packet.getOnlyExtension(OccupantId::class.java)
                    else null
                val occupantId = occupant?.getId()
                if (occupantId == null) {
                    Log.d(
                        Config.LOGTAG,
                        "received reaction via PM channel w/o occupant ids. ignoring"
                    )
                    return
                }
                isReceived = user == null || !mucOptions.isOurAccount(user)
                if (isReceived) {
                    reactionFrom = counterpart
                } else {
                    if (occupantId != message.getOccupantId()) {
                        Log.d(
                            Config.LOGTAG,
                            "reaction received via MUC PM did not pass validation"
                        )
                        return
                    }
                    reactionFrom = account.getJid().asBareJid()
                }
            } else {
                if (packet.fromAccount(account)) {
                    isReceived = false
                    reactionFrom = account.getJid().asBareJid()
                } else {
                    isReceived = true
                    reactionFrom = counterpart
                }
            }
            val combinedReactions =
                Reaction.withFrom(
                    message.getReactions(),
                    reactions.getReactions(),
                    isReceived,
                    reactionFrom
                )
            message.setReactions(combinedReactions)
            this.service.updateMessage(message, false)
        }
    }

    fun sendReactions(message: Message, reactions: Collection<String>): Boolean {
        val conversation: Conversation =
            if (message.getConversation() is Conversation) message.getConversation() as Conversation
            else return false
        val isPrivateMessage = message.isPrivateMessage()
        val reactTo: Jid
        val typeGroupChat: Boolean
        val reactToId: String?
        val combinedReactions: Collection<Reaction>
        if (conversation.getMode() == Conversational.MODE_MULTI && !isPrivateMessage) {
            val mucOptions = conversation.getMucOptions()
            if (!mucOptions.participating()) {
                Log.e(Config.LOGTAG, "not participating in MUC")
                return false
            }
            val self = mucOptions.getSelf()
            val occupantId = self.getOccupantId()
            if (Strings.isNullOrEmpty(occupantId)) {
                Log.e(Config.LOGTAG, "occupant id not found for reaction in MUC")
                return false
            }
            val existingRaw =
                ImmutableSet.copyOf(Collections2.transform(message.getReactions()) { r -> r.reaction })
            val reactionsAsExistingVariants =
                ImmutableSet.copyOf(
                    Collections2.transform(reactions) { r: String ->
                        Emoticons.existingVariant(r, existingRaw)
                    }
                )
            if (reactions != reactionsAsExistingVariants) {
                Log.d(Config.LOGTAG, "modified reactions to existing variants")
            }
            reactToId = message.getServerMsgId()
            reactTo = conversation.getAddress().asBareJid()
            typeGroupChat = true
            combinedReactions =
                Reaction.withOccupantId(
                    message.getReactions(),
                    reactionsAsExistingVariants,
                    false,
                    self.getFullJid(),
                    conversation.getAccount().getJid(),
                    occupantId!!
                )
        } else {
            reactToId =
                if (message.isCarbon() || message.getStatus() == Message.STATUS_RECEIVED) {
                    message.getRemoteMsgId()
                } else {
                    message.getUuid()
                }
            typeGroupChat = false
            reactTo =
                if (isPrivateMessage) {
                    message.getCounterpart()
                } else {
                    conversation.getAddress().asBareJid()
                }
            combinedReactions =
                Reaction.withFrom(
                    message.getReactions(),
                    reactions,
                    false,
                    conversation.getAccount().getJid()
                )
        }
        if (reactTo == null || Strings.isNullOrEmpty(reactToId)) {
            Log.e(Config.LOGTAG, "could not find id to react to")
            return false
        }
        val reactionMessage = reaction(reactTo, typeGroupChat, reactToId!!, reactions)
        this.connection.sendMessagePacket(reactionMessage)
        message.setReactions(combinedReactions)
        this.getDatabase().updateMessage(message, false)
        this.service.updateConversationUi()
        return true
    }

    companion object {
        private fun reaction(
            to: Jid,
            groupChat: Boolean,
            reactingTo: String,
            ourReactions: Collection<String>
        ): im.conversations.android.xmpp.model.stanza.Message {
            val packet = im.conversations.android.xmpp.model.stanza.Message()
            packet.setType(
                if (groupChat)
                    im.conversations.android.xmpp.model.stanza.Message.Type.GROUPCHAT
                else im.conversations.android.xmpp.model.stanza.Message.Type.CHAT
            )
            packet.setTo(to)
            val reactions = packet.addExtension(Reactions())
            reactions.setId(reactingTo)
            for (ourReaction in ourReactions) {
                reactions.addExtension(
                    im.conversations.android.xmpp.model.reactions.Reaction(ourReaction)
                )
            }
            packet.addExtension(Store())
            return packet
        }
    }
}
