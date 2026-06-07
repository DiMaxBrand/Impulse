package eu.siacs.conversations.xmpp.manager

import android.content.Context
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.Config
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import eu.siacs.conversations.xmpp.jingle.transports.SocksByteStreamsTransport
import im.conversations.android.xmpp.model.socks5.Query
import im.conversations.android.xmpp.model.stanza.Iq
import java.util.UUID

class StreamHostManager(context: Context, connection: XmppConnection) :
    AbstractManager(context, connection) {

    fun getProxyCandidate(asInitiator: Boolean): ListenableFuture<SocksByteStreamsTransport.Candidate> {
        if (Config.DISABLE_PROXY_LOOKUP) {
            return Futures.immediateFailedFuture(
                IllegalStateException("Proxy look up is disabled")
            )
        }
        val streamer =
            getManager(DiscoManager::class.java).findDiscoItemByFeature(Namespace.BYTE_STREAMS)
                ?: return Futures.immediateFailedFuture(
                    IllegalStateException("No proxy/streamer found")
                )
        return getProxyCandidate(asInitiator, streamer.key)
    }

    private fun getProxyCandidate(
        asInitiator: Boolean,
        streamer: Jid
    ): ListenableFuture<SocksByteStreamsTransport.Candidate> {
        val iq = Iq(Iq.Type.GET, Query())
        iq.setTo(streamer)
        return Futures.transform(
            connection.sendIqPacket(iq),
            { response ->
                val query = response.getExtension(Query::class.java)
                    ?: throw IllegalStateException("No stream host query found in response")
                val streamHost = query.getStreamHost()
                    ?: throw IllegalStateException("no stream host found in query")
                val jid = streamHost.getJid()
                val host = streamHost.getHost()
                val port = streamHost.getPort()
                if (jid == null || host == null || port == null) {
                    throw IllegalStateException("StreamHost had incomplete information")
                }
                SocksByteStreamsTransport.Candidate(
                    UUID.randomUUID().toString(),
                    host,
                    streamer,
                    port,
                    655360 + (if (asInitiator) 0 else 15),
                    SocksByteStreamsTransport.CandidateType.PROXY
                )
            },
            MoreExecutors.directExecutor()
        )
    }
}
