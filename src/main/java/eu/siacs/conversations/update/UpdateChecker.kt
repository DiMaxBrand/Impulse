package eu.siacs.conversations.update

import eu.siacs.conversations.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class UpdateChecker(private val client: OkHttpClient) {

    fun checkForUpdate(channel: UpdateChannel): CheckResult {
        val releases = fetchReleases() ?: return CheckResult.UpToDate
        val current = parseVersion(stripBuildMeta(BuildConfig.VERSION_NAME)) ?: return CheckResult.UpToDate

        val best = releases
            .filter { releaseMatchesChannel(it.optString("tag_name"), channel) }
            .mapNotNull { release ->
                val tag = release.optString("tag_name")
                val version = parseVersion(tag) ?: return@mapNotNull null
                val apkUrl = findApkAsset(release) ?: return@mapNotNull null
                Triple(version, apkUrl, tag) to release
            }
            .maxWithOrNull { (a, _), (b, _) -> compareSemver(a.first, b.first) }
            ?: return CheckResult.UpToDate
        val (bestTriple, bestRelease) = best

        val cmp = compareSemver(bestTriple.first, current)
        return when {
            cmp > 0 -> CheckResult.UpdateAvailable(
                UpdateInfo(
                    versionName = bestTriple.third,
                    channel = channel,
                    downloadUrl = bestTriple.second,
                    releaseNotes = bestRelease.optString("body"),
                    releaseTitle = bestRelease.optString("name"),
                )
            )
            cmp < 0 -> CheckResult.ChannelBehind
            else -> CheckResult.UpToDate
        }
    }

    sealed class CheckResult {
        data class UpdateAvailable(val info: UpdateInfo) : CheckResult()
        object UpToDate : CheckResult()
        // The best release on this channel is older than what is currently installed.
        // Shown when the user switches to a less-cutting-edge channel mid-cycle.
        object ChannelBehind : CheckResult()
    }

    /** Every published release with an APK asset, newest first, regardless of channel or how
     * it compares to the installed version. Powers the developer-options manual version picker,
     * which deliberately bypasses the checkForUpdate() upgrade-only restriction. */
    fun listReleases(): List<UpdateInfo> {
        val releases = fetchReleases() ?: return emptyList()
        return releases
            .mapNotNull { release ->
                val tag = release.optString("tag_name")
                if (tag.isNullOrEmpty()) return@mapNotNull null
                val version = parseVersion(tag) ?: return@mapNotNull null
                val apkUrl = findApkAsset(release) ?: return@mapNotNull null
                Triple(version, apkUrl, tag) to release
            }
            .sortedWith { (a, _), (b, _) -> compareSemver(b.first, a.first) }
            .map { (triple, release) ->
                val (_, apkUrl, tag) = triple
                val channel = when {
                    tag.contains("-alpha.") -> UpdateChannel.ALPHA
                    tag.contains("-beta.") -> UpdateChannel.BETA
                    tag.contains("-rc.") -> UpdateChannel.RC
                    else -> UpdateChannel.STABLE
                }
                UpdateInfo(
                    versionName = tag,
                    channel = channel,
                    downloadUrl = apkUrl,
                    releaseNotes = release.optString("body"),
                    releaseTitle = release.optString("name"),
                )
            }
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
        val isStable = !base.contains('-')
        // Each channel is inclusive of all tiers at or above it in stability, so that
        // e.g. a beta subscriber automatically receives an RC or stable release when it
        // drops — without having to manually switch channels.
        return when (channel) {
            UpdateChannel.STABLE -> isStable
            UpdateChannel.RC    -> isStable || base.contains("-rc.")
            UpdateChannel.BETA  -> isStable || base.contains("-rc.") || base.contains("-beta.")
            UpdateChannel.ALPHA -> isStable || base.contains("-rc.") || base.contains("-beta.") || base.contains("-alpha.")
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

        // Shared by every place that decides whether a *stored* version string (pending or
        // already-downloaded) is still worth acting on — file/pref presence alone isn't enough,
        // since whatever wrote it can only be trusted at the moment it ran; what's actually
        // installed can move on without that state ever being told to reconcile. Always re-derive
        // from BuildConfig.VERSION_NAME — the one source of truth for what's running right now —
        // rather than some other cached "current version" value that could itself be stale.
        fun isNewerThanInstalled(versionName: String?): Boolean {
            if (versionName == null) return false
            val current = parseVersion(stripBuildMeta(BuildConfig.VERSION_NAME)) ?: return true
            val candidate = parseVersion(versionName) ?: return false
            return compareSemver(candidate, current) > 0
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
