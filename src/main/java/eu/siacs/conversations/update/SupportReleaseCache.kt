package eu.siacs.conversations.update

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Disposable release-note cache, never a registry of contacts or tracking items. */
class SupportReleaseCache(context: Context) {
    private val file = File(context.cacheDir, "support-release-history.json")

    fun read(): List<UpdateChecker.NewerRelease>? = try {
        val array = JSONArray(file.readText())
        (0 until array.length()).map {
            val entry = array.getJSONObject(it)
            UpdateChecker.NewerRelease(entry.getString("version"), entry.getString("notes"))
        }
    } catch (_: Exception) { null }

    fun isFresh(): Boolean = file.exists() && System.currentTimeMillis() - file.lastModified() < 15 * 60 * 1000L

    fun write(releases: List<UpdateChecker.NewerRelease>) {
        val array = JSONArray()
        releases.forEach { array.put(JSONObject().put("version", it.versionName).put("notes", it.notes)) }
        try {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(array.toString())
            temp.renameTo(file)
        } catch (_: Exception) { /* The overview still uses this check's result. */ }
    }
}
