package eu.siacs.conversations.xmpp.jingle

import eu.siacs.conversations.services.CallIntegration
import eu.siacs.conversations.xmpp.Jid

interface OngoingRtpSession {
    fun getWith(): Jid
    fun getSessionId(): String
    fun getCallIntegration(): CallIntegration
    fun getMedia(): Set<Media>
}
