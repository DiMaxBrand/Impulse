package eu.siacs.conversations.xmpp.jingle

import eu.siacs.conversations.xmpp.jingle.stanzas.Content
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericDescription
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericTransportInfo

class DescriptionTransport<D : GenericDescription, T : GenericTransportInfo>(
    @JvmField val senders: Content.Senders,
    @JvmField val description: D,
    @JvmField val transport: T,
)
