package eu.siacs.conversations.xmpp.manager

import android.content.Context
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.NodeConfiguration
import im.conversations.android.xmpp.model.Extension
import im.conversations.android.xmpp.model.data.Data
import im.conversations.android.xmpp.model.stanza.Iq

class PepManager(context: Context, connection: XmppConnection) :
    AbstractManager(context, connection) {

    fun <T : Extension> fetchItems(clazz: Class<T>): ListenableFuture<Map<String, T>> =
        pubSubManager().fetchItems(pepService(), clazz)

    fun <T : Extension> fetchMostRecentItem(clazz: Class<T>): ListenableFuture<T> =
        pubSubManager().fetchMostRecentItem(pepService(), clazz)

    fun <T : Extension> fetchMostRecentItem(node: String, clazz: Class<T>): ListenableFuture<T> =
        pubSubManager().fetchMostRecentItem(pepService(), node, clazz)

    fun publish(
        item: Extension,
        itemId: String,
        nodeConfiguration: NodeConfiguration
    ): ListenableFuture<Void?> =
        pubSubManager().publish(pepService(), item, itemId, nodeConfiguration)

    fun publishSingleton(
        item: Extension,
        node: String,
        nodeConfiguration: NodeConfiguration
    ): ListenableFuture<Void?> =
        pubSubManager().publishSingleton(pepService(), item, node, nodeConfiguration)

    fun publishSingleton(
        item: Extension,
        nodeConfiguration: NodeConfiguration
    ): ListenableFuture<Void?> =
        pubSubManager().publishSingleton(pepService(), item, nodeConfiguration)

    fun retract(itemId: String, node: String): ListenableFuture<Iq> =
        pubSubManager().retract(pepService(), itemId, node)

    fun delete(node: String): ListenableFuture<Void?> {
        val future = pubSubManager().delete(pepService(), node)
        return Futures.transform(future, { _: Iq? -> null }, MoreExecutors.directExecutor())
    }

    fun getNodeConfiguration(node: String): ListenableFuture<Data> =
        pubSubManager().getNodeConfiguration(pepService(), node)

    fun isAvailable(): Boolean {
        val infoQuery = getManager(DiscoManager::class.java).get(pepService()) ?: return false
        return infoQuery.hasIdentityWithCategoryAndType("pubsub", "pep")
            && infoQuery.hasFeature(Namespace.PUB_SUB_PERSISTENT_ITEMS)
    }

    fun hasPublishOptions(): Boolean =
        getManager(DiscoManager::class.java).hasAccountFeature(Namespace.PUB_SUB_PUBLISH_OPTIONS)

    fun hasConfigNodeMax(): Boolean =
        getManager(DiscoManager::class.java).hasAccountFeature(Namespace.PUB_SUB_CONFIG_NODE_MAX)

    private fun pubSubManager(): PubSubManager = getManager(PubSubManager::class.java)

    private fun pepService(): Jid = this.account.jid.asBareJid()
}
