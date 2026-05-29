package eu.siacs.conversations.xmpp.jingle.stanzas

import android.util.Log
import com.google.common.base.Preconditions
import com.google.common.base.Strings
import com.google.common.collect.ImmutableList
import eu.siacs.conversations.Config
import eu.siacs.conversations.xml.Element
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.jingle.transports.SocksByteStreamsTransport

class SocksByteStreamsTransportInfo private constructor() :
    GenericTransportInfo("transport", Namespace.JINGLE_TRANSPORTS_S5B) {

    constructor(
        transportId: String,
        candidates: Collection<SocksByteStreamsTransport.Candidate>
    ) : this() {
        Preconditions.checkNotNull(transportId, "transport id must not be null")
        for (candidate in candidates) {
            this.addChild(candidate.asElement())
        }
        this.setAttribute("sid", transportId)
    }

    fun getTransportId(): String? = this.getAttribute("sid")

    fun getTransportInfo(): TransportInfo? {
        return if (hasChild("proxy-error")) {
            ProxyError()
        } else if (hasChild("candidate-error")) {
            CandidateError()
        } else if (hasChild("candidate-used")) {
            val candidateUsed = findChild("candidate-used")
            val cid = candidateUsed?.getAttribute("cid")
            if (Strings.isNullOrEmpty(cid)) null else CandidateUsed(cid!!)
        } else if (hasChild("activated")) {
            val activated = findChild("activated")
            val cid = activated?.getAttribute("cid")
            if (Strings.isNullOrEmpty(cid)) null else Activated(cid!!)
        } else {
            null
        }
    }

    fun getCandidates(): List<SocksByteStreamsTransport.Candidate> {
        val candidateBuilder = ImmutableList.Builder<SocksByteStreamsTransport.Candidate>()
        for (child in this.children) {
            if ("candidate" == child.name
                && Namespace.JINGLE_TRANSPORTS_S5B == child.namespace
            ) {
                try {
                    candidateBuilder.add(SocksByteStreamsTransport.Candidate.of(child))
                } catch (e: Exception) {
                    Log.d(Config.LOGTAG, "skip over broken candidate", e)
                }
            }
        }
        return candidateBuilder.build()
    }

    fun getDestinationAddress(): String? = this.getAttribute("dstaddr")

    abstract class TransportInfo

    class CandidateUsed(@JvmField val cid: String) : TransportInfo()

    class Activated(@JvmField val cid: String) : TransportInfo()

    class CandidateError : TransportInfo()

    class ProxyError : TransportInfo()

    companion object {
        @JvmStatic
        fun upgrade(element: Element): SocksByteStreamsTransportInfo {
            Preconditions.checkArgument(
                "transport" == element.name,
                "Name of provided element is not transport"
            )
            Preconditions.checkArgument(
                Namespace.JINGLE_TRANSPORTS_S5B == element.namespace,
                "Element does not match s5b transport namespace"
            )
            val transportInfo = SocksByteStreamsTransportInfo()
            transportInfo.setAttributes(element.attributes)
            transportInfo.setChildren(element.children)
            return transportInfo
        }
    }
}
