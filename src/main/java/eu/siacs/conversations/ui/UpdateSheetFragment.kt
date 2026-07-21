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
import eu.siacs.conversations.update.UpdateDownloader
import eu.siacs.conversations.update.UpdateInfo
import eu.siacs.conversations.update.UpdatePreferences
import eu.siacs.conversations.update.formatSpeedAndEta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateSheetFragment : BottomSheetDialogFragment() {

    private val prefs by lazy { UpdatePreferences(requireContext()) }
    private var uiState by mutableStateOf(UpdatesUiState())
    private var installInitiated = false

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

        val apkExists = prefs.downloadedApkExists()
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
        )
        pollDownload(id)
    }

    private fun pollDownload(id: Long) {
        lifecycleScope.launch {
            val etaTracker = DownloadEtaTracker()
            while (true) {
                val progress = withContext(Dispatchers.IO) {
                    UpdateDownloader.queryProgress(requireContext(), id)
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

    private fun cancelDownload() {
        val id = prefs.activeDownloadId
        uiState = uiState.copy(
            downloadPhase = DownloadPhase.CANCELING,
            cancelConfirmVisible = false,
        )
        lifecycleScope.launch {
            if (id != -1L) {
                withContext(Dispatchers.IO) {
                    UpdateDownloader.cancelDownload(requireContext(), id)
                }
            }
            prefs.activeDownloadId = -1L
            prefs.clearPending()
            delay(600)
            dismiss()
        }
    }

    companion object {
        const val TAG = "update_sheet"

        @JvmStatic
        fun shouldShow(context: Context): Boolean {
            val prefs = UpdatePreferences(context)
            // A non-null downloadedApkPath isn't enough on its own — the file it points to can
            // go missing behind our back (the nightly ApkCleanupWorker runs on an independent
            // schedule from the download check, so it can race an in-progress/just-finished
            // download; OS-level storage cleanup is another way). initState() already self-heals
            // this, but only after the sheet is already showing, which is how you'd get a sheet
            // with no content at all instead of no sheet. Check here too so it never opens for a
            // download that isn't actually there.
            val hasDownloaded = prefs.downloadedApkPath != null && prefs.downloadedApkExists()
            if (prefs.pendingUpdateVersion == null && !hasDownloaded) return false
            return System.currentTimeMillis() > prefs.sheetDismissedUntil
        }
    }
}
