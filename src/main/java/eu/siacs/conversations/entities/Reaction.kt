package eu.siacs.conversations.entities

import android.util.Log
import androidx.annotation.NonNull
import com.google.common.base.MoreObjects
import com.google.common.base.Strings
import com.google.common.collect.Collections2
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Maps
import com.google.common.collect.Multimaps
import com.google.common.collect.Ordering
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import eu.siacs.conversations.Config
import eu.siacs.conversations.utils.Emoticons
import eu.siacs.conversations.xmpp.Jid
import java.io.IOException
import java.util.Comparator

class Reaction(
    @JvmField val reaction: String,
    @JvmField val received: Boolean,
    @JvmField val from: Jid?,
    @JvmField val trueJid: Jid?,
    @JvmField val occupantId: String?
) : MucOptions.IdentifiableUser {

    fun normalizedReaction(): String = Emoticons.normalizeToVS16(this.reaction)

    override fun mucUserAddress(): Jid? = from

    override fun mucUserRealAddress(): Jid? = trueJid?.asBareJid()

    override fun mucUserOccupantId(): String? = occupantId

    @NonNull
    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .add("reaction", reaction)
            .add("received", received)
            .add("from", from)
            .add("trueJid", trueJid)
            .add("occupantId", occupantId)
            .toString()
    }

    class Aggregated(
        @JvmField val reactions: List<Map.Entry<String, Int>>,
        @JvmField val ourReactions: Set<String>
    )

    private class JidTypeAdapter : TypeAdapter<Jid>() {
        @Throws(IOException::class)
        override fun write(out: JsonWriter, value: Jid?) {
            if (value == null) {
                out.nullValue()
            } else {
                out.value(value.toString())
            }
        }

        @Throws(IOException::class)
        override fun read(`in`: JsonReader): Jid? {
            return when (`in`.peek()) {
                JsonToken.NULL -> {
                    `in`.nextNull()
                    null
                }
                JsonToken.STRING -> Jid.of(`in`.nextString())
                else -> throw IOException("Unexpected token")
            }
        }
    }

    companion object {
        @JvmField
        val SUGGESTIONS: List<String> = listOf(
            "❤️",
            "👍",
            "👎",
            "😂",
            "😮",
            "😢"
        )

        private val GSON: Gson = GsonBuilder()
            .registerTypeAdapter(Jid::class.java, JidTypeAdapter())
            .create()

        @JvmStatic
        fun toString(reactions: Collection<Reaction>?): String? {
            return if (reactions == null || reactions.isEmpty()) null else GSON.toJson(reactions)
        }

        @JvmStatic
        fun fromString(asString: String?): Collection<Reaction> {
            if (Strings.isNullOrEmpty(asString)) {
                return emptyList()
            }
            return try {
                GSON.fromJson(asString, object : TypeToken<List<Reaction>>() {}.type)
            } catch (e: Exception) {
                Log.e(Config.LOGTAG, "could not restore reactions", e)
                emptyList()
            }
        }

        @JvmStatic
        fun withOccupantId(
            existing: Collection<Reaction>,
            reactions: Collection<String>,
            received: Boolean,
            from: Jid?,
            trueJid: Jid?,
            occupantId: String
        ): Collection<Reaction> {
            val builder = ImmutableList.Builder<Reaction>()
            builder.addAll(Collections2.filter(existing) { e -> occupantId != e!!.occupantId })
            builder.addAll(Collections2.transform(reactions) { r -> Reaction(r!!, received, from, trueJid, occupantId) })
            return builder.build()
        }

        @JvmStatic
        fun withFrom(
            existing: Collection<Reaction>,
            reactions: Collection<String>,
            received: Boolean,
            from: Jid
        ): Collection<Reaction> {
            val builder = ImmutableList.Builder<Reaction>()
            builder.addAll(Collections2.filter(existing) { e -> !from.asBareJid().equals(e!!.from!!.asBareJid()) })
            builder.addAll(Collections2.transform(reactions) { r -> Reaction(r!!, received, from, null, null) })
            return builder.build()
        }

        @JvmStatic
        fun aggregated(reactions: Collection<Reaction>): Aggregated {
            val aggregatedReactions: Map<String, Int> =
                Maps.transformValues(
                    Multimaps.index(reactions, Reaction::normalizedReaction).asMap()
                ) { it!!.size }
            val sortedList: List<Map.Entry<String, Int>> = Ordering.from(
                Comparator.comparingInt { o: Map.Entry<String, Int> -> o.value }
            )
                .reverse<Map.Entry<String, Int>>()
                .immutableSortedCopy(aggregatedReactions.entries)
            return Aggregated(
                sortedList,
                ImmutableSet.copyOf(
                    Collections2.transform(
                        Collections2.filter(reactions) { r -> !r!!.received },
                        Reaction::normalizedReaction
                    )
                )
            )
        }
    }
}
