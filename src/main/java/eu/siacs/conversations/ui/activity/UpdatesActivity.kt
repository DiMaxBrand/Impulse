package eu.siacs.conversations.ui.activity

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.format.Formatter
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
import eu.siacs.conversations.update.UpdateChecker
import eu.siacs.conversations.update.UpdateDownloader
import eu.siacs.conversations.update.UpdateInfo
import eu.siacs.conversations.update.UpdatePreferences
import kotlin.math.roundToLong
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

        val apkExists = prefs.downloadedApkExists()
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
            // Speed comes from a sliding window of the last ~4s of (time, bytes) samples,
            // not a single 500ms delta — individual polls are bursty (the OS flushes to disk
            // in chunks, not a smooth stream), so a single-tick rate genuinely alternates
            // between "burst" and "nothing" every poll or two. Averaging over several real
            // seconds of samples smooths that out at the source.
            val samples = ArrayDeque<Pair<Long, Long>>() // elapsedRealtime ms to downloadedBytes
            val windowMs = 4000L

            // The *displayed* ETA is its own gently-corrected countdown, not a fresh division
            // result redrawn every poll: between polls it ticks down by real elapsed time, then
            // nudges a quarter of the way toward the newly measured estimate. Averaging the
            // displayed number against itself (e.g. new = (old + fresh) / 2) decays
            // geometrically and mathematically never reaches zero; nudging a live countdown
            // that's independently ticking down with real time does reach zero, because actual
            // elapsed seconds are doing the counting, not an infinite series of halvings.
            var displayedEtaSeconds: Double? = null
            var lastPollAt = 0L
            while (true) {
                val progress = withContext(Dispatchers.IO) {
                    UpdateDownloader.queryProgress(this@UpdatesActivity, id)
                }
                when (progress) {
                    is UpdateDownloader.DownloadProgress.InProgress -> {
                        // Only STATUS_RUNNING clears statusText to null — paused/queued states
                        // set it, which doubles as our signal that bytes aren't actually moving
                        // right now, so speed/ETA would be meaningless.
                        val speedText = if (progress.statusText == null && progress.totalBytes > 0) {
                            val now = SystemClock.elapsedRealtime()
                            samples.addLast(now to progress.downloadedBytes)
                            while (samples.isNotEmpty() && now - samples.first().first > windowMs) {
                                samples.removeFirst()
                            }
                            val (oldestAt, oldestBytes) = samples.first()
                            val deltaMs = now - oldestAt
                            val deltaBytes = progress.downloadedBytes - oldestBytes
                            val windowBps = if (deltaMs > 200 && deltaBytes >= 0) {
                                deltaBytes * 1000.0 / deltaMs
                            } else 0.0

                            val elapsedSincePoll = if (lastPollAt != 0L) (now - lastPollAt) / 1000.0 else 0.0
                            lastPollAt = now
                            if (windowBps > 0.0) {
                                val remainingBytes = progress.totalBytes - progress.downloadedBytes
                                val rawEtaSeconds = remainingBytes / windowBps
                                val current = displayedEtaSeconds
                                displayedEtaSeconds = if (current == null) {
                                    rawEtaSeconds
                                } else {
                                    val ticked = (current - elapsedSincePoll).coerceAtLeast(0.0)
                                    ticked + (rawEtaSeconds - ticked) * 0.25
                                }
                                formatSpeedAndEta(windowBps, displayedEtaSeconds!!)
                            } else {
                                null
                            }
                        } else {
                            // Not actively running — reset so a stale rate/countdown doesn't
                            // carry over into the next running stretch (e.g. after pause/resume).
                            samples.clear()
                            displayedEtaSeconds = null
                            lastPollAt = 0L
                            null
                        }
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

    private fun formatSpeedAndEta(bytesPerSecond: Double, etaSecondsRaw: Double): String? {
        if (bytesPerSecond <= 0.0) return null
        val speedText = Formatter.formatShortFileSize(this, bytesPerSecond.toLong()) + "/s"
        // Rounded, not truncated: truncation always displays a smaller number than reality,
        // which reads as the countdown "sticking" a beat longer than it should before dropping.
        val etaSeconds = etaSecondsRaw.roundToLong().coerceAtLeast(0)
        val etaText = when {
            etaSeconds < 60 -> "${etaSeconds}s left"
            etaSeconds < 3600 -> "${etaSeconds / 60}m ${etaSeconds % 60}s left"
            else -> "${etaSeconds / 3600}h ${(etaSeconds % 3600) / 60}m left"
        }
        return "$speedText · $etaText"
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
