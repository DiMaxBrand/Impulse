package eu.siacs.conversations.ui.activity

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import eu.siacs.conversations.BuildConfig
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.ActionBarActivity
import eu.siacs.conversations.ui.CheckStatus
import eu.siacs.conversations.ui.DownloadPhase
import eu.siacs.conversations.ui.ImpulseExpressiveTheme
import eu.siacs.conversations.ui.UpdatesScreen
import eu.siacs.conversations.ui.UpdatesUiState
import eu.siacs.conversations.update.DownloadEtaTracker
import eu.siacs.conversations.update.UpdateChecker
import eu.siacs.conversations.update.UpdateDownloader
import eu.siacs.conversations.update.UpdateInfo
import eu.siacs.conversations.update.UpdatePreferences
import eu.siacs.conversations.update.formatSpeedAndEta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class UpdatesActivity : ActionBarActivity() {

    private val prefs by lazy { UpdatePreferences(this) }
    private var uiState by mutableStateOf(UpdatesUiState())
    private var pendingInfo: UpdateInfo? = null

    @OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initState()

        setContent {
            ImpulseExpressiveTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.updates_screen_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_arrow_back_24dp),
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                    },
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        UpdatesScreen(
                            state = uiState,
                            onChannelSelected = { channel ->
                                prefs.selectedChannel = channel
                                uiState = uiState.copy(selectedChannel = channel)
                            },
                            onAutoCheckToggled = { enabled ->
                                prefs.autoCheck = enabled
                                uiState = uiState.copy(autoCheck = enabled)
                            },
                            onCheckNow = { triggerManualCheck() },
                            onDownload = { startUserDownload() },
                            onStop = { cancelDownload() },
                            onContinue = { uiState = uiState.copy(cancelConfirmVisible = false) },
                            onDownloadCircleTapped = { uiState = uiState.copy(cancelConfirmVisible = true) },
                            onInstall = {
                                uiState = uiState.copy(showInstallCard = true)
                            },
                            onConfirmInstall = {
                                val path = prefs.downloadedApkPath ?: return@UpdatesScreen
                                prefs.hasInstalledUpdate = true
                                UpdateDownloader.installApk(this@UpdatesActivity, path)
                            },
                            onHideUpdateSheet = {
                                uiState = uiState.copy(showUpdateSheet = false)
                            },
                        )
                    }
                }
            }
        }

        resumeActiveDownload()
    }

    private fun initState() {
        val rawVersion = BuildConfig.VERSION_NAME
        val currentVersion = UpdateChecker.stripBuildMeta(rawVersion)
        val downloadedPath = prefs.downloadedApkPath
        val pendingVersion = prefs.pendingUpdateVersion
        val canInstallDirectly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else true

        val apkExists = prefs.downloadedApkExists() && UpdateChecker.isNewerThanInstalled(prefs.downloadedVersion)
        if (downloadedPath != null && !apkExists) prefs.clearDownload()

        val restoredPhase = when {
            apkExists -> DownloadPhase.READY
            prefs.pendingNoWifi && pendingVersion != null -> DownloadPhase.NO_WIFI_PENDING
            else -> DownloadPhase.IDLE
        }
        uiState = uiState.copy(
            currentVersion = currentVersion,
            selectedChannel = prefs.selectedChannel,
            autoCheck = prefs.autoCheck,
            downloadPhase = restoredPhase,
            pendingVersion = pendingVersion ?: if (restoredPhase == DownloadPhase.READY) prefs.downloadedVersion else null,
            releaseNotes = prefs.pendingReleaseNotes,
            releaseTitle = prefs.pendingReleaseTitle,
            canInstallDirectly = canInstallDirectly,
            isFirstUpdate = !prefs.hasInstalledUpdate,
            showUpdateSheet = restoredPhase != DownloadPhase.IDLE,
        )
    }

    private fun resumeActiveDownload() {
        val id = prefs.activeDownloadId
        if (id == -1L || uiState.downloadPhase == DownloadPhase.READY) return
        uiState = uiState.copy(downloadPhase = DownloadPhase.DOWNLOADING, showUpdateSheet = true)
        pollDownload(id)
    }

    private fun triggerManualCheck() {
        if (uiState.checkStatus == CheckStatus.CHECKING) return
        uiState = uiState.copy(checkStatus = CheckStatus.CHECKING)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                UpdateChecker(OkHttpClient()).checkForUpdate(uiState.selectedChannel)
            }
            when (result) {
                is UpdateChecker.CheckResult.UpToDate ->
                    uiState = uiState.copy(checkStatus = CheckStatus.UP_TO_DATE)
                is UpdateChecker.CheckResult.ChannelBehind ->
                    uiState = uiState.copy(checkStatus = CheckStatus.CHANNEL_BEHIND)
                is UpdateChecker.CheckResult.UpdateAvailable -> {
                    val info = result.info
                    // A different version's download can still be in flight in the background
                    // (e.g. downloaded rc.104, then Check Now found rc.105 before it finished) —
                    // starting a second download without cancelling the first would leave two
                    // pollDownload loops racing to write prefs.downloadedVersion/downloadedApkPath,
                    // and whichever download happens to finish first would stamp its file with
                    // whatever version is currently in prefs.pendingUpdateVersion at that moment
                    // (already overwritten below to the new version) — a real file/version
                    // mismatch, not just a display glitch. Cancel the stale one first.
                    if (uiState.downloadPhase == DownloadPhase.DOWNLOADING &&
                        prefs.pendingUpdateVersion != null &&
                        prefs.pendingUpdateVersion != info.versionName
                    ) {
                        withContext(Dispatchers.IO) {
                            UpdateDownloader.cancelDownload(this@UpdatesActivity, prefs.activeDownloadId)
                        }
                        prefs.activeDownloadId = -1L
                    }
                    pendingInfo = info
                    prefs.pendingUpdateVersion = info.versionName
                    prefs.pendingUpdateUrl = info.downloadUrl
                    prefs.pendingReleaseNotes = info.releaseNotes
                    prefs.pendingReleaseTitle = info.releaseTitle
                    if (info.versionName == prefs.downloadedVersion && prefs.downloadedApkExists()) {
                        // Already downloaded in full — go straight to install, don't refetch.
                        uiState = uiState.copy(
                            checkStatus = CheckStatus.UPDATE_AVAILABLE,
                            downloadPhase = DownloadPhase.READY,
                            pendingVersion = info.versionName,
                            releaseNotes = info.releaseNotes,
                            releaseTitle = info.releaseTitle,
                            showUpdateSheet = true,
                        )
                    } else if (UpdateDownloader.isWifiConnected(this@UpdatesActivity)) {
                        prefs.pendingNoWifi = false
                        uiState = uiState.copy(
                            checkStatus = CheckStatus.UPDATE_AVAILABLE,
                            pendingVersion = info.versionName,
                            releaseNotes = info.releaseNotes,
                            releaseTitle = info.releaseTitle,
                            showUpdateSheet = true,
                        )
                        startUserDownload()
                    } else {
                        prefs.pendingNoWifi = true
                        uiState = uiState.copy(
                            checkStatus = CheckStatus.UPDATE_AVAILABLE,
                            downloadPhase = DownloadPhase.NO_WIFI_PENDING,
                            pendingVersion = info.versionName,
                            releaseNotes = info.releaseNotes,
                            releaseTitle = info.releaseTitle,
                            showUpdateSheet = true,
                        )
                    }
                }
            }
        }
    }

    private fun startUserDownload() {
        val info = pendingInfo ?: run {
            val url = prefs.pendingUpdateUrl ?: return
            val version = prefs.pendingUpdateVersion ?: return
            eu.siacs.conversations.update.UpdateInfo(
                versionName = version,
                channel = prefs.selectedChannel,
                downloadUrl = url,
                releaseNotes = prefs.pendingReleaseNotes ?: "",
                releaseTitle = prefs.pendingReleaseTitle ?: "",
            )
        }
        val id = UpdateDownloader.startDownload(this, info)
        prefs.activeDownloadId = id
        prefs.pendingNoWifi = false
        // Starting a download is itself engagement — clear any earlier dismiss cooldown so a
        // stale one from a previous session can't suppress the sheet reporting on this download.
        prefs.sheetDismissedUntil = 0L
        uiState = uiState.copy(
            downloadPhase = DownloadPhase.DOWNLOADING,
            downloadProgress = 0f,
            showUpdateSheet = true,
        )
        pollDownload(id)
    }

    private fun pollDownload(id: Long) {
        lifecycleScope.launch {
            val etaTracker = DownloadEtaTracker()
            while (true) {
                // Another download may have superseded this one (see triggerManualCheck) since
                // the last iteration — stop touching shared prefs/uiState for a download that
                // isn't the one Impulse is tracking anymore.
                if (prefs.activeDownloadId != id) return@launch
                val progress = withContext(Dispatchers.IO) {
                    UpdateDownloader.queryProgress(this@UpdatesActivity, id)
                }
                if (prefs.activeDownloadId != id) return@launch
                when (progress) {
                    is UpdateDownloader.DownloadProgress.InProgress -> {
                        // Only STATUS_RUNNING clears statusText to null — paused/queued states
                        // set it, which doubles as our signal that bytes aren't actually moving
                        // right now, so speed/ETA would be meaningless.
                        val sampled = etaTracker.sample(
                            nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                            downloadedBytes = progress.downloadedBytes,
                            totalBytes = progress.totalBytes,
                            activelyRunning = progress.statusText == null,
                        )
                        val speedText = sampled?.let { (bps, eta) -> formatSpeedAndEta(this@UpdatesActivity, bps, eta) }
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
                        // Brief processing moment before showing install button
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
                    UpdateDownloader.cancelDownload(this@UpdatesActivity, id)
                }
            }
            prefs.activeDownloadId = -1L
            prefs.clearPending()
            delay(600)
            finish()
        }
    }
}
