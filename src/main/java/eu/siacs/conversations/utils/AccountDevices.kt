package eu.siacs.conversations.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One device of an account, as the shared list (see DevicesManager) describes it. */
data class DeviceEntry(
    val id: String,
    val resource: String,
    val name: String,
    val version: String,
    /** Left out of the account's status: it no longer says "on silent" or "away" for the account. */
    val excluded: Boolean,
    /** For an entry made on behalf of a device that has not claimed it yet (it runs a version that
     * does not know this list): when the request lapses. 0 once the device has claimed it. */
    val until: Long,
    val seen: Long,
) {
    /** Made by another phone for a device that has not updated yet. */
    val pending: Boolean get() = id.isEmpty()
}

/**
 * What this phone remembers of the devices lists: its own permanent device id, and per account the
 * last list it saw, so that being left out of the status also applies at start-up, before the
 * network answers.
 */
class AccountDevices(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("account_devices", Context.MODE_PRIVATE)

    /** Stays for as long as the app is installed; unlike the session name it never changes. */
    val ownId: String
        get() {
            prefs.getString("own_id", null)?.let { return it }
            val id = UUID.randomUUID().toString().take(8)
            prefs.edit().putString("own_id", id).apply()
            return id
        }

    fun load(account: String): List<DeviceEntry> =
        try {
            val array = JSONArray(prefs.getString("list_$account", "[]"))
            (0 until array.length()).map {
                val o = array.getJSONObject(it)
                DeviceEntry(
                    o.optString("id"), o.optString("resource"), o.optString("name"), o.optString("version"),
                    o.optBoolean("excluded"), o.optLong("until"), o.optLong("seen"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }

    fun save(account: String, list: List<DeviceEntry>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject().put("id", it.id).put("resource", it.resource).put("name", it.name)
                    .put("version", it.version).put("excluded", it.excluded).put("until", it.until)
                    .put("seen", it.seen)
            )
        }
        prefs.edit().putString("list_$account", array.toString()).apply()
    }

    /** Devices the person chose to keep as they are when asked about them at launch. */
    fun isKept(device: String): Boolean = prefs.getBoolean("kept_$device", false)

    fun keep(device: String) = prefs.edit().putBoolean("kept_$device", true).apply()

    fun forgetKept(device: String) = prefs.edit().remove("kept_$device").apply()
}
