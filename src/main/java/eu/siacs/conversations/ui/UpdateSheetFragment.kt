package eu.siacs.conversations.ui

import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import eu.siacs.conversations.update.DownloadEtaTracker
import eu.siacs.conversations.update.UpdateChecker
import eu.siacs.conversations.update.UpdateDownloader
import eu.siacs.conversations.update.UpdateInfo
import eu.siacs.conversations.update.UpdatePreferences
import eu.siacs.conversations.update.formatSpeedAndEta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateSheetFragment : BottomSheetDialogFragment() {

    private val prefs by lazy { UpdatePreferences(requireContext()) }
    private var uiState by mutableStateOf(UpdatesUiState())
    private var installInitiated = false
    // At most one poll loop alive at a time — resumeActiveDownload() (onViewCreated) and
    // startUserDownload() (a later user tap) both call pollDownload(); without this, a second
    // call while the first is still looping starts a genuine duplicate — two coroutines both
    // polling the same download and writing uiState, one of which can keep looping (and
    // overwriting uiState back to DOWNLOADING with a stale progress read) even after the other
    // already reached Complete/READY and quit cleanly.
    private var pollJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ImpulseExpressiveTheme {
                UpdateSheetContent(
                    state = uiState,
                    onDownload = ::startUserDownload,
                    onStop = ::cancelDownload,
                    onContinue = { uiState = uiState.copy(cancelConfirmVisible = false) },
                    onInstall = { uiState = uiState.copy(showInstallCard = true) },
                    onConfirmInstall = {
                        val path = prefs.downloadedApkPath ?: return@UpdateSheetContent
                        installInitiated = true
                        prefs.hasInstalledUpdate = true
                        UpdateDownloader.installApk(requireActivity(), path)
                    },
                    onDownloadCircleTapped = {
                        uiState = uiState.copy(cancelConfirmVisible = true)
                    },
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initState()
        resumeActiveDownload()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (installInitiated) return
        when (uiState.downloadPhase) {
            // Actively in progress — dismissing here just closes the progress view, it isn't
            // the user declining the update, so don't suppress the sheet from reappearing.
            DownloadPhase.DOWNLOADING, DownloadPhase.PROCESSING, DownloadPhase.CANCELING -> Unit
            // Already downloaded, one tap from installing — a short cooldown, not a full day.
            DownloadPhase.READY ->
                prefs.sheetDismissedUntil = System.currentTimeMillis() + 60 * 60 * 1000
            DownloadPhase.IDLE, DownloadPhase.NO_WIFI_PENDING ->
                prefs.sheetDismissedUntil = System.currentTimeMillis() + 24 * 60 * 60 * 1000
        }
    }

    /** Re-reads prefs into uiState — called by ConversationsActivity when a check completes while
     * the sheet is already showing, so a title/version written *after* the sheet was first shown
     * (e.g. this fragment was created from a synchronous re-show before the async launch check
     * that's about to update prefs had returned) doesn't get stuck stale until the fragment is
     * torn down and recreated (e.g. by navigating away and back). */
    fun refresh() {
        if (view != null) initState()
    }

    private fun initState() {
        val downloadedPath = prefs.downloadedApkPath
        val pendingVersion = prefs.pendingUpdateVersion
        val canInstallDirectly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireActivity().packageManager.canRequestPackageInstalls()
        } else true

        val apkExists = prefs.downloadedApkExists() && UpdateChecker.isNewerThanInstalled(prefs.downloadedVersion)
        if (downloadedPath != null && !apkExists) prefs.clearDownload()

        val restoredPhase = when {
            apkExists -> DownloadPhase.READY
            prefs.pendingNoWifi && pendingVersion != null -> DownloadPhase.NO_WIFI_PENDING
            else -> DownloadPhase.IDLE
        }
        uiState = uiState.copy(
            pendingVersion = pendingVersion ?: if (restoredPhase == DownloadPhase.READY) prefs.downloadedVersion else null,
            releaseNotes = prefs.pendingReleaseNotes,
            releaseTitle = prefs.pendingReleaseTitle,
            downloadPhase = restoredPhase,
            canInstallDirectly = canInstallDirectly,
            isFirstUpdate = !prefs.hasInstalledUpdate,
            // restoredPhase is never DOWNLOADING here (only resumeActiveDownload() sets that, and
            // its own poll loop refreshes these within moments) — so these two are always stale
            // leftovers from whatever the *previous* DOWNLOADING stretch last wrote, on any call
            // into initState()/refresh(). Without this, dismissing the sheet mid-download and
            // reopening it (e.g. via Check Now) could show a correct "ready to install" button
            // with a frozen "1.8 MB/s · 3s left" line still sitting above it forever, since
            // nothing else was left to clear text fields this function never otherwise touches.
            downloadStatusText = null,
            downloadSpeedText = null,
        )
    }

    private fun resumeActiveDownload() {
        val id = prefs.activeDownloadId
        if (id == -1L || uiState.downloadPhase == DownloadPhase.READY) return
        uiState = uiState.copy(downloadPhase = DownloadPhase.DOWNLOADING)
        pollDownload(id)
    }

    private fun startUserDownload() {
        val url = prefs.pendingUpdateUrl ?: return
        val version = prefs.pendingUpdateVersion ?: return
        val info = UpdateInfo(
            versionName = version,
            channel = prefs.selectedChannel,
            downloadUrl = url,
            releaseNotes = prefs.pendingReleaseNotes ?: "",
            releaseTitle = prefs.pendingReleaseTitle ?: "",
        )
        val id = UpdateDownloader.startDownload(requireActivity(), info)
        prefs.activeDownloadId = id
        prefs.pendingNoWifi = false
        // Starting a download is itself engagement — clear any earlier dismiss cooldown so a
        // stale one from a previous session can't suppress the sheet reporting on this download.
        prefs.sheetDismissedUntil = 0L
        uiState = uiState.copy(
            downloadPhase = DownloadPhase.DOWNLOADING,
            downloadProgress = 0f,
            // A fresh download start shouldn't briefly show whatever speed/ETA text a previous
            // attempt left behind before the first poll tick overwrites it.
            downloadStatusText = null,
            downloadSpeedText = null,
        )
        pollDownload(id)
    }

    private fun pollDownload(id: Long) {
        val trackedVersion = prefs.pendingUpdateVersion
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            val etaTracker = DownloadEtaTracker()
            while (true) {
                // activeDownloadId no longer pointing at this id has two different causes, not
                // one: a genuinely different download superseded this one (e.g. Check Now from
                // the Updates screen while this sheet's own download was in flight) — nothing to
                // show, just stop; or this *exact* download simply finished via a concurrent
                // watcher instead of this loop (UpdateCheckHelper.awaitDownload(), which the
                // beta/alpha on-launch background check runs independently and can race this one
                // to the same id's completion). Silently bailing out either way used to leave the
                // sheet stuck showing DOWNLOADING with a frozen progress/ETA forever in the second
                // case, since nothing else was left to move it on to READY.
                if (prefs.activeDownloadId != id) {
                    resolveIfFinishedElsewhere(trackedVersion)
                    return@launch
                }
                val progress = withContext(Dispatchers.IO) {
                    UpdateDownloader.queryProgress(requireContext(), id)
                }
                if (prefs.activeDownloadId != id) {
                    resolveIfFinishedElsewhere(trackedVersion)
                    return@launch
                }
                when (progress) {
                    is UpdateDownloader.DownloadProgress.InProgress -> {
                        val sampled = etaTracker.sample(
                            nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                            downloadedBytes = progress.downloadedBytes,
                            totalBytes = progress.totalBytes,
                            activelyRunning = progress.statusText == null,
                        )
                        val speedText = sampled?.let { (bps, eta) -> formatSpeedAndEta(requireContext(), bps, eta) }
                        uiState = uiState.copy(
                            downloadPhase = DownloadPhase.DOWNLOADING,
                            downloadProgress = progress.fraction,
                            downloadStatusText = progress.statusText,
                            downloadSpeedText = speedText,
                        )
                    }
                    is UpdateDownloader.DownloadProgress.Complete -> {
                        uiState = uiState.copy(
                            downloadPhase = DownloadPhase.PROCESSING,
                            downloadStatusText = null,
                            downloadSpeedText = null,
                        )
                        prefs.downloadedVersion = prefs.pendingUpdateVersion
                        prefs.downloadedApkPath = progress.localUri
                        prefs.activeDownloadId = -1L
                        prefs.clearPending()
                        delay(800)
                        uiState = uiState.copy(downloadPhase = DownloadPhase.READY)
                        break
                    }
                    is UpdateDownloader.DownloadProgress.Failed -> {
                        uiState = uiState.copy(
                            downloadPhase = DownloadPhase.DOWNLOADING,
                            downloadStatusText = progress.reasonText,
                            downloadSpeedText = null,
                        )
                        prefs.activeDownloadId = -1L
                        delay(4000)
                        uiState = uiState.copy(downloadPhase = DownloadPhase.IDLE, downloadStatusText = null)
                        break
                    }
                    else -> Unit
                }
                delay(500)
            }
        }
    }

    /** Called once this loop notices prefs.activeDownloadId no longer points at the download it
     * was tracking — if that's because [trackedVersion] itself already finished (stamped by
     * whichever watcher won the race, see pollDownload()'s comment), move the UI on to READY
     * instead of leaving it abandoned mid-DOWNLOADING. A genuinely different/newer download
     * having taken over is a no-op here (downloadedVersion won't match), same as before. */
    private fun resolveIfFinishedElsewhere(trackedVersion: String?) {
        if (trackedVersion != null &&
            trackedVersion == prefs.downloadedVersion &&
            prefs.downloadedApkExists()
        ) {
            uiState = uiState.copy(
                downloadPhase = DownloadPhase.READY,
                downloadStatusText = null,
                downloadSpeedText = null,
            )
        }
    }

    private fun cancelDownload() {
        val id = prefs.activeDownloadId
        uiState = uiState.copy(
            downloadPhase = DownloadPhase.CANCELING,
            cancelConfirmVisible = false,
            // Otherwise the last-seen speed/ETA line lingers on screen underneath the cancel
            // animation instead of disappearing the moment cancellation starts.
            downloadStatusText = null,
            downloadSpeedText = null,
        )
        // Mark this id as no longer active *before* doing the actual (I/O, non-instant) cancel
        // work below, not after — pollDownload()'s loop is still running concurrently and only
        // stops touching uiState once it sees activeDownloadId change. With the old ordering,
        // there was a real window where pollDownload's own in-flight poll could complete and
        // overwrite downloadPhase back to DOWNLOADING with a fresh progress read, right on top of
        // the CANCELING state just set above — a visible flicker back to the download circle
        // before the cancel animation reappeared a moment later.
        prefs.activeDownloadId = -1L
        prefs.clearPending()
        lifecycleScope.launch {
            val enteredCancelingAt = SystemClock.elapsedRealtime()
            if (id != -1L) {
                withContext(Dispatchers.IO) {
                    UpdateDownloader.cancelDownload(requireContext(), id)
                }
            }
            // DownloadManager.remove() above doesn't wait on anything — the actual cancel work is
            // near-instant, so without a floor here the CANCELING phase (and its shape-morph)
            // would flash for a fraction of a second and dismiss before it's even legible. Only
            // ever adds delay, never cuts short — if cancellation genuinely took longer than the
            // floor on its own, this is a no-op.
            val elapsed = SystemClock.elapsedRealtime() - enteredCancelingAt
            val remaining = MIN_CANCELING_DISPLAY_MS - elapsed
            if (remaining > 0) delay(remaining)
            dismiss()
        }
    }

    companion object {
        const val TAG = "update_sheet"

        // Floor for how long DownloadPhase.CANCELING stays visible — see cancelDownload().
        private const val MIN_CANCELING_DISPLAY_MS = 3000L

        @JvmStatic
        fun shouldShow(context: Context): Boolean {
            val prefs = UpdatePreferences(context)
            // A non-null downloadedApkPath isn't enough on its own — the file it points to can
            // go missing behind our back (the nightly ApkCleanupWorker runs on an independent
            // schedule from the download check, so it can race an in-progress/just-finished
            // download; OS-level storage cleanup is another way). initState() already self-heals
            // this, but only after the sheet is already showing, which is how you'd get a sheet
            // with no content at all instead of no sheet. Check here too so it never opens for a
            // download that isn't actually there. Also require the version to still actually be
            // newer than what's installed — see UpdateChecker.isNewerThanInstalled().
            val hasDownloaded = prefs.downloadedApkPath != null &&
                prefs.downloadedApkExists() &&
                UpdateChecker.isNewerThanInstalled(prefs.downloadedVersion)
            val hasPending = UpdateChecker.isNewerThanInstalled(prefs.pendingUpdateVersion)
            if (!hasPending && !hasDownloaded) return false
            return System.currentTimeMillis() > prefs.sheetDismissedUntil
        }
    }
}
