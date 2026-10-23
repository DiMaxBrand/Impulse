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

    private const val LAUNCH_CHECK_MIN_INTERVAL_MS = 5 * 60 * 1000L

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
        // Beta and alpha move fast. Any channel also checks on launch while support is tracking
        // something for this person (a fix or a feature they are waiting for): they should get it
        // the moment it is out, not at the next nightly run.
        if (channel != UpdateChannel.BETA &&
            channel != UpdateChannel.ALPHA &&
            !BugReportRegistry(activity.applicationContext).hasWaitingFromSupport()
        ) return
        // At most one launch check per five minutes: onResume fires on every return to the main
        // screen, and GitHub allows about 60 unauthenticated requests an hour per network address
        // (shared by everyone on the same Wi-Fi). A refused request would show up as a failed
        // check and make an update or a bug fix notice late. The manual "Check now" and the
        // nightly run are not affected.
        val now = System.currentTimeMillis()
        if (now - prefs.lastLaunchCheckAt < LAUNCH_CHECK_MIN_INTERVAL_MS) return
        prefs.lastLaunchCheckAt = now
        activity.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                performCheck(activity.applicationContext)
            }
            onChecked?.run()
        }
    }

    /**
     * Marks the bug reports this phone sent as fixed when any release newer than the installed
     * one names their ID, and posts the one-time notification for the newly matched. Shared by
     * the automatic checks and the Updates screen's manual "Check now" -- the manual one used to
     * skip this, so a fix could stay "waiting" however often you pressed it.
     */
    @JvmStatic
    fun matchBugReports(context: Context, releaseNotes: List<UpdateChecker.NewerRelease>) {
        val registry = BugReportRegistry(context)
        val fixed = registry.matchFixed(releaseNotes)
        if (fixed.isNotEmpty()) {
            // A fix named in a version that is already installed is just as true -- say so
            // (and the entry is dropped right below), instead of the "update to get it" wording.
            val installed =
                fixed.filter {
                    val v = it.fixedIn?.let { name -> UpdateChecker.parseVersion(name) }
                    val current =
                        UpdateChecker.parseVersion(
                            UpdateChecker.stripBuildMeta(eu.siacs.conversations.BuildConfig.VERSION_NAME)
                        )
                    v != null && current != null && UpdateChecker.compareSemver(v, current) <= 0
                }
            val pending = fixed - installed.toSet()
            // What the notice names: the title support typed for the item in the phone's language, or
            // the ID for a report this phone sent itself.
            val ru = SupportTracking.isRussian()
            fun names(list: List<BugReportRegistry.Entry>) =
                list.map { it.title(ru).ifBlank { it.id } }
            fun bugsOnly(list: List<BugReportRegistry.Entry>) =
                list.all { it.kind == BugReportRegistry.KIND_BUG }
            if (pending.isNotEmpty()) {
                UpdateNotifications.postBugFixed(
                    context,
                    names(pending),
                    pending.mapNotNull { it.fixedIn }.first(),
                    alreadyInstalled = false,
                    bugsOnly = bugsOnly(pending),
                )
            }
            if (installed.isNotEmpty()) {
                UpdateNotifications.postBugFixed(
                    context,
                    names(installed),
                    installed.mapNotNull { it.fixedIn }.first(),
                    alreadyInstalled = true,
                    bugsOnly = bugsOnly(installed),
                )
            }
        }
        registry.dropInstalled(eu.siacs.conversations.BuildConfig.VERSION_NAME)
    }

    /** Checks for an update and updates prefs accordingly (auto-downloads on wifi, or marks it
     * pending so the sheet shows a manual Download button otherwise). Pure logic, no UI/Activity
     * coupling — shared between [UpdateCheckWorker] and [checkOnLaunchIfEligible]. The sheet
     * itself is shown separately, driven by this prefs state (ConversationsActivity's
     * maybeShowUpdateSheet() / UpdateSheetFragment.shouldShow()). Blocking — call off the main
     * thread. */
    @JvmStatic
    @JvmOverloads
    fun performCheck(context: Context, overnight: Boolean = false, forced: Boolean = false) {
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
        // A fix that is now installed no longer needs to be tracked.
        BugReportRegistry(context).dropInstalled(eu.siacs.conversations.BuildConfig.VERSION_NAME)
        // "Auto-check" is about the daytime checks (and the nudges that follow). The overnight run
        // still checks, downloads and installs while "Update overnight" is on, so a person who
        // switched the nudges off still ends up on the current version in the morning.
        if (!forced && !prefs.autoCheck && !(overnight && prefs.nightlyInstall)) return
        // "Wait between updates": stay completely quiet (no network check, nothing marked pending,
        // no auto-download) until enough time has passed since the app was last updated.
        if (!forced && prefs.isWithinQuietWindow(context)) return
        val checker = UpdateChecker(OkHttpClient())
        val result = try {
            checker.checkForUpdate(prefs.selectedChannel)
        } catch (_: Exception) {
            null
        } ?: return
        // Release notes that name a bug-report ID this phone sent mean that report is fixed.
        // Reads every release on the channel, after ANY outcome of the check (an update
        // available, already up to date ...): the fix may be in a version already installed.
        // A fix not installed yet stays marked "fixed in X" until it is.
        matchBugReports(context, checker.lastReleaseNotes)
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
