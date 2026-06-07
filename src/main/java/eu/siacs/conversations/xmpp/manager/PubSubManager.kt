package eu.siacs.conversations.xmpp.manager

import android.content.Context
import android.util.Log
import com.google.common.util.concurrent.AsyncFunction
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.Config
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.ExtensionFactory
import im.conversations.android.xmpp.IqErrorException
import im.conversations.android.xmpp.NodeConfiguration
import im.conversations.android.xmpp.PreconditionNotMetException
import im.conversations.android.xmpp.PubSubErrorException
import im.conversations.android.xmpp.model.Extension
import im.conversations.android.xmpp.model.data.Data
import im.conversations.android.xmpp.model.pubsub.Items
import im.conversations.android.xmpp.model.pubsub.PubSub
import im.conversations.android.xmpp.model.pubsub.Publish
import im.conversations.android.xmpp.model.pubsub.PublishOptions
import im.conversations.android.xmpp.model.pubsub.Retract
import im.conversations.android.xmpp.model.pubsub.error.PubSubError
import im.conversations.android.xmpp.model.pubsub.event.Delete
import im.conversations.android.xmpp.model.pubsub.event.Event
import im.conversations.android.xmpp.model.pubsub.event.Purge
import im.conversations.android.xmpp.model.pubsub.owner.Configure
import im.conversations.android.xmpp.model.pubsub.owner.PubSubOwner
import im.conversations.android.xmpp.model.stanza.Iq
import im.conversations.android.xmpp.model.stanza.Message

class PubSubManager(context: Context, connection: XmppConnection) :
    AbstractManager(context, connection) {

    companion object {
        private const val SINGLETON_ITEM_ID = "current"
    }

    fun handleEvent(message: Message) {
        val event = message.getExtension(Event::class.java)
        val action = event.action
        val from = message.from

        if (from is Jid.Invalid) {
            Log.d(
                Config.LOGTAG,
                "${account.jid.asBareJid()}: ignoring event from invalid jid"
            )
            return
        }

        when (action) {
            is Purge -> handlePurge(message, action)
            is Items -> handleItems(message, action)
            is Delete -> handleDelete(message, action)
        }
    }

    fun <T : Extension> fetchItems(
        address: Jid,
        clazz: Class<T>
    ): ListenableFuture<Map<String, T>> {
        val id = ExtensionFactory.id(clazz)
            ?: return Futures.immediateFailedFuture(
                IllegalArgumentException(
                    String.format("%s is not a registered extension", clazz.name)
                )
            )
        return fetchItems(address, id.namespace(), clazz)
    }

    fun <T : Extension> fetchItems(
        address: Jid,
        node: String,
        clazz: Class<T>
    ): ListenableFuture<Map<String, T>> {
        val request = Iq(Iq.Type.GET)
        request.setTo(address)
        val pubSub = request.addExtension(PubSub())
        val itemsWrapper = pubSub.addExtension(PubSub.ItemsWrapper())
        itemsWrapper.setNode(node)
        return Futures.transform(
            connection.sendIqPacket(request),
            { response: Iq ->
                val pubSubResponse = response.getExtension(PubSub::class.java)
                    ?: throw IllegalStateException()
                val items = pubSubResponse.items ?: throw IllegalStateException()
                items.getItemMap(clazz)
            },
            MoreExecutors.directExecutor()
        )
    }

    fun <T : Extension> fetchItem(
        address: Jid,
        itemId: String,
        clazz: Class<T>
    ): ListenableFuture<T> {
        val id = ExtensionFactory.id(clazz)
            ?: return Futures.immediateFailedFuture(
                IllegalArgumentException(
                    String.format("%s is not a registered extension", clazz.name)
                )
            )
        return fetchItem(address, id.namespace(), itemId, clazz)
    }

    fun <T : Extension> fetchItem(
        address: Jid,
        node: String,
        itemId: String,
        clazz: Class<T>
    ): ListenableFuture<T> {
        val request = Iq(Iq.Type.GET)
        request.setTo(address)
        val pubSub = request.addExtension(PubSub())
        val itemsWrapper = pubSub.addExtension(PubSub.ItemsWrapper())
        itemsWrapper.setNode(node)
        val item = itemsWrapper.addExtension(PubSub.Item())
        item.setId(itemId)
        return Futures.transform(
            connection.sendIqPacket(request),
            { response: Iq ->
                val pubSubResponse = response.getExtension(PubSub::class.java)
                    ?: throw IllegalStateException()
                val items = pubSubResponse.items ?: throw IllegalStateException()
                items.getItemOrThrow(itemId, clazz)
            },
            MoreExecutors.directExecutor()
        )
    }

    fun <T : Extension> fetchMostRecentItem(
        address: Jid,
        clazz: Class<T>
    ): ListenableFuture<T> {
        val id = ExtensionFactory.id(clazz)
            ?: return Futures.immediateFailedFuture(
                IllegalArgumentException(
                    String.format("%s is not a registered extension", clazz.name)
                )
            )
        return fetchMostRecentItem(address, id.namespace(), clazz)
    }

    fun <T : Extension> fetchMostRecentItem(
        address: Jid,
        node: String,
        clazz: Class<T>
    ): ListenableFuture<T> {
        val request = Iq(Iq.Type.GET)
        request.setTo(address)
        val pubSub = request.addExtension(PubSub())
        val itemsWrapper = pubSub.addExtension(PubSub.ItemsWrapper())
        itemsWrapper.setNode(node)
        itemsWrapper.setMaxItems(1)
        return Futures.transform(
            connection.sendIqPacket(request),
            { response: Iq ->
                val pubSubResponse = response.getExtension(PubSub::class.java)
                    ?: throw IllegalStateException()
                val items = pubSubResponse.items ?: throw IllegalStateException()
                items.getOnlyItem(clazz)
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun handleItems(message: Message, items: Items) {
        val from = message.from
        val isFromBare = from == null || from.isBareJid
        val node = items.node
        if (connection.fromAccount(message) && Namespace.BOOKMARKS2 == node) {
            getManager(NativeBookmarkManager::class.java).handleItems(items)
            return
        }
        if (connection.fromAccount(message) && Namespace.BOOKMARKS == node) {
            getManager(LegacyBookmarkManager::class.java).handleItems(items)
            return
        }
        if (connection.fromAccount(message) && Namespace.MDS_DISPLAYED == node) {
            getManager(MessageDisplayedSynchronizationManager::class.java).handleItems(items)
            return
        }
        if (isFromBare && Namespace.AVATAR_METADATA == node) {
            getManager(AvatarManager::class.java).handleItems(from, items)
            return
        }
        if (isFromBare && Namespace.NICK == node) {
            getManager(NickManager::class.java).handleItems(from, items)
            return
        }
        if (isFromBare && Namespace.AXOLOTL_DEVICE_LIST == node) {
            getManager(AxolotlManager::class.java).handleItems(from, items)
        }
    }

    private fun handlePurge(message: Message, purge: Purge) {
        val from = message.from
        val isFromBare = from == null || from.isBareJid
        val node = purge.node
        if (connection.fromAccount(message) && Namespace.BOOKMARKS2 == node) {
            getManager(NativeBookmarkManager::class.java).handlePurge()
        }
        if (isFromBare && Namespace.AVATAR_METADATA == node) {
            // purge (delete all items in a node) is functionally equivalent to delete
            getManager(AvatarManager::class.java).handleDelete(from)
        }
    }

    private fun handleDelete(message: Message, delete: Delete) {
        val from = message.from
        val isFromBare = from == null || from.isBareJid
        val node = delete.node
        if (connection.fromAccount(message) && Namespace.BOOKMARKS2 == node) {
            getManager(NativeBookmarkManager::class.java).handleDelete()
            return
        }
        if (isFromBare && Namespace.AVATAR_METADATA == node) {
            getManager(AvatarManager::class.java).handleDelete(from)
            return
        }
        if (isFromBare && Namespace.NICK == node) {
            getManager(NickManager::class.java).handleDelete(from)
        }
    }

    fun publishSingleton(
        address: Jid,
        item: Extension,
        nodeConfiguration: NodeConfiguration
    ): ListenableFuture<Void?> {
        val id = ExtensionFactory.id(item.javaClass)
        return publish(address, item, SINGLETON_ITEM_ID, id.namespace(), nodeConfiguration)
    }

    fun publishSingleton(
        address: Jid,
        item: Extension,
        node: String,
        nodeConfiguration: NodeConfiguration
    ): ListenableFuture<Void?> =
        publish(address, item, SINGLETON_ITEM_ID, node, nodeConfiguration)

    fun publish(
        address: Jid,
        item: Extension,
        itemId: String,
        nodeConfiguration: NodeConfiguration
    ): ListenableFuture<Void?> {
        val id = ExtensionFactory.id(item.javaClass)
        return publish(address, item, itemId, id.namespace(), nodeConfiguration)
    }

    fun publish(
        address: Jid,
        itemPayload: Extension,
        itemId: String,
        node: String,
        nodeConfiguration: NodeConfiguration
    ): ListenableFuture<Void?> {
        val future = publishNoRetry(address, itemPayload, itemId, node, nodeConfiguration)
        return Futures.catchingAsync(
            future,
            PreconditionNotMetException::class.java,
            { _ ->
                Log.d(Config.LOGTAG, "Node $node on $address requires reconfiguration")
                val reconfigurationFuture = reconfigureNode(address, node, nodeConfiguration)
                Futures.transformAsync(
                    reconfigurationFuture,
                    { _ -> publishNoRetry(address, itemPayload, itemId, node, nodeConfiguration) },
                    MoreExecutors.directExecutor()
                )
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun publishNoRetry(
        address: Jid,
        itemPayload: Extension,
        itemId: String,
        node: String,
        nodeConfiguration: NodeConfiguration
    ): ListenableFuture<Void?> {
        val iq = Iq(Iq.Type.SET)
        iq.setTo(address)
        val pubSub = iq.addExtension(PubSub())
        val publish = pubSub.addExtension(Publish())
        publish.setNode(node)
        val item = publish.addExtension(PubSub.Item())
        item.setId(itemId)
        item.addExtension(itemPayload)
        pubSub.addExtension(PublishOptions.of(nodeConfiguration))
        val iqFuture: ListenableFuture<Void?> = Futures.transform(
            connection.sendIqPacket(iq),
            { _: Iq? -> null },
            MoreExecutors.directExecutor()
        )
        return Futures.catchingAsync(
            iqFuture,
            IqErrorException::class.java,
            PubSubExceptionTransformer<Void?>(),
            MoreExecutors.directExecutor()
        )
    }

    private fun reconfigureNode(
        address: Jid,
        node: String,
        nodeConfiguration: NodeConfiguration
    ): ListenableFuture<Void?> =
        Futures.transformAsync(
            getNodeConfiguration(address, node),
            { data: Data? -> setNodeConfiguration(address, node, data!!.submit(nodeConfiguration)) },
            MoreExecutors.directExecutor()
        )

    fun getNodeConfiguration(address: Jid, node: String): ListenableFuture<Data> {
        val iq = Iq(Iq.Type.GET)
        iq.setTo(address)
        val pubSub = iq.addExtension(PubSubOwner())
        val configure = pubSub.addExtension(Configure())
        configure.setNode(node)
        return Futures.transform(
            connection.sendIqPacket(iq),
            { result: Iq ->
                val pubSubOwnerResult = result.getExtension(PubSubOwner::class.java)
                val configureResult = pubSubOwnerResult?.getExtension(Configure::class.java)
                    ?: throw IllegalStateException(
                        "No configuration found in configuration request result"
                    )
                configureResult.data
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun setNodeConfiguration(
        address: Jid,
        node: String,
        data: Data
    ): ListenableFuture<Void?> {
        val iq = Iq(Iq.Type.SET)
        iq.setTo(address)
        val pubSub = iq.addExtension(PubSubOwner())
        val configure = pubSub.addExtension(Configure())
        configure.setNode(node)
        configure.addExtension(data)
        return Futures.transform(
            connection.sendIqPacket(iq),
            { _: Iq? ->
                Log.d(Config.LOGTAG, "Modified node configuration $node on $address")
                null
            },
            MoreExecutors.directExecutor()
        )
    }

    fun retract(address: Jid, itemId: String, node: String): ListenableFuture<Iq> {
        val iq = Iq(Iq.Type.SET)
        iq.setTo(address)
        val pubSub = iq.addExtension(PubSub())
        val retract = pubSub.addExtension(Retract())
        retract.setNode(node)
        retract.setNotify(true)
        val item = retract.addExtension(PubSub.Item())
        item.setId(itemId)
        return connection.sendIqPacket(iq)
    }

    fun delete(address: Jid, node: String): ListenableFuture<Iq> {
        val iq = Iq(Iq.Type.SET)
        iq.setTo(address)
        val pubSub = iq.addExtension(PubSubOwner())
        val delete = pubSub.addExtension(im.conversations.android.xmpp.model.pubsub.owner.Delete())
        delete.setNode(node)
        return connection.sendIqPacket(iq)
    }

    private class PubSubExceptionTransformer<V> : AsyncFunction<IqErrorException, V> {
        override fun apply(ex: IqErrorException): ListenableFuture<V> {
            val error = ex.error
                ?: return Futures.immediateFailedFuture(ex)
            val pubSubError = error.getExtension(PubSubError::class.java)
            return when {
                pubSubError is PubSubError.PreconditionNotMet ->
                    Futures.immediateFailedFuture(PreconditionNotMetException(ex.response))
                pubSubError != null ->
                    Futures.immediateFailedFuture(PubSubErrorException(ex.response))
                else ->
                    Futures.immediateFailedFuture(ex)
            }
        }
    }
}
