package eu.siacs.conversations.xmpp.manager

import android.util.Log
import com.google.common.collect.Collections2
import com.google.common.collect.ImmutableSet
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.moderation.Moderate
import im.conversations.android.xmpp.model.retraction.Retract
import im.conversations.android.xmpp.model.stanza.Iq
import im.conversations.android.xmpp.model.stanza.Message

class ModerationManager(
    private val service: XmppConnectionService,
    connection: XmppConnection
) : AbstractManager(service.applicationContext, connection) {

    fun moderate(message: eu.siacs.conversations.entities.Message): ListenableFuture<Void?> {
        val serverMsgId = message.getServerMsgId()
        val previous = message.getEditedServerMessageIds()
        if (!previous.isEmpty()) {
            Log.d(
                Config.LOGTAG,
                "${getAccount().getJid()}: requesting deletion of previous stanza-ids: $previous"
            )
        }
        val serverMsgIds =
            ImmutableSet.Builder<String>().add(serverMsgId).addAll(previous).build()
        val conversation = message.getConversation()
        val address = conversation.getAddress().asBareJid()
        val future = moderate(address, serverMsgIds)
        return Futures.transform(
            future,
            { _ ->
                if (message.getConversation() is Conversation) {
                    val c = message.getConversation() as Conversation
                    c.remove(message)
                    if (getDatabase().deleteMessage(message.getUuid())) {
                        Log.d(Config.LOGTAG, "deleted local copy of moderated message")
                        deleteAssociatedFile(message)
                    }
                    this.service.updateConversationUi()
                    null
                } else {
                    throw IllegalStateException("Message was not part of conversation")
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun moderate(address: Jid, ids: Collection<String>): ListenableFuture<List<Iq>> {
        val futures = Collections2.transform(ids) { id ->
            val iq = Iq(Iq.Type.SET)
            iq.setTo(address)
            val moderate = iq.addExtension(Moderate(id))
            moderate.addExtension(Retract())
            this.connection.sendIqPacket(iq)
        }
        return Futures.allAsList(futures)
    }

    fun handleRetraction(message: Message) {
        val account = getAccount()
        val from = Jid.Invalid.getNullForInvalid(message.getFrom())
        if (from == null) return
        if (message.getType() == Message.Type.CHAT) {
            handleDirectRetraction(message, from)
            return
        }
        if (from.isFullJid() || message.getType() != Message.Type.GROUPCHAT) {
            Log.d(
                Config.LOGTAG,
                "received retraction from $from but retractions are only supported in MUC"
            )
            return
        }
        val mucOptions = getManager(MultiUserChatManager::class.java).getState(from.asBareJid())
        if (mucOptions == null) {
            Log.d(
                Config.LOGTAG,
                "${account.getJid().asBareJid()}: received retraction in MUC w/o state"
            )
            return
        }
        if (mucOptions.isPrivateAndNonAnonymous()) {
            Log.d(
                Config.LOGTAG,
                "${account.getJid().asBareJid()}: retractions are only supported in public channels"
            )
            return
        }
        val retraction = message.getExtension(Retract::class.java)
        val stanzaId = if (retraction == null) null else retraction.getId()
        if (stanzaId == null) {
            Log.d(Config.LOGTAG, "retraction was missing stanza-id")
            return
        }
        val conversation = mucOptions.getConversation()
        val inMemoryMessage = conversation.findMessageWithServerMsgId(stanzaId)
        val retractedMessage: eu.siacs.conversations.entities.Message? =
            inMemoryMessage ?: getDatabase().getMessageWithServerMsgId(conversation, stanzaId)
        if (retractedMessage == null) {
            Log.d(Config.LOGTAG, "received retraction for $stanzaId. Message not found.")
            return
        }
        val moderated = retraction!!.getModerated()
        val by = if (moderated == null) null else moderated.getBy()
        conversation.remove(retractedMessage)
        this.service.getNotificationService().clear(retractedMessage)
        if (getDatabase().deleteMessage(retractedMessage.getUuid())) {
            Log.d(
                Config.LOGTAG,
                "received retraction for $stanzaId in $from by $by"
            )
            deleteAssociatedFile(retractedMessage)
        }
        this.service.updateConversationUi()
    }

    private fun handleDirectRetraction(message: Message, from: Jid) {
        val retraction = message.getExtension(Retract::class.java) ?: return
        val stanzaId = retraction.getId()
        if (stanzaId == null) {
            Log.d(Config.LOGTAG, "1:1 retraction was missing stanza-id")
            return
        }
        val conversation = service.find(getAccount(), from.asBareJid())
        if (conversation == null) {
            Log.d(Config.LOGTAG, "received 1:1 retraction but conversation with ${from.asBareJid()} not found")
            return
        }
        val inMemoryMessage = conversation.findMessageWithUuidOrRemoteId(stanzaId)
        val retractedMessage = inMemoryMessage
            ?: getDatabase().getMessageWithUuidOrRemoteId(conversation, stanzaId)
        if (retractedMessage == null) {
            Log.d(Config.LOGTAG, "received 1:1 retraction for $stanzaId — message not found")
            return
        }
        conversation.remove(retractedMessage)
        service.getNotificationService().clear(retractedMessage)
        if (getDatabase().deleteMessage(retractedMessage.getUuid())) {
            Log.d(Config.LOGTAG, "handled 1:1 retraction for $stanzaId from ${from.asBareJid()}")
            deleteAssociatedFile(retractedMessage)
        }
        service.updateConversationUi()
    }

    private fun deleteAssociatedFile(message: eu.siacs.conversations.entities.Message) {
        if (message.isFileOrImage()) {
            val storageLocation = message.getRelativeFilePath() ?: return
            val file = storageLocation.file()
            // since the retracted message has already been deleted this should come up
            // empty for files that are not used elsewhere
            val messagesWithFile = getDatabase().getMessagesWithFile(file)
            if (messagesWithFile.isEmpty() && file.exists()) {
                synchronized(service.FILENAMES_TO_IGNORE_DELETION) {
                    service.FILENAMES_TO_IGNORE_DELETION.add(file.absolutePath)
                }
                if (file.delete()) {
                    Log.d(Config.LOGTAG, "deleted associated file")
                }
            }
        }
    }
}
