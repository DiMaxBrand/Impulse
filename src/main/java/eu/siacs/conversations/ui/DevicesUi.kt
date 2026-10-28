package eu.siacs.conversations.ui

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.utils.AccountDevices
import eu.siacs.conversations.xmpp.manager.DevicesManager
import eu.siacs.conversations.xmpp.manager.PresenceManager
import im.conversations.android.xmpp.model.stanza.Presence

/*
 * The devices of an account: a section in the accounts screen (hidden while there is only one
 * device), and a one-time card at launch when another device of the account is set to silent. See
 * DevicesManager for how a device is left out of the status.
 */

/** One device of an account, for display. */
internal data class DeviceRow(
    val resource: String,
    val name: String,
    val version: String,
    val isThis: Boolean,
    /** What the server shows for it now; null while it is offline or this very device. */
    val availability: Presence.Availability?,
    val excluded: Boolean,
    /** Runs a version without the devices list: it can only be asked, and has to update first. */
    val needsUpdate: Boolean,
    /** For a request still waiting for such a device: when it lapses. */
    val pendingUntil: Long,
    val seen: Long,
)

private const val OFFLINE_SHOWN_MS = 30L * 24 * 60 * 60 * 1000

internal fun deviceRows(account: Account): List<DeviceRow> {
    val connection = account.getXmppConnection() ?: return emptyList()
    val manager = connection.getManager(DevicesManager::class.java)
    val entries = manager.entries()
    val ownId = manager.ownDeviceId
    val ownResource = account.getResource()
    val online =
        connection.getManager(PresenceManager::class.java)
            .getPresences(account.getJid().asBareJid())
            .mapNotNull { p -> p.getFrom()?.resource?.let { it to p.getAvailability() } }
            .toMap()
    val now = System.currentTimeMillis()
    val rows = ArrayList<DeviceRow>()
    val thisEntry = entries.firstOrNull { it.id == ownId }
    rows += DeviceRow(
        ownResource, thisEntry?.name.orEmpty(), thisEntry?.version.orEmpty(), true, null,
        thisEntry?.excluded == true, false, 0, now,
    )
    for (e in entries) {
        if (e.id == ownId || e.resource == ownResource) continue
        val availability = online[e.resource]
        if (e.pending) {
            if (e.until > now || e.until == 0L) {
                rows += DeviceRow(e.resource, "", "", false, availability, e.excluded, true, e.until, e.seen)
            }
            continue
        }
        if (availability == null && now - e.seen > OFFLINE_SHOWN_MS) continue
        rows += DeviceRow(e.resource, e.name, e.version, false, availability, e.excluded, false, 0, e.seen)
    }
    for ((resource, availability) in online) {
        if (resource == ownResource || entries.any { it.resource == resource }) continue
        rows += DeviceRow(resource, "", "", false, availability, false, true, 0, now)
    }
    return rows
}

/** Another device of this account that is on silent while this one is not, and was not left as it is. */
internal fun silentOtherDevice(account: Account, context: android.content.Context): DeviceRow? {
    if (!account.isOnlineAndConnected()) return null
    val rows = deviceRows(account)
    if (rows.size < 2) return null
    if (rows.first { it.isThis }.excluded) return null
    val store = AccountDevices(context)
    return rows.firstOrNull {
        !it.isThis && it.availability == Presence.Availability.DND && !it.excluded &&
            !(it.needsUpdate && it.pendingUntil > 0) && !store.isKept(account.getJid().asBareJid().toString() + "/" + it.resource)
    }
}

@Composable
private fun availabilityLabel(a: Presence.Availability?): String =
    when (a) {
        Presence.Availability.CHAT, Presence.Availability.ONLINE -> stringResource(R.string.presence_online)
        Presence.Availability.AWAY -> stringResource(R.string.presence_away)
        Presence.Availability.XA -> stringResource(R.string.presence_xa)
        Presence.Availability.DND -> stringResource(R.string.presence_dnd)
        else -> stringResource(R.string.devices_offline)
    }

/**
 * The devices of one account, shown inside that account's card (only when it has more than one
 * device): which ones count in the status, rename, details. Each device is keyed by its resource, so
 * a device coming or going between two compositions cannot put the rows out of step (BUG-EGYB).
 */
@Composable
internal fun DevicesBlock(account: Account, revision: Int) {
    var tick by remember(account.getUuid()) { mutableIntStateOf(0) }
    val rows = remember(revision, tick, account) { deviceRows(account) }
    if (rows.size >= 2) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .animateContentSize(spring(stiffness = 380f, dampingRatio = 0.8f))
                .padding(vertical = 10.dp),
        ) {
            Text(
                stringResource(R.string.devices_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                stringResource(R.string.devices_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            rows.forEach { row ->
                androidx.compose.runtime.key(row.resource) {
                    DeviceItem(account, row, rows) { tick++ ; Unit }
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(account: Account, row: DeviceRow, all: List<DeviceRow>, changed: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    val manager = account.getXmppConnection().getManager(DevicesManager::class.java)
    val title = when {
        row.name.isNotBlank() -> row.name
        row.needsUpdate -> stringResource(R.string.devices_other_unnamed)
        else -> android.os.Build.MODEL
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_smartphone_24dp), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val status = when {
                row.isThis -> stringResource(R.string.devices_this) + " · " +
                    stringResource(if (row.excluded) R.string.devices_excluded else R.string.devices_included)
                row.needsUpdate && row.excluded && row.pendingUntil > 0 -> stringResource(
                    R.string.devices_pending,
                    DateUtils.formatDateTime(context, row.pendingUntil, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE),
                )
                row.excluded -> stringResource(R.string.devices_excluded) + " · " + availabilityLabel(row.availability)
                else -> availabilityLabel(row.availability)
            }
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (row.needsUpdate && !(row.excluded && row.pendingUntil > 0)) {
                Text(
                    stringResource(R.string.devices_needs_update),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(painterResource(R.drawable.ic_more_vert_24dp), contentDescription = null)
            }
            ExpressiveDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                ExpressiveMenuItem(
                    if (row.excluded) R.drawable.ic_check_circle_24dp else R.drawable.ic_do_not_disturb_on_24dp,
                    stringResource(if (row.excluded) R.string.devices_include else R.string.devices_exclude),
                ) {
                    menu = false
                    val result = manager.setExcluded(row.resource, !row.excluded)
                    if (result == DevicesManager.Result.LAST_DEVICE) {
                        Toast.makeText(context, R.string.devices_last_device, Toast.LENGTH_LONG).show()
                    }
                    if (row.excluded) {
                        AccountDevices(context).forgetKept(account.getJid().asBareJid().toString() + "/" + row.resource)
                    }
                    changed()
                }
                if (!row.needsUpdate) {
                    ExpressiveMenuItem(R.drawable.ic_edit_24dp, stringResource(R.string.devices_rename)) {
                        menu = false; rename = true
                    }
                }
                ExpressiveMenuItem(R.drawable.ic_info_outline_24dp, stringResource(R.string.devices_details)) {
                    menu = false; details = true
                }
            }
        }
    }
    if (rename) {
        var text by remember { mutableStateOf(title) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text(stringResource(R.string.devices_rename)) },
            text = { OutlinedTextField(text, { text = it.take(40) }, singleLine = true) },
            confirmButton = {
                Button(onClick = { manager.rename(row.resource, text); rename = false; changed() }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = { TextButton(onClick = { rename = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    if (details) {
        AlertDialog(
            onDismissRequest = { details = false },
            title = { Text(title) },
            text = {
                Text(
                    listOf(
                        stringResource(R.string.devices_session, row.resource),
                        if (row.version.isNotBlank()) stringResource(R.string.devices_version, row.version) else null,
                        if (!row.isThis && row.seen > 0 && row.availability == null) stringResource(
                            R.string.devices_last_seen,
                            DateUtils.getRelativeTimeSpanString(row.seen).toString(),
                        ) else null,
                    ).filterNotNull().joinToString("\n"),
                )
            },
            confirmButton = { TextButton(onClick = { details = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}

/**
 * Shown once at launch when another device of the account is on silent, so the person sees as
 * silent: leave it out of the status, or keep it as it is. The card changes after "Exclude" to say
 * what happens next.
 */
class DeviceSilentSheetFragment : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            val accountJid = requireArguments().getString(ARG_ACCOUNT).orEmpty()
            val resource = requireArguments().getString(ARG_RESOURCE).orEmpty()
            val name = requireArguments().getString(ARG_NAME).orEmpty()
            setContent {
                ImpulseExpressiveTheme {
                    var result by remember { mutableStateOf<DevicesManager.Result?>(null) }
                    val context = LocalContext.current
                    val key = "$accountJid/$resource"
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_smartphone_24dp), null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp).padding(bottom = 4.dp),
                        )
                        AnimatedContent(targetState = result, label = "deviceCard") { r ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    stringResource(
                                        when (r) {
                                            null -> R.string.device_prompt_title
                                            DevicesManager.Result.PENDING_UPDATE -> R.string.device_prompt_pending_title
                                            else -> R.string.device_prompt_done_title
                                        },
                                    ),
                                    style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                                )
                                Text(
                                    stringResource(
                                        when (r) {
                                            null -> R.string.device_prompt_body
                                            DevicesManager.Result.PENDING_UPDATE -> R.string.device_prompt_pending_body
                                            else -> R.string.device_prompt_done_body
                                        },
                                        name,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                                )
                                if (r == null) {
                                    Button(
                                        onClick = {
                                            val account = (activity as? XmppActivity)?.xmppConnectionService
                                                ?.accounts?.firstOrNull { it.getJid().asBareJid().toString() == accountJid }
                                            result = account?.getXmppConnection()?.getManager(DevicesManager::class.java)
                                                ?.setExcluded(resource, true) ?: DevicesManager.Result.NOT_AVAILABLE
                                            if (result == DevicesManager.Result.LAST_DEVICE ||
                                                result == DevicesManager.Result.NOT_AVAILABLE
                                            ) {
                                                dismiss()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text(stringResource(R.string.device_prompt_exclude)) }
                                    TextButton(
                                        onClick = { AccountDevices(context).keep(key); dismiss() },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            stringResource(R.string.device_prompt_keep),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    Button(onClick = { dismiss() }, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(android.R.string.ok))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }

    companion object {
        const val TAG = "DeviceSilentSheet"
        private const val ARG_ACCOUNT = "account"
        private const val ARG_RESOURCE = "resource"
        private const val ARG_NAME = "name"
        private val askedThisRun = HashSet<String>()

        /** Opens the card if some device is on silent that has not been dealt with; at most once per run for each. */
        @JvmStatic
        fun maybeShow(activity: XmppActivity) {
            // Roster updates arrive on a background thread.
            activity.runOnUiThread { showIfNeeded(activity) }
        }

        private fun showIfNeeded(activity: XmppActivity) {
            if (activity.isFinishing || activity.isDestroyed) return
            val service = activity.xmppConnectionService ?: return
            val fm = activity.supportFragmentManager
            if (fm.isStateSaved || fm.findFragmentByTag(TAG) != null || fm.findFragmentByTag(UpdateSheetFragment.TAG) != null) return
            for (account in service.accounts) {
                val row = silentOtherDevice(account, activity) ?: continue
                val key = account.getJid().asBareJid().toString() + "/" + row.resource
                if (!askedThisRun.add(key)) continue
                DeviceSilentSheetFragment().apply {
                    arguments = Bundle().apply {
                        putString(ARG_ACCOUNT, account.getJid().asBareJid().toString())
                        putString(ARG_RESOURCE, row.resource)
                        putString(ARG_NAME, row.name.ifBlank { activity.getString(R.string.devices_other_unnamed) })
                    }
                }.show(fm, TAG)
                return
            }
        }
    }
}
