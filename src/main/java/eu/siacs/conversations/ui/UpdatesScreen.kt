package eu.siacs.conversations.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import eu.siacs.conversations.R
import eu.siacs.conversations.update.UpdateChannel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    state: UpdatesUiState,
    onChannelSelected: (UpdateChannel) -> Unit,
    onAutoCheckToggled: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onDownload: () -> Unit,
    onStop: () -> Unit,
    onContinue: () -> Unit,
    onInstall: () -> Unit,
    onConfirmInstall: () -> Unit,
    onDownloadCircleTapped: () -> Unit = {},
    onShowUpdateSheet: () -> Unit = {},
    onHideUpdateSheet: () -> Unit = {},
) {
    var channelPickerVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Group 1 — Current version
        ExpressiveGroupRow(GroupPosition.SINGLE) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.updates_current_version_label)) },
                trailingContent = {
                    Text(
                        text = state.currentVersion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        Spacer(Modifier.height(6.dp))

        // Group 2 — Channel selector
        ExpressiveGroupRow(GroupPosition.SINGLE) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.updates_channel_label)) },
                supportingContent = {
                    Text(
                        text = stringResource(channelDisplayName(state.selectedChannel)),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickableRow { channelPickerVisible = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        Spacer(Modifier.height(6.dp))

        // Group 3 — Actions
        ExpressiveGroupRow(GroupPosition.TOP) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.updates_auto_check_label)) },
                trailingContent = {
                    Switch(
                        checked = state.autoCheck,
                        onCheckedChange = onAutoCheckToggled,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        ExpressiveGroupRow(GroupPosition.BOTTOM) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.updates_check_now)) },
                modifier = Modifier.clickableRow(onClick = onCheckNow),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Inline status for idle states (checking / up-to-date / new version found)
        val mainText = mainStatusText(state)
        AnimatedContent(
            targetState = mainText,
            transitionSpec = {
                (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 3 } + fadeOut())
            },
            label = "main_status_text",
        ) { text ->
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Box(Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.height(4.dp))

        // Debug: trigger the update bottom sheet with a fake pending update
        FilledTonalButton(
            onClick = onShowUpdateSheet,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Show update bottom sheet")
        }
    }

    if (channelPickerVisible) {
        ChannelPickerDialog(
            selectedChannel = state.selectedChannel,
            onChannelSelected = { channel ->
                onChannelSelected(channel)
                channelPickerVisible = false
            },
            onDismiss = { channelPickerVisible = false },
        )
    }

    // Update flow bottom sheet
    if (state.showUpdateSheet) {
        ModalBottomSheet(onDismissRequest = onHideUpdateSheet) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.pendingVersion != null) {
                    Text(
                        text = stringResource(R.string.updates_new_version_available, state.pendingVersion),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
                StatusSection(
                    state = state,
                    onDownload = onDownload,
                    onStop = onStop,
                    onContinue = onContinue,
                    onInstall = onInstall,
                    onConfirmInstall = onConfirmInstall,
                    onDownloadCircleTapped = onDownloadCircleTapped,
                )
            }
        }
    }
}

// ─── Status + Download button ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusSection(
    state: UpdatesUiState,
    onDownload: () -> Unit,
    onStop: () -> Unit,
    onContinue: () -> Unit,
    onInstall: () -> Unit,
    onConfirmInstall: () -> Unit,
    onDownloadCircleTapped: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Status text for download phases — slides on change
        AnimatedContent(
            targetState = sheetStatusText(state),
            transitionSpec = {
                (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 3 } + fadeOut())
            },
            label = "sheet_status_text",
        ) { text ->
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Outer AnimatedContent keys only on phase — progress updates won't retrigger animation
        AnimatedContent(
            targetState = state.downloadPhase,
            transitionSpec = {
                val spatial = spring<Float>(stiffness = 380f, dampingRatio = 0.8f)
                val effects = spring<Float>(stiffness = 1600f, dampingRatio = 1.0f)
                (fadeIn(effects) + scaleIn(spatial, initialScale = 0.88f)) togetherWith
                        (fadeOut(effects) + scaleOut(spatial, targetScale = 0.88f)) using
                        SizeTransform(clip = false) { _, _ -> spring(stiffness = 380f, dampingRatio = 0.8f) }
            },
            label = "download_phase",
        ) { phase ->
            when (phase) {
                DownloadPhase.IDLE -> Box(Modifier.fillMaxWidth())
                DownloadPhase.NO_WIFI_PENDING -> {
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.updates_download))
                    }
                }
                DownloadPhase.DOWNLOADING -> {
                    DownloadingCircle(
                        progress = state.downloadProgress,
                        cancelConfirm = state.cancelConfirmVisible,
                        onTap = onDownloadCircleTapped,
                        onStop = onStop,
                        onContinue = onContinue,
                    )
                }
                DownloadPhase.PROCESSING -> ProcessingCircle()
                DownloadPhase.CANCELING -> CancelingCircle()
                DownloadPhase.READY -> {
                    // Inner AnimatedContent handles pill → install card expansion
                    AnimatedContent(
                        targetState = state.showInstallCard,
                        transitionSpec = {
                            val spatial = spring<Float>(stiffness = 380f, dampingRatio = 0.8f)
                            val effects = spring<Float>(stiffness = 1600f, dampingRatio = 1.0f)
                            (fadeIn(effects) + scaleIn(spatial, initialScale = 0.92f)) togetherWith
                                    (fadeOut(effects) + scaleOut(spatial, targetScale = 0.92f)) using
                                    SizeTransform(clip = false) { _, _ -> spring(stiffness = 380f, dampingRatio = 0.8f) }
                        },
                        label = "install_card",
                    ) { showCard ->
                        if (showCard) {
                            InstallCard(
                                isFirstTime = state.isFirstUpdate,
                                canInstallDirectly = state.canInstallDirectly,
                                onConfirm = onConfirmInstall,
                            )
                        } else {
                            Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.updates_proceed_to_install))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InstallCard(
    isFirstTime: Boolean,
    canInstallDirectly: Boolean,
    onConfirm: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_system_update_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = stringResource(
                    if (isFirstTime) R.string.updates_install_first_time_title
                    else R.string.updates_install_title
                ),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    if (!canInstallDirectly) R.string.updates_install_grant_permission
                    else R.string.updates_install_ready
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.updates_install_now))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadingCircle(
    progress: Float,
    cancelConfirm: Boolean,
    onTap: () -> Unit,
    onStop: () -> Unit,
    onContinue: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var swipeTriggered by remember { mutableStateOf<SwipeAction?>(null) }
    val stopFraction by animateFloatAsState(
        targetValue = (-offsetX.value / 120f).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 1600f, dampingRatio = 1.0f),
        label = "stop_tint",
    )

    LaunchedEffect(swipeTriggered) {
        when (swipeTriggered) {
            SwipeAction.STOP -> { onStop(); swipeTriggered = null }
            SwipeAction.CONTINUE -> { onContinue(); swipeTriggered = null }
            null -> Unit
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(
            visible = cancelConfirm,
            enter = slideInHorizontally { -it } + expandHorizontally(expandFrom = Alignment.Start) + fadeIn(spring(stiffness = 1600f, dampingRatio = 1.0f)),
            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(spring(stiffness = 1600f, dampingRatio = 1.0f)),
        ) {
            FilledTonalButton(onClick = onStop) {
                Text(stringResource(R.string.updates_stop))
            }
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .pointerInput(cancelConfirm) {
                        if (!cancelConfirm) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        when {
                                            offsetX.value < -80f -> swipeTriggered = SwipeAction.STOP
                                            offsetX.value > 80f -> swipeTriggered = SwipeAction.CONTINUE
                                            else -> offsetX.animateTo(
                                                0f,
                                                spring(stiffness = 800f, dampingRatio = 0.6f),
                                            )
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        offsetX.animateTo(0f, spring(stiffness = 800f, dampingRatio = 0.6f))
                                    }
                                },
                            ) { _, dragAmount ->
                                scope.launch {
                                    val resistance = (1f - (kotlin.math.abs(offsetX.value) / 200f)).coerceAtLeast(0.3f)
                                    offsetX.snapTo((offsetX.value + dragAmount * resistance).coerceIn(-120f, 120f))
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val circleColor = lerp(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.errorContainer,
                    stopFraction,
                )
                Surface(
                    shape = CircleShape,
                    color = circleColor,
                    modifier = Modifier.size(56.dp),
                    onClick = onTap,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = cancelConfirm,
            enter = slideInHorizontally { it } + expandHorizontally(expandFrom = Alignment.End) + fadeIn(spring(stiffness = 1600f, dampingRatio = 1.0f)),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(spring(stiffness = 1600f, dampingRatio = 1.0f)),
        ) {
            OutlinedButton(onClick = onContinue) {
                Text(stringResource(R.string.updates_continue))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProcessingCircle() {
    var showAlmostDone by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(56.dp),
            onClick = { showAlmostDone = true },
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        AnimatedVisibility(visible = showAlmostDone) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2000)
                showAlmostDone = false
            }
            Text(
                text = stringResource(R.string.updates_almost_done),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CancelingCircle() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.size(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            LoadingIndicator(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

// ─── Channel picker dialog ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChannelPickerDialog(
    selectedChannel: UpdateChannel,
    onChannelSelected: (UpdateChannel) -> Unit,
    onDismiss: () -> Unit,
) {
    var infoChannel by remember { mutableStateOf<UpdateChannel?>(null) }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
        ) {
            AnimatedContent(
                targetState = infoChannel,
                transitionSpec = {
                    if (targetState != null) {
                        // List → InfoPage: info scales in, list fades out (no scale — avoids all-rows zoom)
                        (scaleIn(spring(stiffness = 800f, dampingRatio = 0.6f), initialScale = 0.88f) +
                                fadeIn(spring(stiffness = 1600f, dampingRatio = 1.0f))) togetherWith
                                fadeOut(spring(stiffness = 1600f, dampingRatio = 1.0f))
                    } else {
                        // InfoPage → List: list fades in, info scales out
                        fadeIn(spring(stiffness = 1600f, dampingRatio = 1.0f)) togetherWith
                                (scaleOut(spring(stiffness = 800f, dampingRatio = 0.6f), targetScale = 0.88f) +
                                        fadeOut(spring(stiffness = 1600f, dampingRatio = 1.0f)))
                    }
                },
                label = "channel_dialog_content",
            ) { channel ->
                if (channel == null) {
                    ChannelList(
                        selectedChannel = selectedChannel,
                        onChannelSelected = onChannelSelected,
                        onInfoClicked = { infoChannel = it },
                    )
                } else {
                    ChannelInfoPage(
                        channel = channel,
                        onBack = { infoChannel = null },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChannelList(
    selectedChannel: UpdateChannel,
    onChannelSelected: (UpdateChannel) -> Unit,
    onInfoClicked: (UpdateChannel) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        // Hero icon + centered title
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_filter_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.updates_channel_label),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        val channels = UpdateChannel.entries
        channels.forEachIndexed { index, channel ->
            val position = when {
                channels.size == 1 -> GroupPosition.SINGLE
                index == 0 -> GroupPosition.TOP
                index == channels.lastIndex -> GroupPosition.BOTTOM
                else -> GroupPosition.MIDDLE
            }
            ExpressiveGroupRow(
                position = position,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(channelDisplayName(channel))) },
                    leadingContent = {
                        RadioButton(
                            selected = channel == selectedChannel,
                            onClick = { onChannelSelected(channel) },
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { onInfoClicked(channel) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_info_outline_24dp),
                                contentDescription = stringResource(R.string.update_channel_info_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    modifier = Modifier.clickableRow { onChannelSelected(channel) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            if (index < channels.lastIndex) Spacer(Modifier.height(2.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ChannelInfoPage(
    channel: UpdateChannel,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_info_outline_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(channelDisplayName(channel)),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(channelDescription(channel)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(android.R.string.ok))
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

enum class GroupPosition { TOP, MIDDLE, BOTTOM, SINGLE }
private enum class SwipeAction { STOP, CONTINUE }

@Composable
fun ExpressiveGroupRow(
    position: GroupPosition,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = when (position) {
        GroupPosition.TOP -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        GroupPosition.MIDDLE -> RoundedCornerShape(8.dp)
        GroupPosition.BOTTOM -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
        GroupPosition.SINGLE -> RoundedCornerShape(28.dp)
    }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth(),
    ) {
        content()
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)

fun channelDisplayName(channel: UpdateChannel): Int = when (channel) {
    UpdateChannel.STABLE -> R.string.update_channel_stable
    UpdateChannel.RC -> R.string.update_channel_rc
    UpdateChannel.BETA -> R.string.update_channel_beta
    UpdateChannel.ALPHA -> R.string.update_channel_alpha
}

private fun channelDescription(channel: UpdateChannel): Int = when (channel) {
    UpdateChannel.STABLE -> R.string.update_channel_stable_description
    UpdateChannel.RC -> R.string.update_channel_rc_description
    UpdateChannel.BETA -> R.string.update_channel_beta_description
    UpdateChannel.ALPHA -> R.string.update_channel_alpha_description
}

// Status text shown in the main screen (idle/checking states only)
@Composable
private fun mainStatusText(state: UpdatesUiState): String? = when {
    state.checkStatus == CheckStatus.CHECKING -> stringResource(R.string.updates_status_checking)
    state.checkStatus == CheckStatus.UP_TO_DATE -> stringResource(R.string.updates_status_up_to_date)
    state.pendingVersion != null && state.downloadPhase == DownloadPhase.IDLE ->
        stringResource(R.string.updates_new_version_available, state.pendingVersion)
    else -> null
}

// Status text shown inside the update bottom sheet (download phase states)
@Composable
private fun sheetStatusText(state: UpdatesUiState): String? = when {
    state.downloadPhase == DownloadPhase.CANCELING -> stringResource(R.string.updates_canceling)
    state.cancelConfirmVisible -> stringResource(R.string.updates_stop_download_question)
    state.downloadPhase == DownloadPhase.NO_WIFI_PENDING ->
        stringResource(R.string.updates_status_no_wifi_detected)
    state.downloadPhase == DownloadPhase.DOWNLOADING ->
        stringResource(R.string.updates_status_downloading)
    state.downloadPhase == DownloadPhase.PROCESSING ->
        stringResource(R.string.updates_status_processing)
    state.downloadPhase == DownloadPhase.READY ->
        stringResource(R.string.updates_status_ready)
    else -> null
}

// ─── State model ─────────────────────────────────────────────────────────────

enum class CheckStatus { IDLE, CHECKING, UP_TO_DATE, UPDATE_AVAILABLE }
enum class DownloadPhase { IDLE, NO_WIFI_PENDING, DOWNLOADING, PROCESSING, READY, CANCELING }

data class UpdatesUiState(
    val currentVersion: String = "",
    val selectedChannel: UpdateChannel = UpdateChannel.STABLE,
    val autoCheck: Boolean = true,
    val checkStatus: CheckStatus = CheckStatus.IDLE,
    val downloadPhase: DownloadPhase = DownloadPhase.IDLE,
    val downloadProgress: Float = 0f,
    val cancelConfirmVisible: Boolean = false,
    val pendingVersion: String? = null,
    val showInstallCard: Boolean = false,
    val canInstallDirectly: Boolean = true,
    val isFirstUpdate: Boolean = false,
    val showUpdateSheet: Boolean = false,
)
