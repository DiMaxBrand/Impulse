package eu.siacs.conversations.xmpp.manager

import android.util.Log
import com.google.common.base.Strings
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.NodeConfiguration
import im.conversations.android.xmpp.model.mds.Displayed
import im.conversations.android.xmpp.model.pubsub.Items
import im.conversations.android.xmpp.model.unique.StanzaId

class MessageDisplayedSynchronizationManager(
    private val service: XmppConnectionService,
    connection: XmppConnection
) : AbstractManager(service.applicationContext, connection) {

    fun handleItems(items: Items) {
        for (item in items.getItemMap(Displayed::class.java).entries) {
            this.processMdsItem(item)
        }
    }

    fun processMdsItem(item: Map.Entry<String, Displayed>) {
        if (!Config.MESSAGE_DISPLAYED_SYNCHRONIZATION) {
            Log.d(Config.LOGTAG, "processing MDS is disabled")
            return
        }
        val account = account
        val jid: Jid? = Jid.Invalid.getNullForInvalid(Jid.ofOrInvalid(item.key))
        if (jid == null) {
            return
        }
        val displayed = item.value
        val stanzaId = displayed.stanzaId
        val id: String? = stanzaId?.id
        val conversation: Conversation? = this.service.find(account, jid)
        if (id != null && conversation != null) {
            conversation.setDisplayState(id)
            this.service.markReadUpToStanzaId(conversation, id)
        }
    }

    fun fetch() {
        val future = getManager(PepManager::class.java).fetchItems(Displayed::class.java)
        Futures.addCallback(
            future,
            object : FutureCallback<Map<String, Displayed>> {
                override fun onSuccess(result: Map<String, Displayed>) {
                    for (entry in result.entries) {
                        processMdsItem(entry)
                    }
                }

                override fun onFailure(t: Throwable) {
                    Log.d(
                        Config.LOGTAG,
                        "${account.jid.asBareJid()}: could not retrieve MDS items",
                        t
                    )
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun hasFeature(): Boolean {
        val pepManager = getManager(PepManager::class.java)
        return pepManager.hasPublishOptions()
                && pepManager.hasConfigNodeMax()
                && Config.MESSAGE_DISPLAYED_SYNCHRONIZATION
    }

    fun hasServerAssist(): Boolean {
        return getManager(DiscoManager::class.java).hasAccountFeature(Namespace.MDS_SERVER_ASSIST)
    }

    fun displayed(message: Message) {
        val stanzaId = message.serverMsgId
        if (Strings.isNullOrEmpty(stanzaId)) {
            return
        }
        val conversation: Conversation
        val conversational = message.conversation
        if (conversational is Conversation) {
            conversation = conversational
        } else {
            return
        }
        val account = conversation.getAccount()
        val connection = account.xmppConnection
        if (!connection.getManager(MessageDisplayedSynchronizationManager::class.java).hasFeature()) {
            return
        }
        val itemId: Jid = if (message.isPrivateMessage) {
            message.counterpart
        } else {
            conversation.getAddress().asBareJid()
        }
        val future = this.publish(itemId, displayed(stanzaId!!, conversation))
        Futures.addCallback(
            future,
            object : FutureCallback<Void?> {
                override fun onSuccess(result: Void?) {
                    Log.d(Config.LOGTAG, "published mds for $itemId#$stanzaId")
                }

                override fun onFailure(t: Throwable) {
                    Log.d(Config.LOGTAG, "failed to publish MDS", t)
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun publish(itemId: Jid, displayed: Displayed): ListenableFuture<Void?> {
        return getManager(PepManager::class.java)
            .publish(displayed, itemId.toString(), NodeConfiguration.WHITELIST_MAX_ITEMS)
    }

    companion object {
        @JvmStatic
        fun displayed(id: String, conversation: Conversation): Displayed {
            val by: Jid = if (conversation.getMode() == Conversational.MODE_MULTI) {
                conversation.getAddress().asBareJid()
            } else {
                conversation.getAccount().jid.asBareJid()
            }
            val displayed = Displayed()
            val stanzaId = displayed.addExtension(StanzaId(id))
            stanzaId.setBy(by)
            return displayed
        }
    }
}
