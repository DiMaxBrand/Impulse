package eu.siacs.conversations.xmpp.manager

import android.util.Log
import com.google.common.collect.ClassToInstanceMap
import com.google.common.collect.ImmutableClassToInstanceMap
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.hints.NoStore
import im.conversations.android.xmpp.model.stanza.Message
import im.conversations.android.xmpp.model.state.Active
import im.conversations.android.xmpp.model.state.ChatStateNotification
import im.conversations.android.xmpp.model.state.Composing
import im.conversations.android.xmpp.model.state.Gone
import im.conversations.android.xmpp.model.state.Inactive
import im.conversations.android.xmpp.model.state.Paused
import java.util.HashMap
import java.util.Objects

class ChatStateManager(private val service: XmppConnectionService, connection: XmppConnection) :
    AbstractManager(service.applicationContext, connection) {

    private val appSettings = AppSettings(service.applicationContext)
    private val incoming = HashMap<Jid, Class<out ChatStateNotification>>()
    private val outgoing = HashMap<Jid, Class<out ChatStateNotification>>()

    fun process(message: Message) {
        val from = message.getFrom() ?: return
        val chatStateNotification = message.getExtension(ChatStateNotification::class.java) ?: return
        val chatState = chatStateNotification.javaClass
        if (getManager(MultiUserChatManager::class.java).isMuc(message)) {
            val mucOptions =
                getManager(MultiUserChatManager::class.java).getState(from.asBareJid()) ?: return
            val user = mucOptions.getUser(from) ?: return
            user.setChatState(chatState)
        } else {
            val account = getAccount().getJid().asBareJid()
            if (from.asBareJid() == account) {
                val to = message.getTo() ?: return
                Log.d(Config.LOGTAG, "put outgoing ${to.asBareJid()}=$chatState")
                this.outgoing[to.asBareJid()] = chatState
                val conversation = this.service.find(getAccount(), to)
                val activity = listOf(Active::class.java, Composing::class.java).contains(chatState)
                if (activity) {
                    getManager(ActivityManager::class.java)
                        .record(from, ActivityManager.ActivityType.CHAT_STATE)
                }
                if (conversation == null || conversation.getContact().isSelf()) return
                if (activity) {
                    this.service.markRead(conversation)
                }
            } else {
                Log.d(Config.LOGTAG, "put incoming ${from.asBareJid()}=$chatState")
                val previous = this.incoming.put(from.asBareJid(), chatState)
                if (previous != chatState) {
                    this.service.updateConversationUi()
                }
            }
        }
    }

    fun resetChatStates() {
        synchronized(this.incoming) {
            this.incoming.clear()
        }
    }

    fun sendChatState(
        conversation: Conversation,
        chatState: Class<out ChatStateNotification>
    ) {
        if (this.setOutgoingChatState(conversation, chatState)) {
            this.sendChatState(conversation)
        }
    }

    private fun sendChatState(conversation: Conversation) {
        if (!appSettings.isSendChatStates()) return

        val address = conversation.getAddress().asBareJid()
        val extension = getOutgoingChatStateExtension(address)

        val packet = Message()
        packet.setType(
            if (conversation.getMode() == Conversational.MODE_MULTI) Message.Type.GROUPCHAT
            else Message.Type.CHAT
        )
        packet.setTo(address)
        packet.addExtension(extension)
        packet.addExtension(NoStore())
        this.connection.sendMessagePacket(packet)
    }

    fun getOutgoingChatStateExtension(conversation: Conversation): ChatStateNotification {
        return getOutgoingChatStateExtension(conversation.getAddress().asBareJid())
    }

    private fun getOutgoingChatStateExtension(address: Jid): ChatStateNotification {
        val chatState: Class<out ChatStateNotification>?
        synchronized(this.outgoing) {
            chatState = this.outgoing[address]
        }
        val normalized = chatState ?: Config.DEFAULT_CHAT_STATE
        val extension = CHAT_STATE_NOTIFICATIONS[normalized]
            ?: throw AssertionError("Missing Instance of ${normalized.simpleName}")
        return extension
    }

    fun setOutgoingChatState(
        conversation: Conversation,
        chatState: Class<out ChatStateNotification>
    ): Boolean {
        if (conversation.getMode() == Conversational.MODE_SINGLE
            && !conversation.getContact().isSelf()
            || (conversation.isPrivateAndNonAnonymous() && conversation.getNextCounterpart() == null)
        ) {
            synchronized(this.outgoing) {
                val previous = this.outgoing.put(conversation.getAddress().asBareJid(), chatState)
                val normalized = previous ?: Active::class.java
                return normalized != chatState
            }
        }
        return false
    }

    fun getIncoming(address: Jid): Class<out ChatStateNotification>? {
        synchronized(this.incoming) {
            return this.incoming[address.asBareJid()]
        }
    }

    companion object {
        private val CHAT_STATE_NOTIFICATIONS: ClassToInstanceMap<ChatStateNotification> =
            ImmutableClassToInstanceMap.Builder<ChatStateNotification>()
                .put(Active::class.java, Active())
                .put(Composing::class.java, Composing())
                .put(Gone::class.java, Gone())
                .put(Inactive::class.java, Inactive())
                .put(Paused::class.java, Paused())
                .build()

        @JvmStatic
        fun send(conversation: Conversation, state: Class<out ChatStateNotification>) {
            val account = conversation.getAccount()
            val manager = account.getXmppConnection().getManager(ChatStateManager::class.java)
            manager.sendChatState(conversation, state)
        }
    }
}
