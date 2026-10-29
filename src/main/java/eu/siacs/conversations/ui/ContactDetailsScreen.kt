package eu.siacs.conversations.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.glass.LiquidGlassButton
import eu.siacs.conversations.ui.glass.rememberGlassStack
import eu.siacs.conversations.ui.util.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/*
 * The contact screen, in Compose. A head that is the contact's picture (see ProfileHeader.kt), a row of actions
 * (Material 3 Expressive button group: pressing one widens it and squeezes the others; the ones that do not fit
 * go into its overflow menu), then Expressive cards: who the person is, what they share with you, the recent media,
 * and at the very bottom the encryption keys. In the glass style the picture is glassy and the two buttons on it are
 * glass; the cards below stay Material.
 */

@Composable
fun ContactDetailsScreen(state: ContactDetailsState, actions: ContactDetailsActions) {
    val ui = state.ui
    val glass = rememberLiquidGlassEnabled()
    val density = LocalDensity.current
    val view = LocalView.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    // The picture.
    var avatarBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(ui?.avatarKey, ui?.name) {
        if (ui == null) return@LaunchedEffect
        avatarBitmap = withContext(Dispatchers.IO) { actions.avatar(1024) }
    }
    val avatarImage: ImageBitmap? = remember(avatarBitmap) { avatarBitmap?.asImageBitmap() }
    val tint = remember(avatarBitmap) { averageColor(avatarBitmap) }

    // Pulling down at the top: the big picture stretches and springs back.
    val pull = remember { Animatable(0f) }
    val connection = remember(scroll) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pull.value > 0f && available.y < 0f) {
                    val use = min(pull.value, -available.y)
                    scope.launch { pull.snapTo(pull.value - use) }
                    return Offset(0f, -use)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && scroll.value == 0 && source == NestedScrollSource.UserInput) {
                    // A rubber band: the further it is pulled, the less it gives.
                    val cap = with(density) { 160.dp.toPx() }
                    val next = pull.value + available.y * (1f - pull.value / cap).coerceIn(0.1f, 1f) * 0.6f
                    scope.launch { pull.snapTo(next.coerceAtMost(cap)) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (pull.value > 0f) pull.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 260f))
                return Velocity.Zero
            }
        }
    }

    val backdrop = rememberLayerBackdrop()
    val glassStack = rememberGlassStack(backdrop)
    val tilt = rememberTilt(glass)

    BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).nestedScroll(connection),
    ) {
        val width = with(density) { maxWidth.toPx() }
        val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
        val bigHeight = min(width, with(density) { 440.dp.toPx() })
        val circle = with(density) { 124.dp.toPx() }
        val circleTop = statusTop + with(density) { 64.dp.toPx() }
        // Where the camera is (the hole in the screen), or the middle of the top edge when there is none.
        val dock = remember(view, width, statusTop) { cameraSpot(view, width, statusTop) }
        val metrics = HeaderMetrics(
            width = width,
            bigHeight = bigHeight,
            toCircle = with(density) { 150.dp.toPx() },
            toCamera = with(density) { 150.dp.toPx() } + with(density) { 150.dp.toPx() },
            circle = circle,
            circleTop = circleTop,
            dockX = dock.x,
            dockY = dock.y,
            dockSize = with(density) { 18.dp.toPx() },
            bigCorner = with(density) { 36.dp.toPx() },
        )
        val s = scroll.value.toFloat()

        // The status bar's icons: light over the picture, dark once the bar has its own light background.
        val activity = LocalContext.current as? android.app.Activity
        val dark = androidx.compose.foundation.isSystemInDarkTheme()
        val barSolid = ((s - metrics.toCircle) / (metrics.toCamera - metrics.toCircle)).coerceIn(0f, 1f) > 0.5f
        androidx.compose.runtime.SideEffect {
            activity?.window?.let {
                androidx.core.view.WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars = !dark && barSolid
            }
        }

        // After a scroll that stops half-way through the head's range it settles on the nearest of its three states.
        LaunchedEffect(scroll.isScrollInProgress) {
            if (scroll.isScrollInProgress) return@LaunchedEffect
            val v = scroll.value
            val stops = listOf(0, metrics.toCircle.toInt(), metrics.toCamera.toInt())
            if (v in 1 until stops.last()) {
                val nearest = stops.minByOrNull { kotlin.math.abs(it - v) } ?: v
                if (nearest != v) scroll.animateScrollTo(nearest, spring(dampingRatio = 0.85f, stiffness = 220f))
            }
        }

        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            if (ui != null) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    Spacer(Modifier.height(with(density) { bigHeight.toDp() }))
                    DetailsBody(ui, state, actions)
                    Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(40.dp))
                }
                ProfileHeader(
                    bitmap = avatarImage,
                    tint = tint,
                    name = ui.name,
                    presence = ui.presenceLabel,
                    scroll = s,
                    pull = pull.value,
                    metrics = metrics,
                    glass = glass,
                    tilt = tilt,
                    onClick = actions::onAvatar,
                )
                BoxScopeName(ui.name, ui.presenceLabel, s, metrics, Modifier.fillMaxWidth())
            }
        }

        // The bar over the head: a back button, and the name once the picture has gone into the camera.
        TopBar(
            title = ui?.name.orEmpty(),
            scroll = s,
            metrics = metrics,
            glass = glass,
            glassStack = glassStack,
            onBack = actions::onBack,
        )
    }
}

/** The camera's place in the window: the centre of the top cut-out when the phone has one. */
private fun cameraSpot(view: android.view.View, width: Float, statusTop: Float): Offset {
    val cutout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        view.rootWindowInsets?.displayCutout?.boundingRects?.firstOrNull { it.top <= 8 && it.width() > 0 }
    } else null
    return if (cutout != null) {
        Offset(cutout.exactCenterX(), cutout.exactCenterY())
    } else {
        Offset(width / 2f, statusTop / 2f)
    }
}

@Composable
private fun TopBar(
    title: String,
    scroll: Float,
    metrics: HeaderMetrics,
    glass: Boolean,
    glassStack: eu.siacs.conversations.ui.glass.GlassStack,
    onBack: () -> Unit,
) {
    val p = ((scroll - metrics.toCircle) / (metrics.toCamera - metrics.toCircle)).coerceIn(0f, 1f)
    val surface = MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxSize()) {
        // The bar's own background, once the picture is gone.
        Box(
            Modifier.fillMaxWidth().height(110.dp)
                .background(Brush.verticalGradient(listOf(surface.copy(alpha = 0.92f * p), Color.Transparent))),
        )
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val back: @Composable () -> Unit = {
                Icon(painterResource(R.drawable.ic_arrow_back_24dp), contentDescription = stringResource(R.string.back))
            }
            if (glass) {
                LiquidGlassButton(onClick = onBack, glass = glassStack, modifier = Modifier.size(48.dp)) { back() }
            } else {
                FilledTonalIconButton(
                    onClick = onBack,
                    colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                    ),
                ) { back() }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).graphicsAlpha(((p - 0.6f) / 0.4f).coerceIn(0f, 1f)),
            )
        }
    }
}

private fun Modifier.graphicsAlpha(alpha: Float): Modifier = this.alpha(alpha)

// ─── Body ────────────────────────────────────────────────────────────────────────

@Composable
private fun DetailsBody(ui: ContactDetailsUi, state: ContactDetailsState, actions: ContactDetailsActions) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (ui.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ui.tags.forEach { TagChip(it, onClick = {}) }
            }
        }
        ActionRow(ui, actions)
        if (ui.statusMessages.isNotEmpty()) StatusCard(ui)
        AboutCard(ui, actions)
        if (ui.inRoster && ui.send != null && ui.receive != null) PresenceCard(ui, actions)
        if (state.mediaAllowed && state.media.isNotEmpty()) MediaCard(state.media, actions)
        KeysSection(ui, actions)
    }
}

private class ActionItem(
    val icon: Int,
    val label: String,
    val onClick: () -> Unit,
)

@Composable
private fun ActionRow(ui: ContactDetailsUi, actions: ContactDetailsActions) {
    val items = buildList {
        if (!ui.inRoster) add(ActionItem(R.drawable.ic_person_add_24dp, stringResource(R.string.add_contact), actions::onAddToRoster))
        add(ActionItem(R.drawable.ic_chat_24dp, stringResource(R.string.contact_action_message), actions::onMessage))
        ui.addressBookLabel?.let { add(ActionItem(R.drawable.ic_contacts_24dp, stringResource(it), actions::onAddressBook)) }
        add(ActionItem(R.drawable.ic_share_24dp, stringResource(R.string.share_invitation)) { actions.onShareLink(true) })
        add(ActionItem(R.drawable.ic_qr_code_scanner_24dp, stringResource(R.string.share_as_uri)) { actions.onShareLink(false) })
        if (ui.canEditDelete) add(ActionItem(R.drawable.ic_edit_24dp, stringResource(R.string.action_edit_contact), actions::onEdit))
        if (ui.customNotifications) add(ActionItem(R.drawable.ic_notifications_24dp, stringResource(R.string.custom_notifications), actions::onCustomNotifications))
        if (ui.canBlock) add(
            ActionItem(
                R.drawable.ic_cancel_24dp,
                stringResource(if (ui.blocked) R.string.action_unblock_contact else R.string.action_block_contact),
                actions::onBlock,
            ),
        )
        if (ui.canEditDelete) add(ActionItem(R.drawable.ic_delete_24dp, stringResource(R.string.action_delete_contact), actions::onDelete))
    }
    // The buttons that fit sit in a row; the rest are behind the last one ("More"). (The button group of the
    // Material library measures itself with a negative width when its labels do not fit, and crashes; this row
    // works out how many fit by itself.)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val slots = (maxWidth / 92.dp).toInt().coerceAtLeast(2)
        val overflow = items.size > slots
        val shown = if (overflow) items.take(slots - 1) else items
        val rest = if (overflow) items.drop(slots - 1) else emptyList()
        var menu by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            shown.forEach { item -> ActionButton(item.icon, item.label, item.onClick, Modifier.weight(1f)) }
            if (overflow) {
                Box(Modifier.weight(1f)) {
                    ActionButton(R.drawable.ic_more_vert_24dp, stringResource(R.string.contact_action_more), { menu = true }, Modifier.fillMaxWidth())
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        rest.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.label) },
                                leadingIcon = { Icon(painterResource(item.icon), contentDescription = null) },
                                onClick = { menu = false; item.onClick() },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** An icon over a label in a tonal tile: pressed, it swells and rounds off (like the library's button group does). */
@Composable
private fun ActionButton(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 30.dp else 18.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "actionCorner")
    val scale by animateFloatAsState(if (pressed) 1.08f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "actionScale")
    androidx.compose.material3.Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 14.dp, top = 6.dp),
    )
}

@Composable
private fun DetailRow(
    position: GroupPosition,
    icon: Int,
    title: String,
    summary: String?,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ExpressiveGroupRow(position) {
        ListItem(
            leadingContent = { Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(title) },
            supportingContent = summary?.let { { Text(it) } },
            trailingContent = trailing,
            modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun StatusCard(ui: ContactDetailsUi) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ui.statusMessages.forEachIndexed { i, message ->
            val position = when {
                ui.statusMessages.size == 1 -> GroupPosition.SINGLE
                i == 0 -> GroupPosition.TOP
                i == ui.statusMessages.lastIndex -> GroupPosition.BOTTOM
                else -> GroupPosition.MIDDLE
            }
            ExpressiveGroupRow(position) {
                Text(
                    message,
                    style = if (eu.siacs.conversations.utils.Emoticons.isOnlyEmoji(message)) MaterialTheme.typography.displaySmall else MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun AboutCard(ui: ContactDetailsUi, actions: ContactDetailsActions) {
    data class Row(val icon: Int, val title: String, val summary: String?, val click: (() -> Unit)?)
    val rows = buildList {
        add(Row(R.drawable.ic_person_24dp, ui.address, stringResource(R.string.contact_card_address_hint), actions::onCopyAddress))
        add(Row(R.drawable.ic_contacts_24dp, stringResource(R.string.using_account, ui.account), null, actions::onAccount))
        ui.birthdayText?.let { add(Row(R.drawable.ic_info_outline_24dp, (if (ui.birthdayToday) "🎂 " else "") + it, null, null)) }
        add(Row(R.drawable.ic_info_outline_24dp, ui.genderText, null, actions::onGender))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { i, r ->
            val position = when {
                rows.size == 1 -> GroupPosition.SINGLE
                i == 0 -> GroupPosition.TOP
                i == rows.lastIndex -> GroupPosition.BOTTOM
                else -> GroupPosition.MIDDLE
            }
            DetailRow(position, r.icon, r.title, r.summary, r.click)
        }
    }
}

@Composable
private fun PresenceCard(ui: ContactDetailsUi, actions: ContactDetailsActions) {
    val send = ui.send ?: return
    val receive = ui.receive ?: return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        DetailRow(
            GroupPosition.TOP, R.drawable.ic_visibility_24dp, send.label, null,
            onClick = if (send.enabled) ({ actions.onSendPresence(!send.checked) }) else null,
            trailing = { Switch(checked = send.checked, onCheckedChange = if (send.enabled) actions::onSendPresence else null, enabled = send.enabled) },
        )
        DetailRow(
            GroupPosition.BOTTOM, R.drawable.ic_visibility_24dp, receive.label, null,
            onClick = if (receive.enabled) ({ actions.onReceivePresence(!receive.checked) }) else null,
            trailing = { Switch(checked = receive.checked, onCheckedChange = if (receive.enabled) actions::onReceivePresence else null, enabled = receive.enabled) },
        )
    }
}

@Composable
private fun MediaCard(media: List<Attachment>, actions: ContactDetailsActions) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.contact_details_media))
            Spacer(Modifier.weight(1f))
            TextButton(onClick = actions::onViewMedia) { Text(stringResource(R.string.view_media)) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(media.take(12), key = { it.uuid }) { attachment ->
                val density = LocalDensity.current
                val sizePx = with(density) { 96.dp.toPx() }.toInt()
                var preview by remember(attachment) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(attachment) {
                    preview = withContext(Dispatchers.IO) { actions.preview(attachment, sizePx) }?.asImageBitmap()
                }
                Box(
                    Modifier.size(96.dp).clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { actions.onOpenAttachment(attachment) },
                    contentAlignment = Alignment.Center,
                ) {
                    val p = preview
                    if (p != null) {
                        Image(bitmap = p, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(
                            painterResource(eu.siacs.conversations.ui.adapter.MediaAdapter.getImageDrawable(attachment)),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }
    }
}

// ─── Keys (the bottom of the screen) ─────────────────────────────────────────────

@Composable
private fun KeysSection(ui: ContactDetailsUi, actions: ContactDetailsActions) {
    if (ui.keys.isEmpty() && ui.pgpKeyHex == null && ui.inactiveToggleLabel == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.contact_details_keys))
        if (ui.unverifiedWarning) {
            ExpressiveGroupRow(GroupPosition.SINGLE) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_warning_24dp), contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.unverified_devices), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val total = ui.keys.size + (if (ui.pgpKeyHex != null) 1 else 0)
            ui.keys.forEachIndexed { i, key ->
                val position = when {
                    total == 1 -> GroupPosition.SINGLE
                    i == 0 -> GroupPosition.TOP
                    i == total - 1 -> GroupPosition.BOTTOM
                    else -> GroupPosition.MIDDLE
                }
                FingerprintItem(position, key, actions)
            }
            if (ui.pgpKeyHex != null) {
                DetailRow(
                    if (total == 1) GroupPosition.SINGLE else GroupPosition.BOTTOM,
                    R.drawable.ic_lock_24dp,
                    ui.pgpKeyHex,
                    stringResource(R.string.openpgp_key_id),
                    onClick = actions::onPgpKey,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ui.inactiveToggleLabel?.let { label ->
                TextButton(onClick = actions::onToggleInactive) { Text(stringResource(label)) }
            }
            Spacer(Modifier.weight(1f))
            if (ui.canScan) {
                FilledTonalButton(onClick = actions::onScan) {
                    Icon(painterResource(R.drawable.ic_qr_code_scanner_24dp), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scan_qr_code))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FingerprintItem(position: GroupPosition, key: FingerprintRowUi, actions: ContactDetailsActions) {
    var menu by remember { mutableStateOf(false) }
    Box {
        ExpressiveGroupRow(position) {
            Row(
                Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { menu = true }).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            key.highlighted -> stringResource(if (key.x509) R.string.omemo_fingerprint_x509_selected_message else R.string.omemo_fingerprint_selected_message)
                            else -> stringResource(if (key.x509) R.string.omemo_fingerprint_x509 else R.string.omemo_fingerprint)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (key.highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    SelectionContainer {
                        Text(
                            key.pretty,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = if (key.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!key.active) {
                        Text(stringResource(R.string.this_device_is_no_longer_in_use), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(10.dp))
                when {
                    key.verified -> Icon(
                        painterResource(R.drawable.ic_verified_user_24dp),
                        contentDescription = stringResource(R.string.this_device_has_been_verified),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.graphicsAlpha(if (key.active) 1f else 0.45f),
                    )
                    key.active && key.undecided -> TextButton(onClick = { actions.onEnableDevice(key) }) { Text(stringResource(R.string.contact_enable_device)) }
                    else -> Switch(checked = key.trusted, onCheckedChange = if (key.active) { v -> actions.onTrust(key, v) } else null, enabled = key.active)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (key.active && !key.verified) {
                DropdownMenuItem(text = { Text(stringResource(R.string.scan_qr_code)) }, onClick = { menu = false; actions.onScan() })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.copy_fingerprint)) }, onClick = { menu = false; actions.onCopyKey(key) })
            if (key.verified || (!key.active && key.trusted)) {
                DropdownMenuItem(text = { Text(stringResource(R.string.distrust_omemo_key)) }, onClick = { menu = false; actions.onDistrust(key) })
            }
        }
    }
}
