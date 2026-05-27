package eu.siacs.conversations.xmpp.jingle

import eu.siacs.conversations.xmpp.jingle.stanzas.Content
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericDescription
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericTransportInfo

class DescriptionTransport<D : GenericDescription, T : GenericTransportInfo>(
    val senders: Content.Senders,
    val description: D,
    val transport: T,
)
