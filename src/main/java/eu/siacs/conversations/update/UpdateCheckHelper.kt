package eu.siacs.conversations.update

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

object UpdateCheckHelper {

    /** Runs an extra check on app launch, but only for beta/alpha — those channels move fast
     * enough that waiting for the next scheduled UpdateCheckWorker run (once daily, 10:00
     * local) could mean a stale wait if the device wasn't in use at that time. Stable/RC users
     * are not bothered on every launch; they rely solely on the daily background check.
     *
     * [onChecked], if given, runs once the async network check completes (on the main thread,
     * only while this activity is still alive — lifecycleScope cancels it otherwise). The caller
     * is expected to also do its own immediate pending-state check right after calling this, for
     * whatever was already pending from an earlier check; onChecked exists specifically to catch
     * a version detected by *this* check, which the immediate one can't see yet since the network
     * fetch hasn't returned by the time this method itself returns. */
    @JvmStatic
    @JvmOverloads
    fun checkOnLaunchIfEligible(activity: AppCompatActivity, onChecked: Runnable? = null) {
        val prefs = UpdatePreferences(activity.applicationContext)
        val channel = prefs.selectedChannel
        if (channel != UpdateChannel.BETA && channel != UpdateChannel.ALPHA) return
        activity.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                performCheck(activity.applicationContext)
            }
            onChecked?.run()
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
        // Self-heal here too, not just at process startup (Conversations.kt's Application.onCreate):
        // without this, a device where the process never fully cold-starts between an update
        // actually landing and the next automatic check is stuck re-offering an install for a
        // version it's already running — a live check finding nothing newer never reconciled the
        // leftover pending/downloaded state either, since it early-returns below. Safe here
        // specifically because performCheck() is only ever reached from genuinely automatic checks
        // (UpdateCheckWorker, checkOnLaunchIfEligible), never right after the developer-options
        // manual version picker.
        prefs.clearIfNotNewerThan(eu.siacs.conversations.BuildConfig.VERSION_NAME)
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
        prefs.pendingReleaseNotes = info.releaseNotes
        prefs.pendingReleaseTitle = info.releaseTitle

        when {
            info.versionName == prefs.downloadedVersion && prefs.downloadedApkExists() -> {
                // Already downloaded in full — sheet will show READY via initState(), nothing to do.
            }
            prefs.activeDownloadId != -1L -> {
                // A download from an earlier check is still in flight — wait for it instead of
                // wiping and restarting, which is what caused the redownload/renotify loop this
                // guards against.
                awaitDownload(context, prefs, prefs.activeDownloadId)
            }
            UpdateDownloader.isWifiConnected(context) -> {
                prefs.pendingNoWifi = false
                val id = UpdateDownloader.startDownload(context, info)
                prefs.activeDownloadId = id
                awaitDownload(context, prefs, id)
            }
            else -> prefs.pendingNoWifi = true
        }
    }

    // Blocks (this whole function is already documented as blocking / off-main-thread) until the
    // download finishes or a generous timeout elapses, persisting state exactly like
    // UpdateSheetFragment.pollDownload()'s Complete branch does. This is what actually marks a
    // version as downloaded — without it, a headless (worker- or launch-triggered) download would
    // finish inside DownloadManager with prefs never updated, so the NEXT check's dedup guard
    // (downloadedVersion == info.versionName && downloadedApkExists()) would keep failing and
    // wipe + restart the same download indefinitely.
    private fun awaitDownload(context: Context, prefs: UpdatePreferences, downloadId: Long) {
        val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)
        while (System.currentTimeMillis() < deadline) {
            when (val progress = UpdateDownloader.queryProgress(context, downloadId)) {
                is UpdateDownloader.DownloadProgress.Complete -> {
                    prefs.downloadedVersion = prefs.pendingUpdateVersion
                    prefs.downloadedApkPath = progress.localUri
                    prefs.activeDownloadId = -1L
                    prefs.clearPending()
                    return
                }
                is UpdateDownloader.DownloadProgress.Failed -> {
                    prefs.activeDownloadId = -1L
                    return
                }
                else -> Unit
            }
            Thread.sleep(1000)
        }
        // Timed out waiting — leave activeDownloadId set so it's resumed (by the sheet, or the
        // "already in flight" branch above on the next check) instead of wiped and restarted.
    }
}
