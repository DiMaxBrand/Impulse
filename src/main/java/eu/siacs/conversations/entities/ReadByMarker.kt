package eu.siacs.conversations.entities

import eu.siacs.conversations.xmpp.Jid
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Collection
import java.util.concurrent.CopyOnWriteArraySet

class ReadByMarker private constructor() : MucOptions.IdentifiableUser {

    var fullJid: Jid? = null
        private set
    var realJid: Jid? = null
        private set
    private var occupantId: String? = null

    fun toJson(): JSONObject {
        val jsonObject = JSONObject()
        fullJid?.let {
            try { jsonObject.put("fullJid", it.toString()) } catch (e: JSONException) { /* ignore */ }
        }
        realJid?.let {
            try { jsonObject.put("realJid", it.toString()) } catch (e: JSONException) { /* ignore */ }
        }
        occupantId?.let {
            try { jsonObject.put("occupantId", it) } catch (e: JSONException) { /* ignore */ }
        }
        return jsonObject
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val marker = other as ReadByMarker
        if (fullJid != marker.fullJid) return false
        return realJid == marker.realJid
    }

    override fun hashCode(): Int {
        var result = fullJid?.hashCode() ?: 0
        result = 31 * result + (realJid?.hashCode() ?: 0)
        return result
    }

    override fun mucUserAddress(): Jid? = fullJid

    override fun mucUserRealAddress(): Jid? = realJid?.asBareJid()

    override fun mucUserOccupantId(): String? = occupantId

    companion object {
        @JvmStatic
        fun fromJson(jsonArray: JSONArray): Set<ReadByMarker> {
            val readByMarkers: MutableSet<ReadByMarker> = CopyOnWriteArraySet()
            for (i in 0 until jsonArray.length()) {
                try {
                    readByMarkers.add(fromJson(jsonArray.getJSONObject(i)))
                } catch (e: JSONException) {
                    // ignored
                }
            }
            return readByMarkers
        }

        @JvmStatic
        fun from(message: Message): ReadByMarker {
            val marker = ReadByMarker()
            marker.occupantId = message.occupantId
            marker.fullJid = message.counterpart
            marker.realJid = message.trueCounterpart
            return marker
        }

        @JvmStatic
        fun from(user: MucOptions.User): ReadByMarker {
            val marker = ReadByMarker()
            marker.occupantId = user.occupantId
            marker.fullJid = user.fullJid
            marker.realJid = user.realJid
            return marker
        }

        @JvmStatic
        fun from(users: Collection<MucOptions.User>): Set<ReadByMarker> {
            val markers: MutableSet<ReadByMarker> = CopyOnWriteArraySet()
            for (user in users) {
                markers.add(from(user))
            }
            return markers
        }

        @JvmStatic
        fun fromJson(jsonObject: JSONObject): ReadByMarker {
            val marker = ReadByMarker()
            marker.fullJid = try {
                Jid.of(jsonObject.getString("fullJid"))
            } catch (e: Exception) {
                null
            }
            marker.realJid = try {
                Jid.of(jsonObject.getString("realJid"))
            } catch (e: Exception) {
                null
            }
            marker.occupantId = try {
                jsonObject.getString("occupantId")
            } catch (e: Exception) {
                null
            }
            return marker
        }

        @JvmStatic
        fun fromJsonString(json: String?): Set<ReadByMarker> {
            return try {
                fromJson(JSONArray(json))
            } catch (e: Exception) {
                CopyOnWriteArraySet()
            }
        }

        @JvmStatic
        fun toJson(readByMarkers: Set<ReadByMarker>): JSONArray {
            val jsonArray = JSONArray()
            for (marker in readByMarkers) {
                jsonArray.put(marker.toJson())
            }
            return jsonArray
        }

        @JvmStatic
        fun contains(needle: ReadByMarker, readByMarkers: Set<ReadByMarker>): Boolean {
            for (marker in readByMarkers) {
                if (marker.occupantId != null && needle.occupantId != null) {
                    if (marker.occupantId == needle.occupantId) return true
                } else if (marker.realJid != null && needle.realJid != null) {
                    if (marker.realJid!!.asBareJid() == needle.realJid!!.asBareJid()) return true
                } else if (marker.fullJid != null && needle.fullJid != null) {
                    if (marker.fullJid == needle.fullJid) return true
                }
            }
            return false
        }

        @JvmStatic
        fun allUsersRepresented(
            users: Collection<MucOptions.User>,
            markers: Set<ReadByMarker>
        ): Boolean {
            for (user in users) {
                if (!contains(from(user), markers)) return false
            }
            return true
        }

        @JvmStatic
        fun allUsersRepresented(
            users: Collection<MucOptions.User>,
            markers: Set<ReadByMarker>,
            marker: ReadByMarker
        ): Boolean {
            val markersCopy: MutableSet<ReadByMarker> = CopyOnWriteArraySet(markers)
            markersCopy.add(marker)
            return allUsersRepresented(users, markersCopy)
        }
    }
}
