package eu.siacs.conversations.ui

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONObject

/** Account + peer scoped transition state. Clear markers prevent old archive events resurrecting it. */
object ReplyStatuses {
    const val TTL_MS = 30 * 60 * 1000L
    val codes = setOf("working", "checking", "waiting", "clear")
    data class Status(val code: String, val stamp: Long, val expires: Long, val clearOnSend: Boolean) {
        fun active(now: Long) = code != "clear" && expires > now
    }
    private val states = mutableStateMapOf<String, Status>()
    private val loaded = mutableSetOf<String>()
    private fun key(account: String, peer: String, own: Boolean) = "$account|$peer|$own"

    @Synchronized
    fun get(context: Context, account: String, peer: String, own: Boolean): Status? {
        val key = key(account, peer, own)
        if (loaded.add(key)) {
            val stored = context.getSharedPreferences("reply_statuses", 0).getString(key, null)
            try { if (stored != null) {
                val json = JSONObject(stored)
                states[key] = Status(json.getString("code"), json.getLong("stamp"), json.getLong("expires"), json.optBoolean("clear", true))
            } } catch (_: Exception) { }
        }
        return states[key]
    }

    fun valid(status: Status, now: Long): Boolean = status.code in codes && status.stamp > 0 &&
        status.stamp <= now + 5 * 60 * 1000 && (status.code == "clear" ||
        status.expires in status.stamp..(status.stamp + TTL_MS))

    fun newer(previous: Status?, next: Status): Boolean = previous == null || next.stamp > previous.stamp ||
        (next.stamp == previous.stamp && next.code == "clear" && previous.code != "clear")

    @Synchronized
    fun adopt(context: Context, account: String, peer: String, own: Boolean, status: Status): Boolean {
        if (!valid(status, System.currentTimeMillis())) return false
        val previous = get(context, account, peer, own)
        if (!newer(previous, status)) return false
        val key = key(account, peer, own)
        states[key] = status
        context.getSharedPreferences("reply_statuses", 0).edit().putString(key,
            JSONObject().put("code", status.code).put("stamp", status.stamp).put("expires", status.expires)
                .put("clear", status.clearOnSend).toString()).apply()
        return true
    }

    @JvmStatic
    fun onMessage(context: Context, message: eu.siacs.conversations.entities.Message) {
        val conversation = message.getConversation()
        if (conversation.getMode() != 0) return
        val own = message.getStatus() != eu.siacs.conversations.entities.Message.STATUS_RECEIVED
        val account = conversation.getAccount().getUuid() ?: return
        val peer = conversation.getAddress().asBareJid().toString()
        val status = get(context, account, peer, own) ?: return
        if (status.clearOnSend && message.timeSent >= status.stamp) {
            adopt(context, account, peer, own, Status("clear", message.timeSent, 0, true))
        }
    }
}
