package eu.siacs.conversations.xmpp.manager

import android.content.Context
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.ping.Ping
import im.conversations.android.xmpp.model.stanza.Iq
import java.util.concurrent.TimeoutException

class PingManager(context: Context, connection: XmppConnection) :
    AbstractManager(context, connection) {

    fun ping() {
        if (connection.getFeatures().sm()) {
            this.connection.sendRequestStanza()
        } else {
            this.connection.sendIqPacket(Iq(Iq.Type.GET, Ping()))
        }
    }

    fun ping(address: Jid): ListenableFuture<Iq> {
        val iq = Iq(Iq.Type.GET, Ping())
        iq.setTo(address)
        return this.connection.sendIqPacket(iq)
    }

    fun ping(runnable: Runnable) {
        val pingFuture = this.connection.sendIqPacket(Iq(Iq.Type.GET, Ping()))
        Futures.addCallback(
            pingFuture,
            object : com.google.common.util.concurrent.FutureCallback<Iq> {
                override fun onSuccess(result: Iq) {
                    runnable.run()
                }

                override fun onFailure(t: Throwable) {
                    if (t is TimeoutException) return
                    runnable.run()
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun pong(packet: Iq) {
        this.connection.sendResultFor(packet)
    }
}
