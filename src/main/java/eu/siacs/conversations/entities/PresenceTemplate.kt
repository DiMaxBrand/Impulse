package eu.siacs.conversations.entities

import android.content.ContentValues
import android.database.Cursor
import im.conversations.android.xmpp.model.stanza.Presence
import java.util.Objects
import org.jspecify.annotations.NonNull

class PresenceTemplate : AbstractEntity {

    companion object {
        const val TABELNAME = "presence_templates"
        const val LAST_USED = "last_used"
        const val MESSAGE = "message"
        const val STATUS = "status"

        @JvmStatic
        fun fromCursor(cursor: Cursor): PresenceTemplate {
            val template = PresenceTemplate()
            template.uuid = cursor.getString(cursor.getColumnIndexOrThrow(UUID))
            template.lastUsed = cursor.getLong(cursor.getColumnIndexOrThrow(LAST_USED))
            template.statusMessage = cursor.getString(cursor.getColumnIndexOrThrow(MESSAGE))
            template.status =
                Presence.Availability.valueOfShown(
                    cursor.getString(cursor.getColumnIndexOrThrow(STATUS))
                )
            return template
        }
    }

    private var lastUsed: Long = 0
    private var statusMessage: String? = null
    private var status: Presence.Availability = Presence.Availability.ONLINE

    constructor(status: Presence.Availability, statusMessage: String?) {
        this.status = status
        this.statusMessage = statusMessage
        this.lastUsed = System.currentTimeMillis()
        this.uuid = java.util.UUID.randomUUID().toString()
    }

    private constructor()

    override fun getContentValues(): ContentValues {
        val show = status.toShowString()
        val values = ContentValues()
        values.put(LAST_USED, lastUsed)
        values.put(MESSAGE, statusMessage)
        values.put(STATUS, show ?: "")
        values.put(UUID, uuid)
        return values
    }

    fun getStatus(): Presence.Availability = status

    fun getStatusMessage(): String? = statusMessage

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val template = other as PresenceTemplate
        if (!Objects.equals(statusMessage, template.statusMessage)) return false
        return status == template.status
    }

    override fun hashCode(): Int {
        var result = statusMessage?.hashCode() ?: 0
        result = 31 * result + status.hashCode()
        return result
    }

    override fun toString(): @NonNull String = statusMessage ?: ""
}
