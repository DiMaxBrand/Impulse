package eu.siacs.conversations.xmpp.jingle

import com.google.common.collect.ImmutableSet
import java.util.Locale

enum class Media {
    VIDEO,
    AUDIO,
    UNKNOWN;

    override fun toString(): String = super.toString().lowercase(Locale.ROOT)

    companion object {
        @JvmStatic
        fun of(value: String?): Media = if (value == null) UNKNOWN
        else try {
            valueOf(value.uppercase(Locale.ROOT))
        } catch (e: IllegalArgumentException) {
            UNKNOWN
        }

        @JvmStatic
        fun audioOnly(media: Set<Media>): Boolean = ImmutableSet.of(AUDIO) == media

        @JvmStatic
        fun videoOnly(media: Set<Media>): Boolean = ImmutableSet.of(VIDEO) == media
    }
}
