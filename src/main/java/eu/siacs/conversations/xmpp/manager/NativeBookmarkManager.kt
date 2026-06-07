package eu.siacs.conversations.xmpp.manager

import android.util.Log
import com.google.common.collect.ImmutableMap
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.model.Bookmark
import im.conversations.android.xmpp.NodeConfiguration
import im.conversations.android.xmpp.model.bookmark2.Conference
import im.conversations.android.xmpp.model.pubsub.Items
import im.conversations.android.xmpp.model.pubsub.event.Retract
import java.util.Collections

class NativeBookmarkManager(service: XmppConnectionService, connection: XmppConnection) :
    AbstractBookmarkManager(service, connection) {

    fun fetch() {
        val future = getManager(PepManager::class.java).fetchItems(Conference::class.java)
        Futures.addCallback(
            future,
            object : FutureCallback<Map<String, Conference>> {
                override fun onSuccess(bookmarks: Map<String, Conference>) {
                    Log.d(
                        Config.LOGTAG,
                        "NativeBookmarkManager.onSuccess(${bookmarks.size}) bookmarks"
                    )
                    val builder = ImmutableMap.Builder<Jid, Bookmark>()
                    for (entry in bookmarks.entries) {
                        val bookmark = itemToBookmark(entry.key, entry.value, account)
                            ?: continue
                        builder.put(bookmark.getAddress(), bookmark)
                    }
                    setBookmarks(builder.buildKeepingLast(), true)
                }

                override fun onFailure(throwable: Throwable) {
                    Log.d(Config.LOGTAG, "Could not fetch bookmarks", throwable)
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun handleItems(items: Items) {
        this.handleItemMap(items.getItemMap(Conference::class.java))
        this.handleRetractions(items.retractions)
    }

    private fun handleRetractions(retractions: Collection<Retract>) {
        val account = account
        for (retract in retractions) {
            val id = Jid.Invalid.getNullForInvalid(retract.getAttributeAsJid("id"))
            if (id == null) {
                return
            }
            getManager(BookmarkManager::class.java).removeBookmark(id)
            Log.d(Config.LOGTAG, "${account.jid.asBareJid()}: deleted bookmark for $id")
            processDeletedBookmark(id)
            service.updateConversationUi()
        }
    }

    private fun handleItemMap(items: Map<String, Conference>) {
        val account = account
        for (item in items.entries) {
            val bookmark = itemToBookmark(item.key, item.value, account) ?: continue
            val manager = getManager(BookmarkManager::class.java)
            manager.putBookmark(bookmark)
            manager.processModifiedBookmark(bookmark, true)
            service.updateConversationUi()
        }
    }

    fun publish(bookmark: Bookmark): ListenableFuture<Void?> {
        val address = bookmark.getAddress()
        val itemId = address.toString()
        val conference = bookmarkToItem(bookmark)
        return Futures.transform(
            getManager(PepManager::class.java)
                .publish(conference, itemId, NodeConfiguration.WHITELIST_MAX_ITEMS),
            { _: Void? -> null },
            MoreExecutors.directExecutor()
        )
    }

    fun retract(address: Jid): ListenableFuture<Void?> {
        val itemId = address.toString()
        return Futures.transform(
            getManager(PepManager::class.java).retract(itemId, Namespace.BOOKMARKS2),
            { _: Any? -> null },
            MoreExecutors.directExecutor()
        )
    }

    private fun deleteAllItems() {
        val manager = getManager(BookmarkManager::class.java)
        val previous = manager.bookmarkAddresses
        manager.setBookmarks(Collections.emptyMap())
        processDeletedBookmarks(previous)
    }

    fun handleDelete() {
        Log.d(Config.LOGTAG, "${account.jid.asBareJid()}: deleted bookmarks node")
        this.deleteAllItems()
    }

    fun handlePurge() {
        Log.d(Config.LOGTAG, "${account.jid.asBareJid()}: purged bookmarks")
        this.deleteAllItems()
    }

    fun hasFeature(): Boolean {
        val pep = getManager(PepManager::class.java)
        val disco = getManager(DiscoManager::class.java)
        return pep.hasPublishOptions()
            && pep.hasConfigNodeMax()
            && disco.hasAccountFeature(Namespace.BOOKMARKS2_COMPAT)
    }

    companion object {
        @JvmStatic
        private fun itemToBookmark(id: String?, conference: Conference?, account: Account): Bookmark? {
            if (id == null || conference == null) {
                return null
            }
            val jid = Jid.Invalid.getNullForInvalid(Jid.ofOrInvalid(id))
            if (jid == null || jid.isFullJid) {
                return null
            }
            return try {
                BookmarkFactory.createWithExtensions(
                    account,
                    jid,
                    conference.conferenceName,
                    conference.isAutoJoin,
                    conference.nick,
                    conference.password,
                    conference.extensions
                )
            } catch (e: Exception) {
                Log.d(Config.LOGTAG, "could not parse bookmark", e)
                null
            }
        }

        @JvmStatic
        private fun bookmarkToItem(bookmark: Bookmark): Conference {
            val conference = Conference()
            conference.setAutoJoin(bookmark.isAutoJoin)
            conference.setNick(bookmark.nick)
            conference.setConferenceName(bookmark.name)
            conference.setPassword(bookmark.password)
            conference.setExtensions(bookmark.extensions)
            return conference
        }
    }
}
