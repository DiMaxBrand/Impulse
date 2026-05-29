package eu.siacs.conversations.xmpp.manager

import android.content.Context
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.Config
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.disco.external.Services
import im.conversations.android.xmpp.model.stanza.Iq
import java.util.Collections
import org.webrtc.PeerConnection

class ExternalServiceDiscoveryManager(context: Context, connection: XmppConnection) :
    AbstractManager(context, connection) {

    fun getIceServers(): ListenableFuture<Collection<PeerConnection.IceServer>> {
        if (Config.DISABLE_PROXY_LOOKUP) {
            return Futures.immediateFuture<Collection<PeerConnection.IceServer>>(Collections.emptyList())
        }
        return if (hasFeature()) {
            Futures.transform(
                getServices(),
                { services -> ArrayList(services!!.getIceServers()) },
                MoreExecutors.directExecutor()
            )
        } else {
            Futures.immediateFailedFuture(
                IllegalStateException("Server has no support for external service discovery")
            )
        }
    }

    fun getServices(): ListenableFuture<Services> {
        val request = Iq(Iq.Type.GET)
        request.setTo(getAccount().getDomain())
        request.addExtension(Services())
        return Futures.transform(
            this.connection.sendIqPacket(request),
            { response ->
                response!!.getExtension(Services::class.java)
                    ?: throw IllegalStateException("Response did not contain services")
            },
            MoreExecutors.directExecutor()
        )
    }

    fun hasFeature(): Boolean =
        getManager(DiscoManager::class.java)
            .hasServerFeature(Namespace.EXTERNAL_SERVICE_DISCOVERY)
}
