package eu.siacs.conversations.xmpp.jingle.stanzas

import com.google.common.base.Preconditions
import com.google.common.collect.Iterables
import com.google.common.primitives.Ints
import eu.siacs.conversations.xml.Element
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.jingle.SessionDescription
import eu.siacs.conversations.xmpp.jingle.transports.Transport
import java.util.Collections
import java.util.Hashtable

class WebRTCDataChannelTransportInfo() : GenericTransportInfo("transport", Namespace.JINGLE_TRANSPORT_WEBRTC_DATA_CHANNEL) {

    fun innerIceUdpTransportInfo(): IceUdpTransportInfo? {
        val iceUdpTransportInfo = this.findChild("transport", Namespace.JINGLE_TRANSPORT_ICE_UDP)
        return if (iceUdpTransportInfo != null) {
            IceUdpTransportInfo.upgrade(iceUdpTransportInfo)
        } else null
    }

    fun getSctpPort(): Int? {
        val attribute = this.getAttribute("sctp-port") ?: return null
        return Ints.tryParse(attribute)
    }

    fun getMaxMessageSize(): Int? {
        val attribute = this.getAttribute("max-message-size") ?: return null
        return Ints.tryParse(attribute)
    }

    fun cloneWrapper(): WebRTCDataChannelTransportInfo {
        val iceUdpTransport = this.innerIceUdpTransportInfo()
        val transportInfo = WebRTCDataChannelTransportInfo()
        transportInfo.setAttributes(Hashtable(getAttributes()))
        transportInfo.addChild(iceUdpTransport!!.cloneWrapper())
        return transportInfo
    }

    fun addCandidate(candidate: IceUdpTransportInfo.Candidate) {
        this.innerIceUdpTransportInfo()!!.addChild(candidate)
    }

    fun getCandidates(): List<IceUdpTransportInfo.Candidate> {
        val innerTransportInfo = this.innerIceUdpTransportInfo()
            ?: return Collections.emptyList()
        return innerTransportInfo.getCandidates()
    }

    fun getCredentials(): IceUdpTransportInfo.Credentials? {
        val innerTransportInfo = this.innerIceUdpTransportInfo()
        return innerTransportInfo?.getCredentials()
    }

    companion object {
        @JvmField
        val STUB = WebRTCDataChannelTransportInfo()

        @JvmStatic
        fun upgrade(element: Element): WebRTCDataChannelTransportInfo {
            Preconditions.checkArgument(
                "transport" == element.name,
                "Name of provided element is not transport"
            )
            Preconditions.checkArgument(
                Namespace.JINGLE_TRANSPORT_WEBRTC_DATA_CHANNEL == element.namespace,
                "Element does not match ice-udp transport namespace"
            )
            val transportInfo = WebRTCDataChannelTransportInfo()
            transportInfo.setAttributes(element.attributes)
            transportInfo.setChildren(element.children)
            return transportInfo
        }

        @JvmStatic
        fun of(sessionDescription: SessionDescription): Transport.InitialTransportInfo {
            val media = Iterables.getOnlyElement(sessionDescription.media)
            val id = Iterables.getFirst(media.attributes["mid"], null)
            Preconditions.checkNotNull(id, "media has no mid")
            val maxMessageSize = Iterables.getFirst(media.attributes["max-message-size"], null)
            val maxMessageSizeInt = if (maxMessageSize == null) null else Ints.tryParse(maxMessageSize)
            val sctpPort = Iterables.getFirst(media.attributes["sctp-port"], null)
            val sctpPortInt = if (sctpPort == null) null else Ints.tryParse(sctpPort)
            val webRTCDataChannelTransportInfo = WebRTCDataChannelTransportInfo()
            if (maxMessageSizeInt != null) {
                webRTCDataChannelTransportInfo.setAttribute("max-message-size", maxMessageSizeInt)
            }
            if (sctpPortInt != null) {
                webRTCDataChannelTransportInfo.setAttribute("sctp-port", sctpPortInt)
            }
            webRTCDataChannelTransportInfo.addChild(IceUdpTransportInfo.of(sessionDescription, media))

            val groupAttribute = Iterables.getFirst(sessionDescription.attributes["group"], null)
            val group = if (groupAttribute == null) null else Group.ofSdpString(groupAttribute)
            return Transport.InitialTransportInfo(id!!, webRTCDataChannelTransportInfo, group)
        }
    }
}
