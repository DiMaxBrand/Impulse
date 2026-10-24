package eu.siacs.conversations.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A row in the Settings section. */
class SettingsEntry(
    val icon: Int,
    val title: String,
    val summary: String?,
    val onClick: () -> Unit,
) {
    /** Optional hidden action on long-press. */
    var onLongClick: (() -> Unit)? = null
}

/** What the three-dot menu can do to one account. */
enum class AccountAction(val icon: Int, val label: Int) {
    STATUS(R.drawable.ic_edit_24dp, R.string.edit_status_message),
    SHARE_URI(R.drawable.ic_link_24dp, R.string.share_as_uri),
    SHARE_INVITE(R.drawable.ic_join_24dp, R.string.share_invitation),
    SHOW_QR(R.drawable.ic_qr_code_24dp, R.string.show_qr_code),
    SHARE_QR(R.drawable.ic_image_24dp, R.string.publish_qr_code),
    BLOCKLIST(R.drawable.ic_cancel_24dp, R.string.show_block_list),
    RENEW_CERT(R.drawable.ic_autorenew_24dp, R.string.action_renew_certificate),
    MAM(R.drawable.ic_archive_24dp, R.string.mam_prefs),
    PASSWORD(R.drawable.ic_lock_24dp, R.string.change_password),
    DELETE(R.drawable.ic_delete_24dp, R.string.mgmt_account_delete),
}

/** Dialog the hub is showing (state is owned by the activity). */
sealed interface HubDialog {
    class EditName(val account: Account) : HubDialog
    class EditStatus(val account: Account) : HubDialog
    class EditBirthday(val account: Account) : HubDialog
    class ChooseAccount(val action: AccountAction) : HubDialog
    class Mam(val account: Account, val current: Int) : HubDialog
}

class AccountsHubActions(
    val onBack: () -> Unit,
    val onAddAccount: () -> Unit,
    val onToggleEnabled: (Account, Boolean) -> Unit,
    val onAvatar: (Account) -> Unit,
    val onEditName: (Account) -> Unit,
    val onEditStatus: (Account) -> Unit,
    val onEditBirthday: (Account) -> Unit,
    val birthdayOf: (Account) -> eu.siacs.conversations.utils.BirthdayDate?,
    val onShowQr: (Account) -> Unit,
    val onDeviceKeys: (Account) -> Unit,
    val availableActions: () -> List<AccountAction>,
    val runAction: (AccountAction) -> Unit,
    val canEnableAll: () -> Boolean,
    val canDisableAll: () -> Boolean,
    val onEnableAll: () -> Unit,
    val onDisableAll: () -> Unit,
    val onImportBackup: () -> Unit,
    val avatarFor: suspend (Account) -> android.graphics.Bitmap?,
)

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
internal fun AccountsHubScreen(
    accounts: List<Account>,
    revision: Int,
    settings: List<SettingsEntry>,
    actions: AccountsHubActions,
) {
    // "Server info" is deliberately not remembered across visits: it starts off every time.
    var serverInfoOn by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(stringResource(R.string.hub_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back_24dp), contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = actions.onAddAccount) {
                        Icon(painterResource(R.drawable.ic_person_add_24dp),
                            contentDescription = stringResource(R.string.action_add_account))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true; shareOpen = false }) {
                            Icon(painterResource(R.drawable.ic_more_vert_24dp), contentDescription = null)
                        }
                        ExpressiveDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            val available = remember(menuOpen, revision) { actions.availableActions() }
                            val shareItems = available.filter {
                                it == AccountAction.SHARE_URI || it == AccountAction.SHARE_INVITE ||
                                    it == AccountAction.SHOW_QR || it == AccountAction.SHARE_QR
                            }
                            if (shareOpen) {
                                ExpressiveMenuItem(R.drawable.ic_arrow_back_24dp, stringResource(R.string.hub_share)) {
                                    shareOpen = false
                                }
                                shareItems.forEach { a ->
                                    ExpressiveMenuItem(a.icon, stringResource(a.label)) {
                                        menuOpen = false; actions.runAction(a)
                                    }
                                }
                            } else {
                                available.filter { it !in shareItems }.forEach { a ->
                                    if (a == AccountAction.BLOCKLIST && shareItems.isNotEmpty()) {
                                        // keep share just ahead of the account-management items
                                    }
                                    ExpressiveMenuItem(a.icon, stringResource(a.label)) {
                                        menuOpen = false; actions.runAction(a)
                                    }
                                    if (a == AccountAction.STATUS && shareItems.isNotEmpty()) {
                                        ExpressiveMenuItem(R.drawable.ic_share_24dp, stringResource(R.string.hub_share)) {
                                            shareOpen = true
                                        }
                                    }
                                }
                                if (available.none { it == AccountAction.STATUS } && shareItems.isNotEmpty()) {
                                    ExpressiveMenuItem(R.drawable.ic_share_24dp, stringResource(R.string.hub_share)) {
                                        shareOpen = true
                                    }
                                }
                                if (actions.canEnableAll()) {
                                    ExpressiveMenuItem(R.drawable.ic_check_circle_24dp,
                                        stringResource(R.string.enable_all_accounts)) {
                                        menuOpen = false; actions.onEnableAll()
                                    }
                                }
                                if (actions.canDisableAll()) {
                                    ExpressiveMenuItem(R.drawable.ic_cancel_24dp,
                                        stringResource(R.string.disable_all_accounts)) {
                                        menuOpen = false; actions.onDisableAll()
                                    }
                                }
                                ExpressiveMenuItem(R.drawable.ic_archive_24dp, stringResource(R.string.restore_backup)) {
                                    menuOpen = false; actions.onImportBackup()
                                }
                            }
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (accounts.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.hub_no_accounts),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(accounts, key = { it.getUuid() ?: it.hashCode().toString() }) { account ->
                AccountCard(
                    account = account,
                    revision = revision,
                    startExpanded = accounts.size == 1,
                    serverInfoOn = serverInfoOn,
                    onServerInfoChange = { serverInfoOn = it },
                    actions = actions,
                )
            }
            item { DevicesCards(accounts, revision) }
            item { SettingsCard(settings) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Avatar(account: Account, actions: AccountsHubActions, size: Int, onClick: (() -> Unit)?) {
    var bitmap by remember(account.getUuid()) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(account.getUuid()) {
        bitmap = withContext(Dispatchers.IO) { actions.avatarFor(account) }?.asImageBitmap()
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(bitmap = b, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Icon(painterResource(R.drawable.ic_person_24dp), null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
private fun AccountCard(
    account: Account,
    revision: Int,
    startExpanded: Boolean,
    serverInfoOn: Boolean,
    onServerInfoChange: (Boolean) -> Unit,
    actions: AccountsHubActions,
) {
    var expanded by remember(account.getUuid()) { mutableStateOf(startExpanded) }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, spring(stiffness = 380f, dampingRatio = 0.8f), label = "arrow")
    val online = account.isOnlineAndConnected()
    val statusText = stringResource(account.getStatus().getReadableId())
    val isError = remember(revision) { account.getStatus().isError() }
    Surface(
        shape = RoundedCornerShape(28.dp),
        // One tonal step lighter than before (Low, not High); the rows on top of it are one step
        // lighter too (surfaceContainer, not Highest), so they stay darker than their card.
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().animateContentSize(spring(stiffness = 380f, dampingRatio = 0.8f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(account, actions, 48, onClick = null)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        account.getJid().asBareJid().toString(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = account.isEnabled(),
                    onCheckedChange = { actions.onToggleEnabled(account, it) },
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        painterResource(R.drawable.ic_expand_more_24dp),
                        contentDescription = stringResource(if (expanded) R.string.hub_collapse else R.string.hub_expand),
                        modifier = Modifier.rotate(arrow),
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(stiffness = 380f, dampingRatio = 0.8f)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = 380f, dampingRatio = 0.8f)) + fadeOut(),
            ) {
                AccountDetails(account, revision, online, serverInfoOn, onServerInfoChange, actions)
            }
        }
    }
}

@Composable
private fun AccountDetails(
    account: Account,
    revision: Int,
    online: Boolean,
    serverInfoOn: Boolean,
    onServerInfoChange: (Boolean) -> Unit,
    actions: AccountsHubActions,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Profile picture: tapping it goes to the same picker flow as before.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(account, actions, 72, onClick = { actions.onAvatar(account) })
            Spacer(Modifier.width(14.dp))
            OutlinedTextField(
                value = account.getJid().asBareJid().toString(),
                onValueChange = {},
                label = { Text(stringResource(R.string.account_settings_jabber_id)) },
                enabled = false,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        val name = remember(revision) { account.getDisplayName() }
        DetailRow(
            label = stringResource(R.string.your_name),
            value = if (name.isNullOrBlank()) stringResource(R.string.no_name_set_instructions) else name,
            muted = name.isNullOrBlank(),
            onEdit = { actions.onEditName(account) },
        )
        if (online) {
            val status = remember(revision) { account.getPresenceStatusMessage() }
            DetailRow(
                label = stringResource(R.string.hub_status_message),
                value = if (status.isNullOrBlank()) stringResource(R.string.hub_no_status) else status,
                muted = status.isNullOrBlank(),
                onEdit = { actions.onEditStatus(account) },
            )
            val birthday = remember(revision) { actions.birthdayOf(account) }
            DetailRow(
                label = stringResource(R.string.birthday_label),
                value = birthday?.describe(context) ?: stringResource(R.string.birthday_not_set),
                muted = birthday == null,
                onEdit = { actions.onEditBirthday(account) },
            )
            DetailRow(
                label = stringResource(R.string.server_info_session_established),
                value = remember(revision) { sessionEstablishedText(context, account) },
                muted = false,
                onEdit = null,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.server_info_show_more)) },
                trailingContent = { Switch(checked = serverInfoOn, onCheckedChange = onServerInfoChange) },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onServerInfoChange(!serverInfoOn) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
            AnimatedVisibility(visible = serverInfoOn) {
                ServerInfoBlock(account, revision, actions)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, muted: Boolean, onEdit: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (onEdit != null) Modifier.clickable(onClick = onEdit) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (onEdit != null) {
            Icon(painterResource(R.drawable.ic_edit_24dp), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ServerInfoBlock(account: Account, revision: Int, actions: AccountsHubActions) {
    val context = LocalContext.current
    val rows = remember(revision) { serverInfoRows(context, account) }
    val fingerprint = remember(revision) { ownFingerprintText(account) }
    val devices = remember(revision) { otherDeviceKeys(account) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(row.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(row.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
        if (fingerprint != null) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.omemo_fingerprint), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Text(fingerprint, style = MaterialTheme.typography.bodyMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { actions.onShowQr(account) }) {
                    Text(stringResource(R.string.show_qr_code))
                }
            }
        }
        if (devices.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.other_devices), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            devices.forEach { key ->
                Column {
                    Text(key.fingerprint, style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text(
                        stringResource(if (key.verified) R.string.hub_verified_device else R.string.hub_unverified_device),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (fingerprint != null || devices.isNotEmpty()) {
            TextButton(onClick = { actions.onDeviceKeys(account) }) {
                Text(stringResource(R.string.hub_device_keys))
            }
        }
    }
}

@Composable
private fun SettingsCard(entries: List<SettingsEntry>) {
    var expanded by remember { mutableStateOf(true) }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, spring(stiffness = 380f, dampingRatio = 0.8f), label = "arrow")
    Surface(
        shape = RoundedCornerShape(28.dp),
        // One tonal step lighter than before (Low, not High); the rows on top of it are one step
        // lighter too (surfaceContainer, not Highest), so they stay darker than their card.
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().animateContentSize(spring(stiffness = 380f, dampingRatio = 0.8f)),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.hub_settings_section), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(painterResource(R.drawable.ic_expand_more_24dp), null, modifier = Modifier.rotate(arrow))
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(stiffness = 380f, dampingRatio = 0.8f)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = 380f, dampingRatio = 0.8f)) + fadeOut(),
            ) {
                Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    entries.forEachIndexed { index, e ->
                        val top = if (index == 0) 20.dp else 6.dp
                        val bottom = if (index == entries.lastIndex) 20.dp else 6.dp
                        ListItem(
                            leadingContent = {
                                Icon(painterResource(e.icon), null, tint = MaterialTheme.colorScheme.primary)
                            },
                            headlineContent = { Text(e.title) },
                            supportingContent = e.summary?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                            modifier = Modifier
                                .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
                                .combinedClickable(onClick = e.onClick, onLongClick = e.onLongClick),
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        )
                    }
                }
            }
        }
    }
}

// ─── Dialogs ─────────────────────────────────────────────────────────────────

@Composable
internal fun TextEditDialog(
    title: String,
    icon: Int,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(icon), contentDescription = null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            Button(onClick = { onConfirm(text.trim()) }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun AccountChooserDialog(
    accounts: List<Account>,
    actions: AccountsHubActions,
    onChoose: (Account) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_person_24dp), contentDescription = null) },
        title = { Text(stringResource(R.string.hub_choose_account)) },
        text = {
            Column {
                Text(stringResource(R.string.hub_choose_account_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                accounts.forEach { account ->
                    ListItem(
                        leadingContent = { Avatar(account, actions, 40, onClick = null) },
                        headlineContent = { Text(account.getJid().asBareJid().toString(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onChoose(account) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun MamPrefsDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var choice by remember { mutableStateOf(initial) }
    val labels = listOf(R.string.never, R.string.contacts, R.string.always)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_archive_24dp), contentDescription = null) },
        title = { Text(stringResource(R.string.server_side_mam_prefs)) },
        text = {
            Column {
                labels.forEachIndexed { i, label ->
                    ListItem(
                        leadingContent = { RadioButton(selected = choice == i, onClick = null) },
                        headlineContent = { Text(stringResource(label)) },
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { choice = i },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(choice) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
