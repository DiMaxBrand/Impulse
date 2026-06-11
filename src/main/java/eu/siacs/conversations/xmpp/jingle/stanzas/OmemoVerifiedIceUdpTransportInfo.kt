package eu.siacs.conversations.xmpp.jingle.stanzas

import eu.siacs.conversations.xml.Namespace

class OmemoVerifiedIceUdpTransportInfo : IceUdpTransportInfo() {
    fun ensureNoPlaintextFingerprint() {
        if (findChild("fingerprint", Namespace.JINGLE_APPS_DTLS) != null) {
            throw IllegalStateException("OmemoVerifiedIceUdpTransportInfo contains plaintext fingerprint")
        }
    }

    companion object {
        @JvmStatic
        fun upgrade(transportInfo: IceUdpTransportInfo): IceUdpTransportInfo {
            if (transportInfo.hasChild("fingerprint", Namespace.JINGLE_APPS_DTLS)) return transportInfo
            if (transportInfo.hasChild("fingerprint", Namespace.OMEMO_DTLS_SRTP_VERIFICATION)) {
                return OmemoVerifiedIceUdpTransportInfo().apply {
                    setAttributes(transportInfo.attributes)
                    setChildren(transportInfo.children)
                }
            }
            return transportInfo
        }
    }
}
