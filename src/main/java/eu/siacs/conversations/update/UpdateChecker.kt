package eu.siacs.conversations.update

import eu.siacs.conversations.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class UpdateChecker(private val client: OkHttpClient) {

    fun checkForUpdate(channel: UpdateChannel): UpdateInfo? {
        val releases = fetchReleases() ?: return null
        val current = parseVersion(stripBuildMeta(BuildConfig.VERSION_NAME)) ?: return null

        val best = releases
            .filter { releaseMatchesChannel(it.optString("tag_name"), channel) }
            .mapNotNull { release ->
                val tag = release.optString("tag_name")
                val version = parseVersion(tag) ?: return@mapNotNull null
                val apkUrl = findApkAsset(release) ?: return@mapNotNull null
                Triple(version, apkUrl, tag)
            }
            .maxWithOrNull { a, b -> compareSemver(a.first, b.first) }
            ?: return null

        if (compareSemver(best.first, current) <= 0) return null

        return UpdateInfo(
            versionName = best.third,
            channel = channel,
            downloadUrl = best.second,
            releaseNotes = "",
        )
    }

    private fun fetchReleases(): List<JSONObject>? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/DiMaxBrand/Impulse/releases?per_page=50")
            .header("Accept", "application/vnd.github+json")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val array = JSONArray(body)
                (0 until array.length()).map { array.getJSONObject(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findApkAsset(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            if (name.endsWith(".apk")) {
                return asset.optString("browser_download_url")
            }
        }
        return null
    }

    private fun releaseMatchesChannel(tag: String, channel: UpdateChannel): Boolean {
        val base = stripBuildMeta(tag)
        return when (channel) {
            UpdateChannel.STABLE -> !base.contains('-')
            UpdateChannel.RC -> base.contains("-rc.")
            UpdateChannel.BETA -> base.contains("-beta.")
            UpdateChannel.ALPHA -> base.contains("-alpha.")
        }
    }

    companion object {
        fun stripBuildMeta(version: String): String = version.substringBefore('+')

        data class SemVer(
            val major: Int,
            val minor: Int,
            val patch: Int,
            val preType: String?,
            val preNum: Int,
        )

        fun parseVersion(raw: String): SemVer? {
            val base = stripBuildMeta(raw)
            val dashIdx = base.indexOf('-')
            val corePart = if (dashIdx >= 0) base.substring(0, dashIdx) else base
            val prePart = if (dashIdx >= 0) base.substring(dashIdx + 1) else null

            val parts = corePart.split('.')
            if (parts.size < 3) return null
            val major = parts[0].toIntOrNull() ?: return null
            val minor = parts[1].toIntOrNull() ?: return null
            val patch = parts[2].toIntOrNull() ?: return null

            var preType: String? = null
            var preNum = 0
            if (prePart != null) {
                val dotIdx = prePart.lastIndexOf('.')
                if (dotIdx >= 0) {
                    preType = prePart.substring(0, dotIdx)
                    preNum = prePart.substring(dotIdx + 1).toIntOrNull() ?: 0
                } else {
                    preType = prePart
                }
            }
            return SemVer(major, minor, patch, preType, preNum)
        }

        fun compareSemver(a: SemVer, b: SemVer): Int {
            val core = compareValuesBy(a, b, { it.major }, { it.minor }, { it.patch })
            if (core != 0) return core
            // stable (no pre) > any pre-release
            if (a.preType == null && b.preType != null) return 1
            if (a.preType != null && b.preType == null) return -1
            if (a.preType == null && b.preType == null) return 0
            val preOrder = listOf("alpha", "beta", "rc")
            val aOrd = preOrder.indexOf(a.preType)
            val bOrd = preOrder.indexOf(b.preType)
            val ordCmp = aOrd.compareTo(bOrd)
            if (ordCmp != 0) return ordCmp
            return a.preNum.compareTo(b.preNum)
        }
    }
}
