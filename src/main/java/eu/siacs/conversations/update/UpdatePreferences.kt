package eu.siacs.conversations.update

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class UpdatePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)

    var selectedChannel: UpdateChannel
        get() = UpdateChannel.fromId(prefs.getString(KEY_CHANNEL, UpdateChannel.STABLE.id)!!)
        set(value) = prefs.edit { putString(KEY_CHANNEL, value.id) }

    var autoCheck: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_CHECK, value) }

    var pendingUpdateVersion: String?
        get() = prefs.getString(KEY_PENDING_VERSION, null)
        set(value) = prefs.edit { putString(KEY_PENDING_VERSION, value) }

    var pendingUpdateUrl: String?
        get() = prefs.getString(KEY_PENDING_URL, null)
        set(value) = prefs.edit { putString(KEY_PENDING_URL, value) }

    var pendingReleaseNotes: String?
        get() = prefs.getString(KEY_PENDING_RELEASE_NOTES, null)
        set(value) = prefs.edit { putString(KEY_PENDING_RELEASE_NOTES, value) }

    var pendingReleaseTitle: String?
        get() = prefs.getString(KEY_PENDING_RELEASE_TITLE, null)
        set(value) = prefs.edit { putString(KEY_PENDING_RELEASE_TITLE, value) }

    var pendingNoWifi: Boolean
        get() = prefs.getBoolean(KEY_PENDING_NO_WIFI, false)
        set(value) = prefs.edit { putBoolean(KEY_PENDING_NO_WIFI, value) }

    var activeDownloadId: Long
        get() = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        set(value) = prefs.edit { putLong(KEY_DOWNLOAD_ID, value) }

    var downloadedApkPath: String?
        get() = prefs.getString(KEY_DOWNLOADED_APK, null)
        set(value) = prefs.edit { putString(KEY_DOWNLOADED_APK, value) }

    var hasInstalledUpdate: Boolean
        get() = prefs.getBoolean(KEY_HAS_INSTALLED, false)
        set(value) = prefs.edit { putBoolean(KEY_HAS_INSTALLED, value) }

    var downloadedVersion: String?
        get() = prefs.getString(KEY_DOWNLOADED_VERSION, null)
        set(value) = prefs.edit { putString(KEY_DOWNLOADED_VERSION, value) }

    var sheetDismissedUntil: Long
        get() = prefs.getLong(KEY_SHEET_DISMISSED_UNTIL, 0L)
        set(value) = prefs.edit { putLong(KEY_SHEET_DISMISSED_UNTIL, value) }

    fun clearPending() {
        prefs.edit {
            remove(KEY_PENDING_VERSION)
            remove(KEY_PENDING_URL)
            remove(KEY_PENDING_NO_WIFI)
        }
    }

    fun downloadedApkExists(): Boolean {
        val path = downloadedApkPath ?: return false
        val file = java.io.File(android.net.Uri.parse(path).path ?: path)
        return file.exists()
    }

    fun clearDownload() {
        prefs.edit {
            remove(KEY_DOWNLOADED_APK)
            remove(KEY_DOWNLOADED_VERSION)
            remove(KEY_PENDING_RELEASE_NOTES)
            remove(KEY_PENDING_RELEASE_TITLE)
        }
    }

    /** Self-heals stale pending/downloaded state left over from before an update was installed:
     * onConfirmInstall() hands off to the system installer without clearing these (there's no
     * reliable "install actually finished" callback — the process can die mid-install), so on
     * the FIRST launch of a version that was itself the pending update, these prefs are still
     * pointing at what's now already installed. If the referenced APK file has since been
     * cleaned up (nightly wipe, DownloadManager retention), the sheet would otherwise land in
     * the dead IDLE phase — a "new version available" card with no button at all.
     *
     * Only call this once, at process startup, before any UI (or the developer-options manual
     * version picker, which deliberately stages a version that isn't newer) has run — otherwise
     * this would immediately wipe out a deliberate downgrade pick made later in the session. */
    fun clearIfNotNewerThan(currentVersionRaw: String) {
        val current =
            UpdateChecker.parseVersion(UpdateChecker.stripBuildMeta(currentVersionRaw))
                ?: return
        pendingUpdateVersion?.let { UpdateChecker.parseVersion(it) }?.let { pending ->
            if (UpdateChecker.compareSemver(pending, current) <= 0) clearPending()
        }
        downloadedVersion?.let { UpdateChecker.parseVersion(it) }?.let { downloaded ->
            if (UpdateChecker.compareSemver(downloaded, current) <= 0) {
                clearDownload()
                downloadedApkPath = null
            }
        }
    }

    companion object {
        private const val KEY_CHANNEL = "channel"
        private const val KEY_AUTO_CHECK = "auto_check"
        private const val KEY_PENDING_VERSION = "pending_version"
        private const val KEY_PENDING_URL = "pending_url"
        private const val KEY_PENDING_RELEASE_NOTES = "pending_release_notes"
        private const val KEY_PENDING_RELEASE_TITLE = "pending_release_title"
        private const val KEY_PENDING_NO_WIFI = "pending_no_wifi"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_DOWNLOADED_APK = "downloaded_apk"
        private const val KEY_HAS_INSTALLED = "has_installed_update"
        private const val KEY_DOWNLOADED_VERSION = "downloaded_version"
        private const val KEY_SHEET_DISMISSED_UNTIL = "sheet_dismissed_until"
    }
}
