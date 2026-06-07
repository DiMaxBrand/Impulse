package eu.siacs.conversations.entities

import androidx.annotation.DrawableRes
import com.google.common.base.Strings
import eu.siacs.conversations.R

class RtpSessionStatus(
    @JvmField val successful: Boolean,
    @JvmField val duration: Long
) {

    override fun toString(): String = "$successful:$duration"

    companion object {
        @JvmStatic
        fun of(body: String?): RtpSessionStatus {
            val parts = Strings.nullToEmpty(body).split(":", limit = 2)
            var duration = 0L
            if (parts.size == 2) {
                try {
                    duration = parts[1].toLong()
                } catch (e: NumberFormatException) {
                    // do nothing
                }
            }
            val made = try {
                parts[0].toBoolean()
            } catch (e: Exception) {
                false
            }
            return RtpSessionStatus(made, duration)
        }

        @JvmStatic
        @DrawableRes
        fun getDrawable(received: Boolean, successful: Boolean): Int {
            return if (received) {
                if (successful) R.drawable.ic_call_received_24dp
                else R.drawable.ic_call_missed_24db
            } else {
                if (successful) R.drawable.ic_call_made_24dp
                else R.drawable.ic_call_missed_outgoing_24dp
            }
        }
    }
}
