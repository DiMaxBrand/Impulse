package eu.siacs.conversations.ui

import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
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
import eu.siacs.conversations.update.UpdateDownloader
import eu.siacs.conversations.update.UpdateInfo
import eu.siacs.conversations.update.UpdatePreferences
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
        if (!installInitiated) {
            prefs.sheetDismissedUntil = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
        }
    }

    private fun initState() {
        val downloadedPath = prefs.downloadedApkPath
        val pendingVersion = prefs.pendingUpdateVersion
        val canInstallDirectly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireActivity().packageManager.canRequestPackageInstalls()
        } else true

        val apkExists = prefs.downloadedApkExists()
        if (downloadedPath != null && !apkExists) prefs.downloadedApkPath = null

        val restoredPhase = when {
            apkExists -> DownloadPhase.READY
            prefs.pendingNoWifi && pendingVersion != null -> DownloadPhase.NO_WIFI_PENDING
            else -> DownloadPhase.IDLE
        }
        uiState = uiState.copy(
            pendingVersion = pendingVersion ?: if (restoredPhase == DownloadPhase.READY) prefs.downloadedVersion else null,
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
            releaseNotes = "",
        )
        val id = UpdateDownloader.startDownload(requireActivity(), info)
        prefs.activeDownloadId = id
        prefs.pendingNoWifi = false
        uiState = uiState.copy(
            downloadPhase = DownloadPhase.DOWNLOADING,
            downloadProgress = 0f,
        )
        pollDownload(id)
    }

    private fun pollDownload(id: Long) {
        lifecycleScope.launch {
            while (true) {
                val progress = withContext(Dispatchers.IO) {
                    UpdateDownloader.queryProgress(requireContext(), id)
                }
                when (progress) {
                    is UpdateDownloader.DownloadProgress.InProgress ->
                        uiState = uiState.copy(
                            downloadPhase = DownloadPhase.DOWNLOADING,
                            downloadProgress = progress.fraction,
                        )
                    is UpdateDownloader.DownloadProgress.Complete -> {
                        uiState = uiState.copy(downloadPhase = DownloadPhase.PROCESSING)
                        prefs.downloadedVersion = prefs.pendingUpdateVersion
                        prefs.downloadedApkPath = progress.localUri
                        prefs.activeDownloadId = -1L
                        prefs.clearPending()
                        delay(800)
                        uiState = uiState.copy(downloadPhase = DownloadPhase.READY)
                        break
                    }
                    is UpdateDownloader.DownloadProgress.Failed -> {
                        uiState = uiState.copy(downloadPhase = DownloadPhase.IDLE)
                        prefs.activeDownloadId = -1L
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
            if (prefs.pendingUpdateVersion == null && prefs.downloadedApkPath == null) return false
            return System.currentTimeMillis() > prefs.sheetDismissedUntil
        }
    }
}
