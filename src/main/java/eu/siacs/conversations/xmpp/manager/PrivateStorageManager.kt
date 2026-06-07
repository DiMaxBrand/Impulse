package eu.siacs.conversations.xmpp.manager

import android.util.Log
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.Config
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.model.Bookmark
import im.conversations.android.xmpp.model.bookmark.Storage
import im.conversations.android.xmpp.model.stanza.Iq
import im.conversations.android.xmpp.model.storage.PrivateStorage

class PrivateStorageManager(service: XmppConnectionService, connection: XmppConnection) :
    AbstractBookmarkManager(service, connection) {

    fun fetchBookmarks() {
        val iq = Iq(Iq.Type.GET)
        val privateStorage = iq.addExtension(PrivateStorage())
        privateStorage.addExtension(Storage())
        val future = this.connection.sendIqPacket(iq)
        Futures.addCallback(
            future,
            object : FutureCallback<Iq> {
                override fun onSuccess(result: Iq) {
                    val ps = result.getExtension(PrivateStorage::class.java) ?: return
                    val bookmarkStorage = ps.getExtension(Storage::class.java)
                    val bookmarks = LegacyBookmarkManager.storageToBookmarks(bookmarkStorage, account)
                    setBookmarks(bookmarks, false)
                }

                override fun onFailure(t: Throwable) {
                    Log.d(
                        Config.LOGTAG,
                        "${account.jid.asBareJid()}: could not fetch bookmark from private storage",
                        t
                    )
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun publishBookmarks(bookmarks: Collection<Bookmark>): ListenableFuture<Void?> {
        val iq = Iq(Iq.Type.SET)
        val privateStorage = iq.addExtension(PrivateStorage())
        privateStorage.addExtension(LegacyBookmarkManager.asStorage(bookmarks))
        return Futures.transform(
            connection.sendIqPacket(iq),
            { _: Iq? -> null },
            MoreExecutors.directExecutor()
        )
    }
}
