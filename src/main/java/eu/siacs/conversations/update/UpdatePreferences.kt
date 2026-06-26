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

    fun clearPending() {
        prefs.edit {
            remove(KEY_PENDING_VERSION)
            remove(KEY_PENDING_URL)
            remove(KEY_PENDING_NO_WIFI)
        }
    }

    fun clearDownload() {
        prefs.edit { remove(KEY_DOWNLOADED_APK) }
    }

    companion object {
        private const val KEY_CHANNEL = "channel"
        private const val KEY_AUTO_CHECK = "auto_check"
        private const val KEY_PENDING_VERSION = "pending_version"
        private const val KEY_PENDING_URL = "pending_url"
        private const val KEY_PENDING_NO_WIFI = "pending_no_wifi"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_DOWNLOADED_APK = "downloaded_apk"
        private const val KEY_HAS_INSTALLED = "has_installed_update"
    }
}
