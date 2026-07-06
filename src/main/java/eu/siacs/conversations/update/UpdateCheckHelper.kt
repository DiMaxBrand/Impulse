package eu.siacs.conversations.update

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import eu.siacs.conversations.ui.UpdateSheetFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

object UpdateCheckHelper {

    private var checked = false

    @JvmStatic
    fun runIfNeeded(activity: AppCompatActivity) {
        if (checked) return
        checked = true
        val prefs = UpdatePreferences(activity.applicationContext)
        if (!prefs.autoCheck) return
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    UpdateChecker(OkHttpClient()).checkForUpdate(prefs.selectedChannel)
                } catch (_: Exception) {
                    null
                }
            } ?: return@launch
            if (result is UpdateChecker.CheckResult.UpdateAvailable) {
                val info = result.info
                prefs.pendingUpdateVersion = info.versionName
                prefs.pendingUpdateUrl = info.downloadUrl
                // Mirror the "Check Now" flow so the sheet never lands in the dead IDLE phase
                // (no download/install button at all) it would otherwise get from
                // UpdateSheetFragment.initState() when neither pendingNoWifi nor an already-
                // downloaded APK apply: on wifi, start the download in the background so it's
                // ready without the user needing to open Updates and tap Check Now themselves;
                // off wifi, mark it pending so the sheet shows the manual Download button.
                if (info.versionName == prefs.downloadedVersion && prefs.downloadedApkExists()) {
                    // Already downloaded in full — sheet will show READY via initState().
                } else if (UpdateDownloader.isWifiConnected(activity)) {
                    prefs.pendingNoWifi = false
                    prefs.activeDownloadId = UpdateDownloader.startDownload(activity, info)
                } else {
                    prefs.pendingNoWifi = true
                }
                val fm = activity.supportFragmentManager
                if (!activity.isFinishing && !fm.isStateSaved && UpdateSheetFragment.shouldShow(activity)) {
                    if (fm.findFragmentByTag(UpdateSheetFragment.TAG) == null) {
                        UpdateSheetFragment().show(fm, UpdateSheetFragment.TAG)
                    }
                }
            }
        }
    }
}
