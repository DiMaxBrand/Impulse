package eu.siacs.conversations.xmpp.jingle

import com.google.common.base.MoreObjects
import com.google.common.base.Objects
import com.google.common.collect.Collections2
import com.google.common.collect.ImmutableSet
import eu.siacs.conversations.xmpp.jingle.stanzas.Content
import eu.siacs.conversations.xmpp.jingle.stanzas.IceUdpTransportInfo
import eu.siacs.conversations.xmpp.jingle.stanzas.RtpDescription

class ContentAddition private constructor(
    @JvmField val direction: Direction,
    @JvmField val summary: Set<Summary>
) {

    fun media(): Set<Media> =
        ImmutableSet.copyOf(Collections2.transform(summary) { s -> s!!.media })

    override fun toString(): String =
        MoreObjects.toStringHelper(this)
            .add("direction", direction)
            .add("summary", summary)
            .toString()

    enum class Direction {
        OUTGOING,
        INCOMING
    }

    class Summary(
        @JvmField val name: String,
        val media: Media,
        val senders: Content.Senders
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val summary = other as Summary
            return Objects.equal(name, summary.name)
                    && media == summary.media
                    && senders == summary.senders
        }

        override fun hashCode(): Int = Objects.hashCode(name, media, senders)

        override fun toString(): String =
            MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("media", media)
                .add("senders", senders)
                .toString()
    }

    companion object {
        @JvmStatic
        fun of(direction: Direction, rtpContentMap: RtpContentMap): ContentAddition =
            ContentAddition(direction, summary(rtpContentMap))

        @JvmStatic
        fun summary(rtpContentMap: RtpContentMap): Set<Summary> =
            ImmutableSet.copyOf(
                Collections2.transform(rtpContentMap.contents.entries) { e ->
                    val dt: DescriptionTransport<RtpDescription, IceUdpTransportInfo> = e!!.value
                    Summary(e.key, dt.description.getMedia(), dt.senders)
                }
            )
    }
}
