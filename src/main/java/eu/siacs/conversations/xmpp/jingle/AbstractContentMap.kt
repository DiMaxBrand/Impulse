package eu.siacs.conversations.xmpp.jingle

import com.google.common.collect.Collections2
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import eu.siacs.conversations.xmpp.jingle.stanzas.Content
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericDescription
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericTransportInfo
import eu.siacs.conversations.xmpp.jingle.stanzas.Group
import im.conversations.android.xmpp.model.jingle.Jingle
import im.conversations.android.xmpp.model.stanza.Iq

abstract class AbstractContentMap<D : GenericDescription, T : GenericTransportInfo>(
    @JvmField val group: Group?,
    @JvmField val contents: Map<String, DescriptionTransport<D, T>>
) {

    class UnsupportedApplicationException(message: String) : IllegalArgumentException(message)

    class UnsupportedTransportException(message: String) : IllegalArgumentException(message)

    fun getSenders(): Set<Content.Senders> =
        ImmutableSet.copyOf(Collections2.transform(contents.values) { dt -> dt!!.senders })

    fun getNames(): List<String> = ImmutableList.copyOf(contents.keys)

    fun toJinglePacket(action: Jingle.Action, sessionId: String): Iq {
        val iq = Iq(Iq.Type.SET)
        val jinglePacket = iq.addExtension(Jingle(action, sessionId))
        for ((key, descriptionTransport) in contents) {
            val content = Content(
                Content.Creator.INITIATOR,
                descriptionTransport.senders,
                key
            )
            if (descriptionTransport.description != null) {
                content.addChild(descriptionTransport.description)
            }
            content.addChild(descriptionTransport.transport)
            jinglePacket.addJingleContent(content)
        }
        if (this.group != null) {
            jinglePacket.addGroup(this.group)
        }
        return iq
    }

    fun requireContentDescriptions() {
        if (this.contents.isEmpty()) {
            throw IllegalStateException("No contents available")
        }
        for ((key, value) in this.contents) {
            if (value.description == null) {
                throw IllegalStateException("$key is lacking content description")
            }
        }
    }
}
