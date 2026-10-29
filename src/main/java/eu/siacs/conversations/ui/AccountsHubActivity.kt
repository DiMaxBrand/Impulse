package eu.siacs.conversations.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.security.KeyChain
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import eu.siacs.conversations.BuildConfig
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.entities.PresenceTemplate
import eu.siacs.conversations.services.AbstractQuickConversationsService
import eu.siacs.conversations.services.BarcodeProvider
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate
import eu.siacs.conversations.ui.activity.SettingsActivity
import eu.siacs.conversations.ui.activity.UpdatesActivity
import eu.siacs.conversations.utils.BirthdayStore
import eu.siacs.conversations.xmpp.manager.BirthdayManager
import eu.siacs.conversations.xmpp.manager.BlockingManager
import eu.siacs.conversations.xmpp.manager.EasyOnboardingManager
import eu.siacs.conversations.xmpp.manager.MessageArchiveManager
import eu.siacs.conversations.xmpp.manager.RegistrationManager
import im.conversations.android.xmpp.model.mam.Preferences

/**
 * One home for accounts and settings: every account as a card (details inline, no separate
 * screen), the account actions in the three-dot menu, and the settings list underneath.
 */
class AccountsHubActivity : XmppActivity(), OnAccountUpdate {

    private var accounts by mutableStateOf<List<Account>>(emptyList())
    private val revision = mutableIntStateOf(0)
    private var dialog by mutableStateOf<HubDialog?>(null)
    private var focusJid by mutableStateOf<String?>(null)
    private var certificateTarget: Account? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        focusJid = intent?.getStringExtra(EXTRA_FOCUS_ACCOUNT)
        setContent {
            ImpulseExpressiveTheme {
                AccountsHubScreen(
                    accounts = accounts,
                    revision = revision.intValue,
                    settings = settingsEntries(),
                    actions = hubActions,
                    focusJid = focusJid,
                    onFocused = { focusJid = null },
                )
                HubDialogs()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_FOCUS_ACCOUNT)?.let { focusJid = it }
    }

    override fun onAccountUpdate() = refreshUi()

    override fun refreshUiReal() {
        val service = xmppConnectionService ?: return
        accounts = ArrayList(service.accounts)
        revision.intValue++
    }

    override fun onBackendConnected() {
        refreshUiReal()
        // "Set it up" from the one-time birthday prompt lands straight on the picker.
        if (intent?.getBooleanExtra(EXTRA_OPEN_BIRTHDAY, false) == true) {
            intent.removeExtra(EXTRA_OPEN_BIRTHDAY)
            accounts.firstOrNull { it.isOnlineAndConnected() }?.let { dialog = HubDialog.EditBirthday(it) }
        }
    }

    // ─── Account actions ────────────────────────────────────────────────────

    private fun available(action: AccountAction, account: Account): Boolean {
        val online = account.isOnlineAndConnected()
        val connection = if (online) account.getXmppConnection() else null
        return when (action) {
            AccountAction.STATUS -> online
            AccountAction.SHARE_URI, AccountAction.SHARE_INVITE,
            AccountAction.SHOW_QR, AccountAction.SHARE_QR -> true
            AccountAction.BLOCKLIST ->
                connection?.getManager(BlockingManager::class.java)?.hasFeature() == true
            AccountAction.RENEW_CERT -> account.getPrivateKeyAlias() != null
            AccountAction.MAM ->
                connection?.getManager(MessageArchiveManager::class.java)?.hasFeature() == true
            AccountAction.PASSWORD, AccountAction.DELETE ->
                connection?.getManager(RegistrationManager::class.java)?.hasFeature() == true
        }
    }

    private fun applicable(action: AccountAction) = accounts.filter { available(action, it) }

    private fun run(action: AccountAction) {
        val candidates = applicable(action)
        when {
            candidates.isEmpty() -> Unit
            candidates.size == 1 -> perform(action, candidates[0])
            else -> dialog = HubDialog.ChooseAccount(action)
        }
    }

    private fun perform(action: AccountAction, account: Account) {
        when (action) {
            AccountAction.STATUS -> dialog = HubDialog.EditStatus(account)
            AccountAction.SHARE_URI -> shareInvite(account, asHttp = false)
            AccountAction.SHARE_INVITE -> shareInvite(account, asHttp = true)
            AccountAction.SHOW_QR -> showQrCode(account)
            AccountAction.SHARE_QR -> {
                val intent = Intent(Intent.ACTION_SEND)
                intent.putExtra(Intent.EXTRA_STREAM, BarcodeProvider.getUriForAccount(this, account))
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                intent.type = "image/png"
                startActivity(Intent.createChooser(intent, getText(R.string.share_with)))
            }
            AccountAction.BLOCKLIST -> {
                val intent = Intent(this, BlocklistActivity::class.java)
                intent.putExtra(EXTRA_ACCOUNT, account.getJid().toString())
                startActivity(intent)
            }
            AccountAction.RENEW_CERT -> {
                certificateTarget = account
                try {
                    KeyChain.choosePrivateKeyAlias(this, { alias ->
                        if (alias != null) certificateTarget?.let { xmppConnectionService.updateKeyInAccount(it, alias) }
                    }, null, null, null, -1, null)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.device_does_not_support_certificates, Toast.LENGTH_LONG).show()
                }
            }
            AccountAction.MAM -> fetchMamPreference(account)
            AccountAction.PASSWORD -> {
                val intent = Intent(this, ChangePasswordActivity::class.java)
                intent.putExtra(EXTRA_ACCOUNT, account.getJid().toString())
                startActivity(intent)
            }
            AccountAction.DELETE -> deleteAccount(account)
        }
    }

    private fun shareInvite(account: Account, asHttp: Boolean) {
        val manager = account.getXmppConnection().getManager(EasyOnboardingManager::class.java)
        val future: com.google.common.util.concurrent.ListenableFuture<out de.gultsch.common.MiniUri> =
            if (asHttp) manager.invitationUrl() else manager.inviteOrFallback()
        val toast = if (future.isDone) null else Toast.makeText(this, R.string.please_wait, Toast.LENGTH_LONG)
        toast?.show()
        @Suppress("UNCHECKED_CAST")
        Futures.addCallback(
            future as com.google.common.util.concurrent.ListenableFuture<de.gultsch.common.MiniUri>,
            object : FutureCallback<de.gultsch.common.MiniUri> {
                override fun onSuccess(result: de.gultsch.common.MiniUri) {
                    toast?.cancel()
                    val uri = result.asUri().toString()
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "text/plain"
                    intent.putExtra(Intent.EXTRA_TEXT, uri)
                    try {
                        startActivity(Intent.createChooser(intent, getText(R.string.share_uri_with)))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(this@AccountsHubActivity, R.string.no_application_to_share_uri,
                            Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(t: Throwable) {
                    toast?.cancel()
                    Log.e(Config.LOGTAG, "could not fetch invite uri", t)
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun fetchMamPreference(account: Account) {
        val toast = Toast.makeText(this, R.string.fetching_mam_prefs, Toast.LENGTH_LONG)
        toast.show()
        val future = account.getXmppConnection().getManager(MessageArchiveManager::class.java)
            .archivingPreference
        Futures.addCallback(
            future,
            object : FutureCallback<Preferences.Default> {
                override fun onSuccess(current: Preferences.Default) {
                    toast.cancel()
                    val index = listOf(Preferences.Default.NEVER, Preferences.Default.ROSTER,
                        Preferences.Default.ALWAYS).indexOf(current).coerceAtLeast(0)
                    dialog = HubDialog.Mam(account, index)
                }

                override fun onFailure(t: Throwable) {
                    toast.cancel()
                    Log.d(Config.LOGTAG, "error fetching mam preferences", t)
                    Toast.makeText(this@AccountsHubActivity, R.string.unable_to_fetch_mam_prefs,
                        Toast.LENGTH_LONG).show()
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun setEnabled(account: Account, enable: Boolean) {
        if (enable) {
            account.setOption(Account.OPTION_DISABLED, false)
            account.setOption(Account.OPTION_SOFT_DISABLED, false)
            account.getXmppConnection().resetEverything()
        } else {
            account.setOption(Account.OPTION_DISABLED, true)
            account.setOption(Account.OPTION_QUICKSTART_AVAILABLE, false)
        }
        if (!xmppConnectionService.updateAccount(account)) {
            Toast.makeText(this, R.string.unable_to_update_account, Toast.LENGTH_SHORT).show()
        }
    }

    private val hubActions by lazy {
        AccountsHubActions(
            onBack = { finish() },
            onAddAccount = { startActivity(Intent(this, EditAccountActivity::class.java)) },
            onToggleEnabled = ::setEnabled,
            onAvatar = { account ->
                val intent = Intent(applicationContext, PublishProfilePictureActivity::class.java)
                intent.putExtra(EXTRA_ACCOUNT, account.getJid().asBareJid().toString())
                startActivity(intent)
            },
            onEditName = { dialog = HubDialog.EditName(it) },
            onEditStatus = { dialog = HubDialog.EditStatus(it) },
            onEditBirthday = { dialog = HubDialog.EditBirthday(it) },
            birthdayOf = { account ->
                account.getUuid()?.let {
                    BirthdayStore(this).get(it, account.getJid().asBareJid().toString())
                }
            },
            onShowQr = { showQrCode(it) },
            onDeviceKeys = { switchToAccount(it) },
            availableActions = { AccountAction.entries.filter { a -> applicable(a).isNotEmpty() } },
            runAction = ::run,
            canEnableAll = { accounts.any { !it.isEnabled() } },
            canDisableAll = { accounts.any { it.isEnabled() } },
            onEnableAll = { accounts.filter { !it.isEnabled() }.forEach { setEnabled(it, true) } },
            onDisableAll = { accounts.filter { it.isEnabled() }.forEach { setEnabled(it, false) } },
            onImportBackup = {
                if (hasStoragePermission(REQUEST_IMPORT_BACKUP)) {
                    startActivity(Intent(this, ImportBackupActivity::class.java))
                }
            },
            avatarFor = { account ->
                val px = (96 * resources.displayMetrics.density).toInt()
                try { avatarService().get(account, px, false) } catch (_: Exception) { null as Bitmap? }
            },
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_IMPORT_BACKUP && grantResults.isNotEmpty() &&
            grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            startActivity(Intent(this, ImportBackupActivity::class.java))
        }
    }

    // ─── Dialogs ────────────────────────────────────────────────────────────

    @androidx.compose.runtime.Composable
    private fun HubDialogs() {
        when (val d = dialog) {
            null -> Unit
            is HubDialog.EditName -> TextEditDialog(
                title = getString(R.string.your_name),
                icon = R.drawable.ic_person_24dp,
                initial = d.account.getDisplayName() ?: "",
                onConfirm = { value ->
                    d.account.setDisplayName(value)
                    xmppConnectionService.publishDisplayName(d.account)
                    dialog = null
                    refreshUiReal()
                },
                onDismiss = { dialog = null },
            )
            is HubDialog.EditStatus -> TextEditDialog(
                title = getString(R.string.edit_status_message_title),
                icon = R.drawable.ic_edit_24dp,
                initial = d.account.getPresenceStatusMessage() ?: "",
                onConfirm = { value ->
                    xmppConnectionService.changeStatus(
                        d.account, PresenceTemplate(d.account.getPresenceStatus(), value), null)
                    dialog = null
                    refreshUiReal()
                },
                onDismiss = { dialog = null },
            )
            is HubDialog.EditBirthday -> BirthdayEditDialog(
                current = d.account.getUuid()?.let {
                    BirthdayStore(this).get(it, d.account.getJid().asBareJid().toString())
                },
                onConfirm = { birthday ->
                    d.account.getXmppConnection().getManager(BirthdayManager::class.java).publish(birthday)
                    dialog = null
                    refreshUiReal()
                },
                onDismiss = { dialog = null },
            )
            is HubDialog.ChooseAccount -> AccountChooserDialog(
                accounts = applicable(d.action),
                actions = hubActions,
                onChoose = { dialog = null; perform(d.action, it) },
                onDismiss = { dialog = null },
            )
            is HubDialog.Mam -> MamPrefsDialog(
                initial = d.current,
                onConfirm = { choice ->
                    val pref = listOf(Preferences.Default.NEVER, Preferences.Default.ROSTER,
                        Preferences.Default.ALWAYS)[choice]
                    d.account.getXmppConnection().getManager(MessageArchiveManager::class.java)
                        .setArchivingPreference(pref)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }
    }

    // ─── Settings list ──────────────────────────────────────────────────────

    private fun settingsPage(fragment: String): () -> Unit = {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_FRAGMENT, fragment))
    }

    private fun settingsEntries(): List<SettingsEntry> {
        val pkg = "eu.siacs.conversations.ui.fragment.settings."
        return buildList {
            add(SettingsEntry(R.drawable.ic_touch_app_24dp, getString(R.string.pref_title_interface),
                getString(R.string.pref_summary_appearance)) {
                startActivity(Intent(this@AccountsHubActivity,
                    eu.siacs.conversations.ui.activity.AppearanceActivity::class.java))
            })
            add(SettingsEntry(R.drawable.ic_security_24dp, getString(R.string.pref_title_security),
                getString(R.string.pref_summary_security), settingsPage(pkg + "SecuritySettingsFragment")))
            add(SettingsEntry(R.drawable.ic_privacy_tip_24dp, getString(R.string.pref_privacy),
                getString(R.string.pref_privacy_summary), settingsPage(pkg + "PrivacySettingsFragment")))
            add(SettingsEntry(R.drawable.ic_lightbulb_24dp, getString(R.string.pref_presence_settings),
                getString(R.string.pref_presence_settings_summary), settingsPage(pkg + "AvailabilitySettingsFragment")))
            add(SettingsEntry(R.drawable.ic_notifications_24dp, getString(R.string.notifications),
                getString(R.string.pref_notifications_summary), settingsPage(pkg + "NotificationsSettingsFragment")))
            add(SettingsEntry(R.drawable.ic_attachment_24dp, getString(R.string.pref_attachments),
                getString(R.string.pref_attachments_summary), settingsPage(pkg + "AttachmentsSettingsFragment")))
            add(SettingsEntry(R.drawable.ic_settings_ethernet_24dp, getString(R.string.pref_connection_options),
                getString(
                    if (eu.siacs.conversations.ui.fragment.settings.ConnectionSettingsFragment.hideChannelDiscovery())
                        R.string.pref_connection_summary else R.string.pref_connection_summary_w_cd),
                settingsPage(pkg + "ConnectionSettingsFragment")))
            add(SettingsEntry(R.drawable.ic_archive_24dp, getString(R.string.backup_and_restore),
                getString(R.string.pref_backup_summary), settingsPage(pkg + "BackupSettingsFragment")))
            if (getString(R.string.default_push_server).isNotEmpty()) {
                add(SettingsEntry(R.drawable.ic_cloud_sync_24dp, getString(R.string.unified_push_distributor),
                    getString(R.string.unified_push_summary), settingsPage(pkg + "UpSettingsFragment")))
            }
            add(SettingsEntry(R.drawable.ic_new_releases_24dp, getString(R.string.pref_title_updates),
                getString(R.string.pref_summary_updates)) {
                startActivity(Intent(this@AccountsHubActivity, UpdatesActivity::class.java))
            })
            if (BuildConfig.PRIVACY_POLICY != null && AbstractQuickConversationsService.isPlayStoreFlavor()) {
                add(SettingsEntry(R.drawable.ic_privacy_tip_24dp, getString(R.string.privacy_policy), null) {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(BuildConfig.PRIVACY_POLICY)))
                })
            }
            add(SettingsEntry(R.drawable.ic_help_24dp, getString(R.string.help_and_support), null) {
                HelpSupportSheetFragment().show(supportFragmentManager, HelpSupportSheetFragment.TAG)
            })
            add(SettingsEntry(R.drawable.ic_info_outline_24dp,
                getString(R.string.title_activity_about_x, getString(R.string.app_name)), null) {
                startActivity(Intent(this@AccountsHubActivity, AboutActivity::class.java))
            }.also {
                // Hidden, as before: long-press "About" for the developer options.
                it.onLongClick = {
                    startActivity(Intent(this@AccountsHubActivity,
                        eu.siacs.conversations.ui.activity.DeveloperOptionsActivity::class.java))
                }
            })
        }
    }

    companion object {
        private const val REQUEST_IMPORT_BACKUP = 0x63fb
        const val EXTRA_OPEN_BIRTHDAY = "eu.siacs.conversations.open_birthday"

        /** The address of an account to scroll to, open and flash (its own contact card was tapped). */
        const val EXTRA_FOCUS_ACCOUNT = "eu.siacs.conversations.focus_account"
    }
}
