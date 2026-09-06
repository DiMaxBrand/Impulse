package eu.siacs.conversations.ui.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import eu.siacs.conversations.ui.UpdateSheetFragment
import eu.siacs.conversations.ui.UpdatesScreen
import eu.siacs.conversations.ui.UpdatesUiState
import eu.siacs.conversations.update.UpdateChecker
import eu.siacs.conversations.update.UpdateDownloader
import eu.siacs.conversations.update.UpdatePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Settings destination for update checking/channel picking. The actual download-progress UI
 * (poll/cancel/install) is no longer duplicated here -- it's delegated entirely to the shared
 * [UpdateSheetFragment], the same one [eu.siacs.conversations.ui.ConversationsActivity] shows
 * elsewhere. This activity used to render its own separate inline `ModalBottomSheet` with its
 * own copy of the poll/init/cancel/resend state machine (near-identical to the Fragment's, down
 * to the comments) -- two independently-maintained copies of the same logic, which is exactly
 * how bugs fixed in one silently stayed broken in the other. Now there is exactly one.
 */
class UpdatesActivity : ActionBarActivity() {

    private val prefs by lazy { UpdatePreferences(this) }
    private var uiState by mutableStateOf(UpdatesUiState())

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
                        )
                    }
                }
            }
        }

        // A download/install already in flight or ready from an earlier session -- surface it
        // via the shared sheet instead of this screen trying to render its own progress UI.
        if (prefs.activeDownloadId != -1L || prefs.downloadedApkExists()) {
            showUpdateSheet()
        }
    }

    override fun onResume() {
        super.onResume()
        // Cheap prefs re-read, not a poll loop -- the sheet Fragment (when shown) owns live
        // download progress entirely on its own. This just keeps this screen's own "new version
        // available" line (mainStatusText in UpdatesScreen.kt) from going stale after returning
        // here, e.g. from the sheet itself, or from the background.
        initState()
    }

    private fun initState() {
        val rawVersion = BuildConfig.VERSION_NAME
        val currentVersion = UpdateChecker.stripBuildMeta(rawVersion)
        val downloadedPath = prefs.downloadedApkPath
        val pendingVersion = prefs.pendingUpdateVersion

        val apkExists = prefs.downloadedApkExists() && UpdateChecker.isNewerThanInstalled(prefs.downloadedVersion)
        if (downloadedPath != null && !apkExists) prefs.clearDownload()

        val restoredPhase = when {
            apkExists -> DownloadPhase.READY
            prefs.activeDownloadId != -1L -> DownloadPhase.DOWNLOADING
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
        )
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
                is UpdateChecker.CheckResult.CheckFailed ->
                    uiState = uiState.copy(checkStatus = CheckStatus.CHECK_FAILED)
                is UpdateChecker.CheckResult.UpdateAvailable -> {
                    val info = result.info
                    // The exact version already actively downloading — nothing to cancel or
                    // restart, just refresh and let it keep going.
                    val alreadyDownloadingThisVersion =
                        prefs.activeDownloadId != -1L && prefs.pendingUpdateVersion == info.versionName
                    // A different version's download can still be in flight in the background
                    // (e.g. downloaded rc.104, then Check Now found rc.105 before it finished) —
                    // starting a second download without cancelling the first would leave two
                    // watchers racing to write prefs.downloadedVersion/downloadedApkPath. Cancel
                    // the stale one first.
                    if (prefs.activeDownloadId != -1L &&
                        prefs.pendingUpdateVersion != null &&
                        prefs.pendingUpdateVersion != info.versionName
                    ) {
                        withContext(Dispatchers.IO) {
                            UpdateDownloader.cancelDownload(this@UpdatesActivity, prefs.activeDownloadId)
                        }
                        prefs.activeDownloadId = -1L
                    }
                    prefs.pendingUpdateVersion = info.versionName
                    prefs.pendingUpdateUrl = info.downloadUrl
                    prefs.pendingReleaseNotes = info.releaseNotes
                    prefs.pendingReleaseTitle = info.releaseTitle

                    if (info.versionName == prefs.downloadedVersion && prefs.downloadedApkExists()) {
                        // Already downloaded in full — the sheet will show READY via its own
                        // initState(), nothing more to do here.
                        uiState = uiState.copy(checkStatus = CheckStatus.UPDATE_AVAILABLE)
                    } else if (alreadyDownloadingThisVersion) {
                        uiState = uiState.copy(checkStatus = CheckStatus.UPDATE_AVAILABLE)
                    } else if (UpdateDownloader.isWifiConnected(this@UpdatesActivity)) {
                        prefs.pendingNoWifi = false
                        // Starting a download is itself engagement — clear any earlier dismiss
                        // cooldown so a stale one from a previous session can't suppress the
                        // sheet reporting on this download.
                        prefs.sheetDismissedUntil = 0L
                        val id = UpdateDownloader.startDownload(this@UpdatesActivity, info)
                        prefs.activeDownloadId = id
                        uiState = uiState.copy(checkStatus = CheckStatus.UPDATE_AVAILABLE)
                    } else {
                        prefs.pendingNoWifi = true
                        uiState = uiState.copy(checkStatus = CheckStatus.UPDATE_AVAILABLE)
                    }
                    initState()
                    showUpdateSheet()
                }
            }
        }
    }

    /** Same idiom as DeveloperOptionsActivity.showUpdateSheet() -- the one Fragment instance,
     * shown wherever it's needed, instead of a separate inline copy per screen. */
    private fun showUpdateSheet() {
        if (isFinishing || supportFragmentManager.isStateSaved) return
        if (supportFragmentManager.findFragmentByTag(UpdateSheetFragment.TAG) == null) {
            UpdateSheetFragment().show(supportFragmentManager, UpdateSheetFragment.TAG)
        }
    }
}
