package eu.siacs.conversations.ui

import android.graphics.Bitmap
import android.net.Uri
import android.content.Intent
import android.content.ActivityNotFoundException
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.security.KeyChain
import android.security.KeyChainAliasCallback
import android.widget.Toast
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.Config
import eu.siacs.conversations.BuildConfig
import eu.siacs.conversations.update.UpdateChannel
import eu.siacs.conversations.update.UpdatePreferences
import eu.siacs.conversations.update.UpdateChecker
import eu.siacs.conversations.update.UpdateDownloader
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.ui.interfaces.OnAvatarPublication
import eu.siacs.conversations.xmpp.manager.PepManager
import eu.siacs.conversations.xmpp.manager.AvatarManager
import im.conversations.android.xmpp.NodeConfiguration
import eu.siacs.conversations.utils.TorServiceUtils
import eu.siacs.conversations.utils.PhoneHelper
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.google.common.util.concurrent.MoreExecutors
import im.conversations.android.xmpp.model.data.Data
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import de.gultsch.common.MiniUri

/** One host for first-run account setup, the tour, updates and notification setup. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class UnifiedOnboardingActivity : QrCodeProcessingActivity(), XmppConnectionService.OnAccountUpdate,
    XmppConnectionService.OnCaptchaRequested, XmppConnectionService.OnAccountCreated,
    XmppConnectionService.OnShowErrorToast, KeyChainAliasCallback, OnAvatarPublication {
    private var creating by mutableStateOf<Boolean?>(null)
    private var registrationDomain by mutableStateOf("on-chat.ru")
    private var preAuth by mutableStateOf<String?>(null)
    private var address by mutableStateOf("")
    private var password by mutableStateOf("")
    private var hostname by mutableStateOf("")
    private var port by mutableStateOf("5222")
    private var serverError by mutableStateOf<String?>(null)
    private var addressError by mutableStateOf<String?>(null)
    private var passwordError by mutableStateOf<String?>(null)
    private var stateMessage by mutableStateOf<String?>(null)
    private var updateMessage by mutableStateOf<String?>(null)
    private var checkingUpdate by mutableStateOf(false)
    private var autoCheck by mutableStateOf(true)
    private var confirmAutoCheckOff by mutableStateOf(false)
    private var confirmDowngrade by mutableStateOf(false)
    private var pending: Account? = null
    private var pendingJid by mutableStateOf<String?>(null)
    private var inviteUri by mutableStateOf<String?>(null)
    private var challenge by mutableStateOf<Challenge?>(null)
    private var backendReady by mutableStateOf(false)
    private var phase by mutableStateOf(0)
    private var tourPage by mutableStateOf(0)
    private var selectedChannel by mutableStateOf(UpdateChannel.fromVersion(BuildConfig.VERSION_NAME) ?: UpdateChannel.STABLE)
    private var channelInitialized by mutableStateOf(false)
    private var checkingAvatar = false
    private var avatarUri by mutableStateOf<Uri?>(null)
    private var contactsOnly by mutableStateOf(false)
    private var privacyTouched by mutableStateOf(false)
    private var privacyLoading by mutableStateOf(false)
    private var privacyRequested = false
    private var avatarStepAvailable by mutableStateOf(false)
    private var publishing by mutableStateOf(false)
    private var completed by mutableStateOf(false)
    private val tourOnly get() = intent?.getBooleanExtra("tour_only", false) == true
    private val session by lazy { OnboardingSession(this) }
    private val cropAvatar = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) avatarUri = result.uriContent
    }
    private val backupPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startActivity(Intent(this, ImportBackupActivity::class.java))
        else Toast.makeText(this, getString(R.string.no_storage_permission, getString(R.string.app_name)), Toast.LENGTH_LONG).show()
    }
    private data class Challenge(val account: Account, val data: Data, val image: Bitmap)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autoCheck = UpdatePreferences(this).autoCheck
        phase = savedInstanceState?.getInt("phase") ?: 0
        tourPage = savedInstanceState?.getInt("tour_page") ?: 0
        selectedChannel = savedInstanceState?.getString("selected_channel")?.let(UpdateChannel::fromId) ?: selectedChannel
        channelInitialized = savedInstanceState?.getBoolean("channel_initialized") ?: false
        avatarUri = savedInstanceState?.getString("avatar_uri")?.let(Uri::parse)
        contactsOnly = savedInstanceState?.getBoolean("contacts_only") ?: false
        privacyTouched = savedInstanceState?.getBoolean("privacy_touched") ?: false
        avatarStepAvailable = savedInstanceState?.getBoolean("avatar_step_available") ?: false
        pendingJid = savedInstanceState?.getString("pending_jid")
        inviteUri = savedInstanceState?.getString("invite_uri") ?: intent?.getStringExtra(StartConversationActivity.EXTRA_INVITE_URI)
        creating = savedInstanceState?.getBoolean("creating")?.takeIf { savedInstanceState.containsKey("creating") }
        registrationDomain = savedInstanceState?.getString("registration_domain") ?: intent?.getStringExtra("registration_domain") ?: "on-chat.ru"
        preAuth = intent?.getStringExtra("pre_auth")
        address = savedInstanceState?.getString("address").orEmpty()
        hostname = savedInstanceState?.getString("hostname").orEmpty()
        port = savedInstanceState?.getString("port") ?: "5222"
        if (tourOnly) {
            phase = 2
        } else if (savedInstanceState == null) {
            val checkpoint = session.read(selectedChannel)
            checkpoint?.let {
                phase = checkpoint.phase
                tourPage = checkpoint.tourPage
                creating = checkpoint.creating
                registrationDomain = checkpoint.registrationDomain
                preAuth = preAuth ?: checkpoint.preAuth
                address = checkpoint.address
                hostname = checkpoint.hostname
                port = checkpoint.port
                pendingJid = checkpoint.accountJid
                inviteUri = checkpoint.inviteUri ?: inviteUri
                selectedChannel = checkpoint.channel
                channelInitialized = checkpoint.channelInitialized
                avatarUri = checkpoint.avatarUri?.let(Uri::parse)
                contactsOnly = checkpoint.contactsOnly
                privacyTouched = checkpoint.privacyTouched
                avatarStepAvailable = checkpoint.avatarStepAvailable
            }
            if (intent?.hasExtra(EditAccountActivity.EXTRA_FORCE_REGISTER) == true && checkpoint?.accountJid == null) {
                creating = intent.getBooleanExtra(EditAccountActivity.EXTRA_FORCE_REGISTER, false)
                registrationDomain = intent.getStringExtra("registration_domain") ?: registrationDomain
                intent.getStringExtra("jid")?.let { address = it; pendingJid = it }
            } else if (checkpoint == null && intent?.hasExtra("jid") == true) {
                pendingJid = intent.getStringExtra("jid")
                address = pendingJid.orEmpty()
            }
            if (checkpoint == null && intent?.getBooleanExtra("post_account", false) == true) phase = 2
        }
        setContent {
            ImpulseExpressiveTheme {
                eu.siacs.conversations.ui.glass.GlassSkinHost(
                    glass = rememberLiquidGlassEnabled(), celebrate = 0, haptics = false,
                ) {
                val progressAnimation = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    snapshotFlow {
                        if (completed || tourOnly) null else OnboardingSession.Checkpoint(phase, tourPage, creating, registrationDomain, preAuth,
                            address, hostname, port, pendingJid, inviteUri, selectedChannel, channelInitialized, avatarUri?.toString(), contactsOnly, privacyTouched, avatarStepAvailable)
                    }.collect { checkpoint -> if (checkpoint != null && !completed) session.save(checkpoint) }
                }
                BackHandler(enabled = phase in 1..5 || (phase == 0 && creating != null)) {
                    when (phase) {
                        0 -> creating = null
                        1 -> Unit // Do not return to credentials after the account is online.
                        2 -> if (tourPage > 0) tourPage-- else if (tourOnly) finish() else if (avatarStepAvailable) phase = 1
                        3 -> phase = 2
                        4 -> phase = 3
                        5 -> phase = 4
                    }
                }
                if (phase == 5) {
                    NotificationSetupScreen(onDone = { phase = 6 }, onBack = { phase = 4 }, onboardingProgress = progressAnimation)
                } else Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val mode = creating
                    if (phase == 1) {
                        Text(stringResource(R.string.your_avatar))
                        OnboardingAvatarPreview(avatarUri)
                        Button(onClick = { pickAvatar() }, enabled = !publishing, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.touch_to_choose_picture)) }
                        if (avatarUri != null) Text(stringResource(R.string.onboarding_avatar_selected))
                        Row {
                            Checkbox(checked = contactsOnly, onCheckedChange = { contactsOnly = it; privacyTouched = true }, enabled = !publishing && !privacyLoading)
                            Text(stringResource(R.string.onboarding_avatar_contacts))
                        }
                        stateMessage?.let { Text(it) }
                        Row(Modifier.fillMaxWidth()) {
                            OnboardingAction(stringResource(R.string.skip), onClick = { phase = 2 }, enabled = !publishing, secondary = true)
                            OnboardingProgress(0.2f, Modifier.weight(1f), progressAnimation)
                            OnboardingAction(stringResource(if (publishing) R.string.publishing else R.string.publish),
                                onClick = { publishAvatar() }, enabled = avatarUri != null && !publishing && !privacyLoading)
                        }
                    } else if (phase == 2) {
                        AnimatedContent(
                            targetState = tourPage,
                            modifier = Modifier.weight(1f),
                            transitionSpec = {
                                val forward = targetState > initialState
                                (slideInHorizontally { if (forward) it / 5 else -it / 5 } + fadeIn()) togetherWith
                                    (slideOutHorizontally { if (forward) -it / 5 else it / 5 } + fadeOut())
                            },
                            label = "onboarding_tour",
                        ) { page -> OnboardingTour(page, Modifier.fillMaxSize()) }
                        Row(Modifier.fillMaxWidth()) {
                            OnboardingAction(stringResource(R.string.back), onClick = { if (tourPage > 0) tourPage-- else if (tourOnly) finish() else if (avatarStepAvailable) phase = 1 }, enabled = tourPage > 0 || tourOnly || avatarStepAvailable, secondary = true)
                            OnboardingProgress((tourPage + 3f) / (ONBOARDING_TOUR_PAGES + 5), Modifier.weight(1f), progressAnimation)
                            OnboardingAction(stringResource(if (tourOnly && tourPage == ONBOARDING_TOUR_PAGES - 1) R.string.notification_setup_done else R.string.next), onClick = { if (tourPage < ONBOARDING_TOUR_PAGES - 1) tourPage++ else if (tourOnly) finish() else openUpdates() })
                        }
                    } else if (phase == 3) {
                        OnboardingUpdateChannel(BuildConfig.VERSION_NAME, selectedChannel, onChoose = { channel, gradual ->
                            val preferences = UpdatePreferences(this@UnifiedOnboardingActivity)
                            val oldChannel = preferences.selectedChannel
                            val oldTarget = preferences.transitionTarget
                            preferences.chooseChannel(channel, gradual, BuildConfig.VERSION_NAME)
                            if (oldChannel != preferences.selectedChannel || oldTarget != preferences.transitionTarget) {
                                if (preferences.activeDownloadId != -1L) {
                                    UpdateDownloader.cancelDownload(this@UnifiedOnboardingActivity, preferences.activeDownloadId)
                                    preferences.activeDownloadId = -1L
                                }
                                preferences.clearPending()
                                preferences.clearDownload()
                                updateMessage = null
                            }
                            selectedChannel = channel
                        }, onUpdateNow = { checkForUpdate() }, modifier = Modifier.weight(1f))
                        updateMessage?.let { Text(it) }
                        Row(Modifier.fillMaxWidth()) {
                            OnboardingAction(stringResource(R.string.back), onClick = { phase = 2 }, secondary = true)
                            OnboardingProgress((ONBOARDING_TOUR_PAGES + 3f) / (ONBOARDING_TOUR_PAGES + 5), Modifier.weight(1f), progressAnimation)
                            OnboardingAction(stringResource(R.string.next), onClick = { phase = 4 })
                        }
                    } else if (phase == 4) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.onboarding_update_notifications_title), style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
                            Text(stringResource(R.string.onboarding_update_notifications_description))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(stringResource(R.string.updates_auto_check_label), Modifier.weight(1f))
                                Switch(checked = autoCheck, onCheckedChange = { enabled ->
                                    if (!enabled && UpdatePreferences(this@UnifiedOnboardingActivity).selectedChannel != UpdateChannel.STABLE) {
                                        confirmAutoCheckOff = true
                                    } else {
                                        autoCheck = enabled
                                        UpdatePreferences(this@UnifiedOnboardingActivity).autoCheck = enabled
                                    }
                                })
                            }
                        }
                        Row(Modifier.fillMaxWidth()) {
                            OnboardingAction(stringResource(R.string.back), onClick = { phase = 3 }, secondary = true)
                            OnboardingProgress(0.9f, Modifier.weight(1f), progressAnimation)
                            OnboardingAction(stringResource(R.string.next), onClick = { phase = 5 })
                        }
                    } else if (phase == 6) {
                        Text(stringResource(R.string.welcome_get_started))
                        Button(onClick = {
                            completed = true
                            session.clear()
                            eu.siacs.conversations.utils.OnboardingPreferences(this@UnifiedOnboardingActivity).completeWelcome()
                            inviteUri?.let { intent.putExtra(StartConversationActivity.EXTRA_INVITE_URI, it) }
                            startActivity(StartConversationActivity.startOrConversationsActivity(this@UnifiedOnboardingActivity, pending))
                            finish()
                        }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.welcome_get_started)) }
                    } else if (mode == null) {
                        // Account entry has five actions. Let this content scroll rather than
                        // clipping choices on compact screens or with large accessibility text.
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Image(painterResource(R.drawable.impulse_round_logo), contentDescription = null, modifier = Modifier.size(96.dp))
                            Text(stringResource(R.string.onboarding_welcome_title), style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
                            Text(stringResource(R.string.welcome_intro_body))
                            Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.create_account)) }
                            TextButton(onClick = { creating = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.log_in)) }
                            TextButton(onClick = { restoreBackup() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore_backup)) }
                            TextButton(onClick = { requestPermissionAndScanQrCode() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_qr_code)) }
                            TextButton(onClick = { chooseCertificate() }, modifier = Modifier.fillMaxWidth(), enabled = backendReady) { Text(stringResource(R.string.action_add_account_with_certificate)) }
                        }
                    } else {
                        OnboardingAccountForm(mode, registrationDomain, address, { address = it; addressError = null }, password,
                            { password = it; passwordError = null }, addressError, passwordError,
                            hostname, { hostname = it; serverError = null }, port, { port = it; serverError = null }, serverError,
                            backendReady, Modifier.weight(1f))
                        stateMessage?.let { Text(it) }
                        if (pending?.status == Account.State.REGISTRATION_WEB || pending?.status == Account.State.PAYMENT_REQUIRED) {
                            TextButton(onClick = { openAccountRedirect() }) { Text(stringResource(R.string.onboarding_open_registration)) }
                        }
                        if (pending?.status == Account.State.TOR_NOT_AVAILABLE) {
                            TextButton(onClick = { startOrbot() }) {
                                Text(stringResource(if (TorServiceUtils.isOrbotInstalled(this@UnifiedOnboardingActivity)) R.string.start_orbot else R.string.install_orbot))
                            }
                        }
                        if (pending?.lastErrorStatus == Account.State.DOWNGRADE_ATTACK && (pending?.pinnedMechanismPriority ?: -1) >= 0) {
                            TextButton(onClick = { confirmDowngrade = true }) { Text(stringResource(R.string.accept)) }
                        }
                        Row(Modifier.fillMaxWidth()) {
                            OnboardingAction(stringResource(R.string.back), onClick = { creating = null }, secondary = true)
                            OnboardingProgress(0.1f, Modifier.weight(1f), progressAnimation)
                            OnboardingAction(stringResource(R.string.next), onClick = { submit(mode) }, enabled = backendReady)
                        }
                    }
                }
                if (confirmDowngrade) {
                    AlertDialog(
                        onDismissRequest = { confirmDowngrade = false },
                        title = { Text(stringResource(R.string.sasl_downgrade)) },
                        text = { Text(stringResource(R.string.onboarding_sasl_warning)) },
                        confirmButton = { Button(onClick = {
                            confirmDowngrade = false
                            pending?.let { account ->
                                account.resetPinnedMechanism()
                                if (!xmppConnectionService.updateAccount(account)) stateMessage = getString(R.string.unable_to_update_account)
                            }
                        }) { Text(stringResource(R.string.accept)) } },
                        dismissButton = { TextButton(onClick = { confirmDowngrade = false }) { Text(stringResource(R.string.cancel)) } },
                    )
                }
                if (confirmAutoCheckOff) {
                    AlertDialog(
                        onDismissRequest = { confirmAutoCheckOff = false },
                        title = { Text(stringResource(R.string.updates_auto_off_title)) },
                        text = { Text(stringResource(R.string.updates_auto_off_body)) },
                        confirmButton = { Button(onClick = { confirmAutoCheckOff = false }) { Text(stringResource(R.string.updates_auto_off_keep)) } },
                        dismissButton = { TextButton(onClick = {
                            confirmAutoCheckOff = false
                            autoCheck = false
                            UpdatePreferences(this@UnifiedOnboardingActivity).autoCheck = false
                        }) { Text(stringResource(R.string.updates_auto_off_anyway)) } },
                    )
                }
                challenge?.let { current ->
                    OnboardingCaptcha(current.image, onAnswer = { answer ->
                        challenge = null
                        current.account.getXmppConnection()?.register(current.data, answer)
                    }, onCancel = {
                        challenge = null
                        current.account.getXmppConnection()?.cancelRegistration()
                    })
                }
                }
            }
        }
    }

    private fun submit(register: Boolean) {
        val service = xmppConnectionService ?: return
        val submittedPassword = password.ifEmpty { pending?.password.orEmpty() }
        when (val result = OnboardingAccountController.submit(service, address, submittedPassword, register, registrationDomain, preAuth, pending, hostname, port) { jid ->
            pendingJid = jid.toString()
            saveCheckpoint()
        }) {
            is OnboardingAccountController.Result.Started -> {
                pending = result.account
                pendingJid = result.account.jid.asBareJid().toString()
                preAuth = null // Already attached to the account if the server needs it.
                password = ""
                refreshUiReal()
            }
            OnboardingAccountController.Result.InvalidAddress -> addressError = getString(if (register) R.string.invalid_username else R.string.invalid_jid)
            OnboardingAccountController.Result.EmptyPassword -> passwordError = getString(R.string.password_should_not_be_empty)
            OnboardingAccountController.Result.AccountAlreadyExists -> addressError = getString(R.string.account_already_exists)
            OnboardingAccountController.Result.InvalidHostname -> serverError = getString(R.string.not_valid_hostname)
            OnboardingAccountController.Result.InvalidPort -> serverError = getString(R.string.not_a_valid_port)
            OnboardingAccountController.Result.UpdateFailed -> stateMessage = getString(R.string.unable_to_update_account)
        }
    }

    private fun saveCheckpoint() {
        if (!completed) session.save(OnboardingSession.Checkpoint(phase, tourPage, creating, registrationDomain, preAuth, address,
            hostname, port, pendingJid, inviteUri, selectedChannel, channelInitialized, avatarUri?.toString(), contactsOnly, privacyTouched, avatarStepAvailable))
    }

    private fun openUpdates() {
        if (!channelInitialized) {
            val preferences = UpdatePreferences(this)
            preferences.selectedChannel = selectedChannel
            preferences.transitionTarget = null
            channelInitialized = true
        }
        phase = 3
    }

    private fun checkForUpdate() {
        if (checkingUpdate) return
        checkingUpdate = true
        updateMessage = getString(R.string.updates_status_checking)
        lifecycleScope.launch {
            val preferences = UpdatePreferences(this@UnifiedOnboardingActivity)
            val channel = preferences.selectedChannel
            val target = preferences.transitionTarget
            val result = try {
                withContext(Dispatchers.IO) { UpdateChecker(OkHttpClient()).checkForUpdate(channel, target) }
            } catch (_: Exception) { UpdateChecker.CheckResult.CheckFailed }
            checkingUpdate = false
            if (channel != preferences.selectedChannel || target != preferences.transitionTarget) return@launch
            when (result) {
                is UpdateChecker.CheckResult.UpToDate -> updateMessage = getString(R.string.updates_status_up_to_date)
                is UpdateChecker.CheckResult.ChannelBehind -> updateMessage = getString(R.string.updates_status_channel_behind)
                is UpdateChecker.CheckResult.CheckFailed -> updateMessage = getString(R.string.updates_status_check_failed)
                is UpdateChecker.CheckResult.UpdateAvailable -> {
                    val info = result.info
                    if (preferences.activeDownloadId != -1L && preferences.pendingUpdateVersion != info.versionName) {
                        val id = preferences.activeDownloadId
                        withContext(Dispatchers.IO) { UpdateDownloader.cancelDownload(this@UnifiedOnboardingActivity, id) }
                        preferences.activeDownloadId = -1L
                    }
                    preferences.pendingUpdateVersion = info.versionName
                    preferences.pendingUpdateUrl = info.downloadUrl
                    preferences.pendingReleaseNotes = info.releaseNotes
                    preferences.pendingReleaseTitle = info.releaseTitle
                    val alreadyDownloaded = preferences.downloadedVersion == info.versionName && preferences.downloadedApkExists()
                    if (preferences.activeDownloadId == -1L && !alreadyDownloaded) {
                        if (UpdateDownloader.isWifiConnected(this@UnifiedOnboardingActivity)) {
                            preferences.pendingNoWifi = false
                            preferences.activeDownloadId = UpdateDownloader.startDownload(this@UnifiedOnboardingActivity, info)
                        } else preferences.pendingNoWifi = true
                    } else preferences.pendingNoWifi = false
                    updateMessage = getString(R.string.updates_new_version_available, info.versionName)
                    if (!isFinishing && !supportFragmentManager.isStateSaved && supportFragmentManager.findFragmentByTag(UpdateSheetFragment.TAG) == null) {
                        UpdateSheetFragment().show(supportFragmentManager, UpdateSheetFragment.TAG)
                    }
                }
            }
        }
    }

    private fun restoreBackup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, ImportBackupActivity::class.java))
        } else backupPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun openAccountRedirect() {
        val url = pending?.getXmppConnection()?.redirectionUrl ?: return
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()))) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.application_found_to_open_website, Toast.LENGTH_LONG).show() }
    }

    private fun startOrbot() {
        if (TorServiceUtils.isOrbotInstalled(this)) TorServiceUtils.startOrbot(this, 0xff22)
        else TorServiceUtils.downloadOrbot(this, 0xff22)
    }

    private fun chooseCertificate() {
        try { KeyChain.choosePrivateKeyAlias(this, this, null, null, null, -1, null) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.device_does_not_support_certificates, Toast.LENGTH_LONG).show() }
    }

    override fun alias(alias: String?) {
        if (alias.isNullOrEmpty()) {
            runOnUiThread { Toast.makeText(this, R.string.no_certificate_selected, Toast.LENGTH_LONG).show() }
        } else xmppConnectionService?.createAccountFromKey(alias, this)
    }

    override fun onAccountCreated(account: Account) {
        runOnUiThread {
            pending = account
            pendingJid = account.jid.asBareJid().toString()
            creating = false
            address = pendingJid.orEmpty()
            // createAccountFromKey intentionally creates disabled accounts; the old setup screen
            // enabled them on confirmation. Enable this newly created account to start sign-in.
            account.setOption(Account.OPTION_DISABLED, false)
            account.setOption(Account.OPTION_SOFT_DISABLED, false)
            if (!xmppConnectionService.updateAccount(account)) {
                stateMessage = getString(R.string.unable_to_update_account)
                return@runOnUiThread
            }
            refreshUiReal()
        }
    }

    override fun informUser(res: Int) { runOnUiThread { Toast.makeText(this, res, Toast.LENGTH_LONG).show() } }
    override fun onShowErrorToast(resId: Int) { runOnUiThread { Toast.makeText(this, resId, Toast.LENGTH_SHORT).show() } }

    override fun onBackendConnected() {
        backendReady = true
        pendingJid?.let { jid ->
            try { pending = xmppConnectionService?.findAccountByJid(eu.siacs.conversations.xmpp.Jid.of(jid)) }
            catch (_: IllegalArgumentException) { pendingJid = null }
        }
        pending?.let { account ->
            if (hostname.isEmpty()) hostname = account.hostname.orEmpty()
            if (port == "5222") port = account.port.toString()
        }
        refreshUiReal()
    }

    override fun onAccountUpdate() = refreshUi()

    override fun refreshUiReal() {
        val account = pending ?: return
        if (phase == 1 && account.isOnlineAndConnected) loadAvatarPrivacy(account)
        stateMessage = getString(account.status.readableId)
        if (phase == 0 && account.isOnlineAndConnected && !checkingAvatar) {
            checkingAvatar = true
            val connection = account.getXmppConnection()
            if (!connection.getManager(PepManager::class.java).isAvailable()) {
                phase = 2
                return
            }
            val avatar = xmppConnectionService.checkForAvatar(account)
            avatar.addListener({
                val hasAvatar = try { avatar.get(); true } catch (_: Exception) { false }
                runOnUiThread {
                    avatarStepAvailable = !hasAvatar
                    phase = if (hasAvatar) 2 else 1
                    stateMessage = null
                    if (!hasAvatar) {
                        loadAvatarPrivacy(account)
                        if (avatarUri == null) lifecycleScope.launch {
                            val profile = withContext(Dispatchers.IO) {
                                try { PhoneHelper.getProfilePictureUri(this@UnifiedOnboardingActivity) } catch (_: Exception) { null }
                            }
                            if (avatarUri == null && phase == 1) avatarUri = profile
                        }
                    }
                }
            }, MoreExecutors.directExecutor())
        }
    }

    private fun loadAvatarPrivacy(account: Account) {
        if (privacyRequested) return
        val connection = account.getXmppConnection() ?: return
        privacyRequested = true
        privacyLoading = true
        val future = connection.getManager(AvatarManager::class.java).pepAccessModel
        future.addListener({
            val contacts = try { future.get() == NodeConfiguration.AccessModel.PRESENCE } catch (_: Exception) { false }
            runOnUiThread { if (!privacyTouched) contactsOnly = contacts; privacyLoading = false }
        }, MoreExecutors.directExecutor())
    }

    private fun pickAvatar() {
        val options = CropImageOptions().apply {
            aspectRatioX = 1; aspectRatioY = 1; fixAspectRatio = true
            outputCompressFormat = Bitmap.CompressFormat.PNG
            imageSourceIncludeCamera = false
            minCropResultHeight = Config.AVATAR_THUMBNAIL_SIZE
            minCropResultWidth = Config.AVATAR_THUMBNAIL_SIZE
        }
        cropAvatar.launch(CropImageContractOptions(null, options))
    }

    private fun publishAvatar() {
        val account = pending ?: return
        val image = avatarUri ?: return
        if (!account.isOnlineAndConnected) { stateMessage = getString(R.string.error_publish_avatar_offline); return }
        publishing = true
        xmppConnectionService.publishAvatar(account, image, !contactsOnly, this)
    }

    override fun onAvatarPublicationSucceeded() = runOnUiThread { publishing = false; phase = 2; stateMessage = null }
    override fun onAvatarPublicationFailed(res: Int) = runOnUiThread { publishing = false; stateMessage = getString(res) }

    override fun onCaptchaRequested(account: Account, data: Data, captcha: Bitmap) {
        runOnUiThread {
            // The service may dispatch a pending CAPTCHA as listeners are registered, before
            // onBackendConnected has resolved the restored account object.
            if (pending === account || pendingJid == account.jid.asBareJid().toString()) {
                pending = account
                challenge = Challenge(account, data, captcha)
            }
        }
    }

    override fun onQrCodeScanned(code: String) {
        val uri = MiniUri.getOrNull(code)
        if (uri !is MiniUri.Xmpp || !uri.isAddress) {
            super.onQrCodeScanned(code)
            return
        }
        val jid = uri.asJid()
        if (jid == null) {
            super.onQrCodeScanned(code)
            return
        }
        val registering = uri.isAction(MiniUri.Xmpp.ACTION_REGISTER) ||
            (uri.isAction(MiniUri.Xmpp.ACTION_ROSTER) && uri.isYesIbr)
        if (registering) {
            registrationDomain = jid.domain.toString()
            address = if (uri.isAction(MiniUri.Xmpp.ACTION_REGISTER) && jid.local != null) jid.asBareJid().toString() else ""
            preAuth = uri.getParameter(MiniUri.Xmpp.PARAMETER_PRE_AUTH)
            pending = null
            pendingJid = null
            creating = true
            if (uri.isAction(MiniUri.Xmpp.ACTION_ROSTER)) inviteUri = uri.asUri().toString()
        } else {
            // A contact invite belongs to the completed flow, not to a second signup activity.
            inviteUri = uri.asUri().toString()
        }
        saveCheckpoint()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        creating?.let { outState.putBoolean("creating", it) }
        outState.putString("registration_domain", registrationDomain)
        outState.putString("address", address)
        outState.putString("hostname", hostname)
        outState.putString("port", port)
        outState.putString("pending_jid", pendingJid)
        outState.putString("invite_uri", inviteUri)
        outState.putInt("phase", phase)
        outState.putInt("tour_page", tourPage)
        outState.putString("selected_channel", selectedChannel.id)
        outState.putBoolean("channel_initialized", channelInitialized)
        outState.putString("avatar_uri", avatarUri?.toString())
        outState.putBoolean("contacts_only", contactsOnly)
        outState.putBoolean("privacy_touched", privacyTouched)
        outState.putBoolean("avatar_step_available", avatarStepAvailable)
        super.onSaveInstanceState(outState)
    }
}
