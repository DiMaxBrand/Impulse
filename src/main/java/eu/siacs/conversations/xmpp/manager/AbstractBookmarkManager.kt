package eu.siacs.conversations.xmpp.manager

import android.util.Log
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.model.Bookmark

abstract class AbstractBookmarkManager(
    protected val service: XmppConnectionService,
    connection: XmppConnection
) : AbstractManager(service, connection) {

    protected fun setBookmarks(bookmarks: Map<Jid, Bookmark>, pep: Boolean) {
        val manager = getManager(BookmarkManager::class.java)
        // leaving MUCs on bookmark deletion doesn't work on clean start because 'previous
        // bookmarks' will be empty and we can't get the diff for which bookmarks have been deleted
        // vs never existed. this could be circumvented by persisting bookmarks across restarts
        val previousBookmarks = manager.getBookmarkAddresses()
        for (bookmark in bookmarks.values) {
            previousBookmarks.remove(bookmark.getAddress().asBareJid())
            manager.processModifiedBookmark(bookmark, pep)
        }
        if (pep) {
            this.processDeletedBookmarks(previousBookmarks)
        }
        manager.setBookmarks(bookmarks)
    }

    protected fun processDeletedBookmarks(bookmarks: Collection<Jid>) {
        Log.d(
            Config.LOGTAG,
            getAccount().getJid().asBareJid().toString() +
                    ": " +
                    bookmarks.size +
                    " bookmarks have been removed"
        )
        for (bookmark in bookmarks) {
            processDeletedBookmark(bookmark)
        }
    }

    protected fun processDeletedBookmark(jid: Jid) {
        val conversation: Conversation? = service.find(getAccount(), jid)
        if (conversation == null) {
            return
        }
        Log.d(
            Config.LOGTAG,
            getAccount().getJid().asBareJid().toString() + ": archiving MUC " + jid + " after PEP update"
        )
        this.service.archiveConversation(conversation, false)
    }
}
