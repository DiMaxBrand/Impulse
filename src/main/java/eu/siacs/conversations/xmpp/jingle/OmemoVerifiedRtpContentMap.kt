package eu.siacs.conversations.xmpp.jingle

import eu.siacs.conversations.xmpp.jingle.stanzas.Group
import eu.siacs.conversations.xmpp.jingle.stanzas.IceUdpTransportInfo
import eu.siacs.conversations.xmpp.jingle.stanzas.OmemoVerifiedIceUdpTransportInfo
import eu.siacs.conversations.xmpp.jingle.stanzas.RtpDescription

class OmemoVerifiedRtpContentMap(
    group: Group?,
    contents: Map<String, DescriptionTransport<RtpDescription, IceUdpTransportInfo>>,
) : RtpContentMap(group, contents) {
    init {
        for (descriptionTransport in contents.values) {
            if (descriptionTransport.transport is OmemoVerifiedIceUdpTransportInfo) {
                descriptionTransport.transport.ensureNoPlaintextFingerprint()
                continue
            }
            throw IllegalStateException("OmemoVerifiedRtpContentMap contains non-verified transport info")
        }
    }
}
