package eu.siacs.conversations.xmpp.manager

import android.content.Context
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.offline.Offline
import im.conversations.android.xmpp.model.offline.Purge
import im.conversations.android.xmpp.model.stanza.Iq

class OfflineMessagesManager(context: Context, connection: XmppConnection) :
    AbstractManager(context, connection) {

    fun purge(): ListenableFuture<Void?> {
        val iq = Iq(Iq.Type.SET)
        iq.addExtension(Offline()).addExtension(Purge())
        return Futures.transform(connection.sendIqPacket(iq), { null }, MoreExecutors.directExecutor())
    }

    fun hasFeature(): Boolean =
        getManager(DiscoManager::class.java).hasServerFeature(Namespace.FLEXIBLE_OFFLINE_MESSAGE_RETRIEVAL)
}
