package eu.siacs.conversations.xmpp.manager

import android.util.Log
import com.google.common.collect.ImmutableMap
import com.google.common.util.concurrent.ListenableFuture
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.model.Bookmark
import im.conversations.android.xmpp.NodeConfiguration
import im.conversations.android.xmpp.model.bookmark.Conference
import im.conversations.android.xmpp.model.bookmark.Storage
import im.conversations.android.xmpp.model.pubsub.Items
import java.util.Collections

class LegacyBookmarkManager(service: XmppConnectionService, connection: XmppConnection) :
    AbstractBookmarkManager(service, connection) {

    fun handleItems(items: Items) {
        val account = this.account
        if (this.hasConversion()) {
            if (getManager(NativeBookmarkManager::class.java).hasFeature()) {
                Log.w(
                    Config.LOGTAG,
                    "${account.jid.asBareJid()}: received storage:bookmark notification even though we opted into bookmarks:1"
                )
            }
            val storage = items.getFirstItem(Storage::class.java)
            val bookmarks = storageToBookmarks(storage, account)
            this.setBookmarks(bookmarks, true)
            Log.d(Config.LOGTAG, "${account.jid.asBareJid()}: processing bookmark PEP event")
        } else {
            Log.d(
                Config.LOGTAG,
                "${account.jid.asBareJid()}: ignoring bookmark PEP event because bookmark conversion was not detected"
            )
        }
    }

    fun hasConversion(): Boolean =
        getManager(PepManager::class.java).hasPublishOptions()
            && getManager(DiscoManager::class.java).hasAccountFeature(Namespace.BOOKMARKS_CONVERSION)

    fun publish(bookmarks: Collection<Bookmark>): ListenableFuture<Void?> {
        val storage = asStorage(bookmarks)
        return getManager(PepManager::class.java).publishSingleton(storage, NodeConfiguration.WHITELIST)
    }

    companion object {
        @JvmStatic
        fun asStorage(bookmarks: Collection<Bookmark>): Storage {
            val storage = Storage()
            for (bookmark in bookmarks) {
                storage.addExtension(asConference(bookmark))
            }
            return storage
        }

        @JvmStatic
        private fun asConference(bookmark: Bookmark): Conference {
            val conference = Conference()
            conference.setJid(bookmark.getAddress())
            conference.setAutoJoin(bookmark.isAutoJoin)
            conference.setNick(bookmark.nick)
            conference.setConferenceName(bookmark.name)
            conference.setPassword(bookmark.password)
            return conference
        }

        @JvmStatic
        fun storageToBookmarks(storage: Storage?, account: Account): Map<Jid, Bookmark> {
            if (storage == null) {
                return Collections.emptyMap()
            }
            val builder = ImmutableMap.Builder<Jid, Bookmark>()
            for (conference in storage.getExtensions(Conference::class.java)) {
                val bookmark = conferenceToBookmark(conference, account) ?: continue
                builder.put(bookmark.getAddress(), bookmark)
            }
            return builder.buildKeepingLast()
        }

        @JvmStatic
        private fun conferenceToBookmark(conference: Conference, account: Account): Bookmark? {
            val address = Jid.Invalid.getNullForInvalid(conference.jid) ?: return null
            return try {
                BookmarkFactory.create(
                    account,
                    address,
                    conference.conferenceName,
                    conference.isAutoJoin,
                    conference.nick,
                    conference.password
                )
            } catch (e: Exception) {
                Log.d(Config.LOGTAG, "could not parse bookmark", e)
                null
            }
        }
    }
}
