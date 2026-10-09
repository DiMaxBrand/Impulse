package eu.siacs.conversations.update

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tracking IDs of the bug reports this device has sent, so that when a release's notes mention an
 * ID the reporter can be told "your issue was fixed". Plain SharedPreferences, like
 * [UpdatePreferences]; entries are dropped once matched or after [DROP_AFTER_DAYS] days, so the
 * list cannot grow without bound.
 */
class BugReportRegistry(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("bug_reports", Context.MODE_PRIVATE)

    data class Entry(val id: String, val channel: String, val sentAt: Long)

    /** Makes a new ID (BUG-XXXX), remembers it with the channel the reporter is on, returns it. */
    @Synchronized
    fun register(channel: String, now: Long = System.currentTimeMillis()): String {
        val existing = entries(now).map { it.id }.toSet()
        var id: String
        do {
            id = "BUG-" + (1..4).map { ALPHABET.random() }.joinToString("")
        } while (id in existing)
        write(entries(now) + Entry(id, channel, now))
        return id
    }

    @Synchronized
    fun pending(now: Long = System.currentTimeMillis()): List<Entry> = entries(now)

    /** True when the oldest pending report has waited past the point where the card should point
     * at support rather than keep promising an automatic notice. */
    fun hasStale(now: Long = System.currentTimeMillis()): Boolean =
        pending(now).any { now - it.sentAt > TimeUnit.DAYS.toMillis(STALE_AFTER_DAYS) }

    /**
     * Looks for stored IDs in [releaseNotes] (case-insensitive); removes and returns the ones
     * found. The fix then reaches exactly the people who reported it, whichever channel it
     * shipped on -- the check that calls this already fetched their channel's notes.
     */
    @Synchronized
    fun matchFixed(releaseNotes: String, now: Long = System.currentTimeMillis()): List<Entry> {
        if (releaseNotes.isBlank()) return emptyList()
        val all = entries(now)
        val fixed = all.filter { releaseNotes.contains(it.id, ignoreCase = true) }
        if (fixed.isNotEmpty()) write(all - fixed.toSet())
        return fixed
    }

    private fun entries(now: Long): List<Entry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val parsed = try {
            val array = JSONArray(raw)
            (0 until array.length()).map {
                val o = array.getJSONObject(it)
                Entry(o.getString("id"), o.optString("channel"), o.optLong("sentAt"))
            }
        } catch (_: Exception) {
            emptyList()
        }
        val cutoff = now - TimeUnit.DAYS.toMillis(DROP_AFTER_DAYS)
        val kept = parsed.filter { it.sentAt >= cutoff }
        if (kept.size != parsed.size) write(kept)
        return kept
    }

    private fun write(list: List<Entry>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject().put("id", it.id).put("channel", it.channel).put("sentAt", it.sentAt)
            )
        }
        prefs.edit { putString(KEY, array.toString()) }
    }

    companion object {
        private const val KEY = "pending"
        // No 0/O/1/I/L: the ID gets read aloud and typed by hand into release notes.
        private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
        /** After this long the card suggests contacting support instead. */
        const val STALE_AFTER_DAYS = 14L
        /** Hard limit: an entry older than this is forgotten. */
        const val DROP_AFTER_DAYS = 45L
    }
}
