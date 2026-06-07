package eu.siacs.conversations.xmpp.jingle.transports

import com.google.common.util.concurrent.ListenableFuture
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericTransportInfo
import eu.siacs.conversations.xmpp.jingle.stanzas.Group
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch

interface Transport {

    @Throws(IOException::class)
    fun getOutputStream(): OutputStream

    @Throws(IOException::class)
    fun getInputStream(): InputStream

    fun asTransportInfo(): ListenableFuture<TransportInfo>

    fun asInitialTransportInfo(): ListenableFuture<InitialTransportInfo>

    fun readyToSentAdditionalCandidates() {}

    fun terminate()

    fun setTransportCallback(callback: Callback)

    fun connect()

    fun getTerminationLatch(): CountDownLatch

    interface Callback {
        fun onTransportEstablished()

        fun onTransportSetupFailed()

        fun onAdditionalCandidate(contentName: String, candidate: Candidate)

        fun onCandidateUsed(streamId: String, candidate: SocksByteStreamsTransport.Candidate)

        fun onCandidateError(streamId: String)

        fun onProxyActivated(streamId: String, candidate: SocksByteStreamsTransport.Candidate)
    }

    enum class Direction {
        SEND,
        RECEIVE,
        SEND_RECEIVE
    }

    class InitialTransportInfo(
        @JvmField val contentName: String,
        transportInfo: GenericTransportInfo,
        group: Group?,
    ) : TransportInfo(transportInfo, group)

    open class TransportInfo {

        @JvmField val transportInfo: GenericTransportInfo
        @JvmField val group: Group?

        constructor(transportInfo: GenericTransportInfo, group: Group?) {
            this.transportInfo = transportInfo
            this.group = group
        }

        constructor(transportInfo: GenericTransportInfo) {
            this.transportInfo = transportInfo
            this.group = null
        }
    }

    interface Candidate
}
