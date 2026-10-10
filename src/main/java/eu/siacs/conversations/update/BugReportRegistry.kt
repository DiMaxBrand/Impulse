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
 * [UpdatePreferences].
 *
 * An entry lives through three stages: waiting (sent, no release names it yet), fixed (a release
 * newer than what is installed names it -- [Entry.fixedIn] is set and the reporter has been
 * told), and gone (the fixing version is installed, or the entry is older than
 * [DROP_AFTER_DAYS]). Staying around until the fix is actually installed -- by the overnight
 * update or the Install button -- is what keeps the Updates screen saying "fixed in X, update to
 * get it" for as long as that is true.
 */
class BugReportRegistry(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("bug_reports", Context.MODE_PRIVATE)

    data class Entry(
        val id: String,
        val channel: String,
        val sentAt: Long,
        /** Version name of the oldest newer release whose notes name this ID, once found. */
        val fixedIn: String? = null,
    )

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

    /** Reports still waiting for a release to name them. */
    fun waiting(now: Long = System.currentTimeMillis()): List<Entry> =
        pending(now).filter { it.fixedIn == null }

    /** Reports a newer release already fixes, not yet installed. */
    fun fixed(now: Long = System.currentTimeMillis()): List<Entry> =
        pending(now).filter { it.fixedIn != null }

    /** True when a waiting report is old enough that the card should point at support instead of
     * promising an automatic notice. */
    fun hasStale(now: Long = System.currentTimeMillis()): Boolean =
        waiting(now).any { now - it.sentAt > TimeUnit.DAYS.toMillis(STALE_AFTER_DAYS) }

    /**
     * Looks for waiting IDs in the notes of every release newer than the installed one (all of
     * them, since several can be published between two checks). An ID found in more than one is
     * attributed to the OLDEST of them -- the first release that has the fix. Marks those entries
     * fixed (keeping them) and returns the ones newly matched, so the caller notifies once.
     */
    @Synchronized
    fun matchFixed(
        newerReleases: List<UpdateChecker.NewerRelease>,
        now: Long = System.currentTimeMillis(),
    ): List<Entry> {
        if (newerReleases.isEmpty()) return emptyList()
        val oldestFirst =
            newerReleases.sortedWith { a, b ->
                val va = UpdateChecker.parseVersion(a.versionName)
                val vb = UpdateChecker.parseVersion(b.versionName)
                if (va != null && vb != null) UpdateChecker.compareSemver(va, vb) else 0
            }
        val all = entries(now)
        val newlyFixed = mutableListOf<Entry>()
        val updated =
            all.map { entry ->
                if (entry.fixedIn != null) return@map entry
                val release =
                    oldestFirst.firstOrNull { it.notes.contains(entry.id, ignoreCase = true) }
                        ?: return@map entry
                entry.copy(fixedIn = release.versionName).also { newlyFixed += it }
            }
        if (newlyFixed.isNotEmpty()) write(updated)
        return newlyFixed
    }

    /** Forgets the reports whose fix is now installed (running version >= the fixing one). */
    @Synchronized
    fun dropInstalled(currentVersionRaw: String, now: Long = System.currentTimeMillis()) {
        val current =
            UpdateChecker.parseVersion(UpdateChecker.stripBuildMeta(currentVersionRaw)) ?: return
        val all = entries(now)
        val kept =
            all.filter { entry ->
                val fixed = entry.fixedIn?.let { UpdateChecker.parseVersion(it) }
                fixed == null || UpdateChecker.compareSemver(current, fixed) < 0
            }
        if (kept.size != all.size) write(kept)
    }

    private fun entries(now: Long): List<Entry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val parsed = try {
            val array = JSONArray(raw)
            (0 until array.length()).map {
                val o = array.getJSONObject(it)
                Entry(
                    o.getString("id"),
                    o.optString("channel"),
                    o.optLong("sentAt"),
                    o.optString("fixedIn").ifEmpty { null },
                )
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
            val o = JSONObject().put("id", it.id).put("channel", it.channel).put("sentAt", it.sentAt)
            it.fixedIn?.let { fixed -> o.put("fixedIn", fixed) }
            array.put(o)
        }
        prefs.edit { putString(KEY, array.toString()) }
    }

    companion object {
        private const val KEY = "pending"
        // No 0/O/1/I/L: the ID gets read aloud and typed by hand into release notes.
        private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
        /** After this long a waiting report's card suggests contacting support instead. */
        const val STALE_AFTER_DAYS = 14L
        /** Hard limit: an entry older than this is forgotten. */
        const val DROP_AFTER_DAYS = 45L
    }
}
