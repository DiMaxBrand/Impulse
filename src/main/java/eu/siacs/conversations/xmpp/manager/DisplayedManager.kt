package eu.siacs.conversations.xmpp.manager

import android.util.Log
import com.google.common.collect.Collections2
import com.google.common.collect.Iterables
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.entities.ReadByMarker
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.hints.Store
import im.conversations.android.xmpp.model.markers.Displayed

class DisplayedManager(
    private val service: XmppConnectionService,
    connection: XmppConnection
) : AbstractManager(service.applicationContext, connection) {

    private val appSettings = AppSettings(service.applicationContext)

    fun processDisplayed(
        packet: im.conversations.android.xmpp.model.stanza.Message,
        selfAddressed: Boolean,
        counterpart: Jid,
        query: MessageArchiveManager.Query?
    ) {
        val account = getAccount()
        val isTypeGroupChat =
            packet.getType() == im.conversations.android.xmpp.model.stanza.Message.Type.GROUPCHAT
        val from = packet.getFrom()
        val displayed = packet.getExtension(Displayed::class.java)
        val id = displayed.getId()
        if (packet.fromAccount(account) && !selfAddressed) {
            val c: Conversation? = this.service.find(account, counterpart.asBareJid())
            val message: Message? =
                if (c == null || id == null) null else c.findReceivedWithRemoteId(id)
            if (message != null && (query == null || query.isCatchup())) {
                this.service.markReadUpTo(c, message)
            }
            if (query == null) {
                getManager(ActivityManager::class.java)
                    .record(from, ActivityManager.ActivityType.DISPLAYED)
            }
        } else if (isTypeGroupChat) {
            val conversation: Conversation? = this.service.find(account, counterpart.asBareJid())
            val message: Message? = if (conversation != null && id != null) {
                conversation.findMessageWithServerMsgId(id)
            } else {
                null
            }
            if (message != null) {
                val user = getManager(MultiUserChatManager::class.java).getMucUser(packet, query)
                if (user != null && user.getMucOptions().isOurAccount(user)) {
                    if (!message.isRead
                        && (query == null || query.isCatchup())
                    ) { // checking if message is unread fixes race conditions with reflections
                        this.service.markReadUpTo(conversation, message)
                    }
                } else if (!counterpart.isBareJid() && user != null && user.getRealJid() != null) {
                    val readByMarker = ReadByMarker.from(user)
                    if (message.addReadByMarker(readByMarker)) {
                        val mucOptions =
                            getManager(MultiUserChatManager::class.java)
                                .getOrCreateState(conversation)
                        val everyone = mucOptions.getMembers()
                        val readyBy = message.getReadyByTrue()
                        val mStatus = message.getStatus()
                        if (mucOptions.isPrivateAndNonAnonymous()
                            && (mStatus == Message.STATUS_SEND_RECEIVED
                                    || mStatus == Message.STATUS_SEND)
                            && readyBy.containsAll(everyone)
                        ) {
                            message.setStatus(Message.STATUS_SEND_DISPLAYED)
                        }
                        this.getDatabase().updateMessage(message, false)
                        this.service.updateConversationUi()
                    }
                }
            }
        } else {
            val displayedMessage: Message? =
                this.service.markMessage(account, from.asBareJid(), id, Message.STATUS_SEND_DISPLAYED)
            var message: Message? = if (displayedMessage == null) null else displayedMessage.prev()
            while (message != null
                && message.getStatus() == Message.STATUS_SEND_RECEIVED
                && message.getTimeSent() < displayedMessage!!.getTimeSent()
            ) {
                this.service.markMessage(message, Message.STATUS_SEND_DISPLAYED)
                message = message.prev()
            }
            if (displayedMessage != null && selfAddressed) {
                dismissNotification(counterpart, query, id)
            }
        }
    }

    private fun dismissNotification(
        counterpart: Jid,
        query: MessageArchiveManager.Query?,
        id: String?
    ) {
        val account = getAccount()
        val conversation = this.service.find(account, counterpart.asBareJid())
        if (conversation != null && (query == null || query.isCatchup())) {
            val displayableId = conversation.findMostRecentRemoteDisplayableId()
            if (displayableId != null && displayableId == id) {
                this.service.markRead(conversation)
            } else {
                Log.w(
                    Config.LOGTAG,
                    "${account.getJid().asBareJid()}: received dismissing display marker that did not match our last id in that conversation"
                )
            }
        }
    }

    fun displayed(readMessages: List<Message>) {
        val last = Iterables.getLast(
            Collections2.filter(readMessages) { m ->
                !m!!.isPrivateMessage() && m.getStatus() == Message.STATUS_RECEIVED
            },
            null
        ) ?: return

        val conversation: Conversation = if (last.getConversation() is Conversation) {
            last.getConversation() as Conversation
        } else {
            return
        }

        val isPrivateAndNonAnonymousMuc =
            conversation.getMode() == Conversational.MODE_MULTI
                    && conversation.isPrivateAndNonAnonymous()

        val sendDisplayedMarker =
            appSettings.isReadReceipts()
                    && (last.trusted() || isPrivateAndNonAnonymousMuc)
                    && ((last.getConversation().getMode() == Conversational.MODE_SINGLE
                    && last.getRemoteMsgId() != null)
                    || (last.getConversation().getMode() == Conversational.MODE_MULTI
                    && last.getServerMsgId() != null))
                    && (last.markable || isPrivateAndNonAnonymousMuc)

        val stanzaId = last.getServerMsgId()

        val serverAssist =
            stanzaId != null
                    && connection
                .getManager(MessageDisplayedSynchronizationManager::class.java)
                .hasServerAssist()

        if (sendDisplayedMarker && serverAssist) {
            val displayedMessage = displayedMessage(last)
            displayedMessage.addExtension(
                MessageDisplayedSynchronizationManager.displayed(stanzaId, conversation)
            )
            displayedMessage.setTo(displayedMessage.getTo().asBareJid())
            Log.d(
                Config.LOGTAG,
                "${getAccount().getJid().asBareJid()}: server assisted $displayedMessage"
            )
            this.connection.sendMessagePacket(displayedMessage)
        } else {
            getManager(MessageDisplayedSynchronizationManager::class.java).displayed(last)
            // read markers will be sent after MDS to flush the CSI stanza queue
            if (sendDisplayedMarker) {
                val displayedMessage = displayedMessage(last)
                Log.d(
                    Config.LOGTAG,
                    "${getAccount().getJid().asBareJid()}: sending displayed marker to ${displayedMessage.getTo()}"
                )
                this.connection.sendMessagePacket(displayedMessage)
            }
        }
    }

    companion object {
        private fun displayedMessage(message: Message): im.conversations.android.xmpp.model.stanza.Message {
            val groupChat = message.getConversation().getMode() == Conversational.MODE_MULTI
            val to: Jid = message.getCounterpart()
            val packet = im.conversations.android.xmpp.model.stanza.Message()
            packet.setType(
                if (groupChat) im.conversations.android.xmpp.model.stanza.Message.Type.GROUPCHAT
                else im.conversations.android.xmpp.model.stanza.Message.Type.CHAT
            )
            packet.setTo(if (groupChat) to.asBareJid() else to)
            val displayed = packet.addExtension(Displayed())
            if (groupChat) {
                displayed.setId(message.getServerMsgId())
            } else {
                displayed.setId(message.getRemoteMsgId())
            }
            packet.addExtension(Store())
            return packet
        }
    }
}
