package eu.siacs.conversations.update

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

object UpdateCheckHelper {

    /** Runs an extra check on app launch, but only for beta/alpha — those channels move fast
     * enough that waiting for the next scheduled UpdateCheckWorker run (once daily, 10:00
     * local) could mean a stale wait if the device wasn't in use at that time. Stable/RC users
     * are not bothered on every launch; they rely solely on the daily background check. */
    @JvmStatic
    fun checkOnLaunchIfEligible(activity: AppCompatActivity) {
        val prefs = UpdatePreferences(activity.applicationContext)
        val channel = prefs.selectedChannel
        if (channel != UpdateChannel.BETA && channel != UpdateChannel.ALPHA) return
        activity.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                performCheck(activity.applicationContext)
            }
        }
    }

    /** Checks for an update and updates prefs accordingly (auto-downloads on wifi, or marks it
     * pending so the sheet shows a manual Download button otherwise). Pure logic, no UI/Activity
     * coupling — shared between [UpdateCheckWorker] and [checkOnLaunchIfEligible]. The sheet
     * itself is shown separately, driven by this prefs state (ConversationsActivity's
     * maybeShowUpdateSheet() / UpdateSheetFragment.shouldShow()). Blocking — call off the main
     * thread. */
    fun performCheck(context: Context) {
        val prefs = UpdatePreferences(context)
        if (!prefs.autoCheck) return
        val result = try {
            UpdateChecker(OkHttpClient()).checkForUpdate(prefs.selectedChannel)
        } catch (_: Exception) {
            null
        } ?: return
        if (result !is UpdateChecker.CheckResult.UpdateAvailable) return
        val info = result.info

        prefs.pendingUpdateVersion = info.versionName
        prefs.pendingUpdateUrl = info.downloadUrl

        if (info.versionName == prefs.downloadedVersion && prefs.downloadedApkExists()) {
            // Already downloaded in full — sheet will show READY via initState(), nothing to do.
        } else if (UpdateDownloader.isWifiConnected(context)) {
            prefs.pendingNoWifi = false
            prefs.activeDownloadId = UpdateDownloader.startDownload(context, info)
        } else {
            prefs.pendingNoWifi = true
        }
    }
}
