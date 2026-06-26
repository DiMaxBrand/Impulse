package eu.siacs.conversations.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
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
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.update.UpdateChannel
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
)
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
    var infoChannel by remember { mutableStateOf<UpdateChannel?>(null) }

    // Reset info page whenever the picker closes
    LaunchedEffect(channelPickerVisible) {
        if (!channelPickerVisible) infoChannel = null
    }

    // Scrim animates independently of the shared-element transition
    val scrimAlpha by animateFloatAsState(
        targetValue = if (channelPickerVisible) 0.32f else 0f,
        animationSpec = spring(stiffness = 1600f, dampingRatio = 1.0f),
        label = "scrim_alpha",
    )

    // Outer SharedTransitionLayout: settings channel row ↔ picker dialog
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── Settings column ───────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
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

                // Channel row — source of the settings→picker container transform
                AnimatedVisibility(
                    visible = !channelPickerVisible,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .sharedBounds(
                                rememberSharedContentState("channel_picker"),
                                animatedVisibilityScope = this@AnimatedVisibility,
                                enter = fadeIn(spring(stiffness = 1600f, dampingRatio = 1.0f)),
                                exit = fadeOut(spring(stiffness = 1600f, dampingRatio = 1.0f)),
                                boundsTransform = BoundsTransform { _, _ ->
                                    spring(stiffness = 380f, dampingRatio = 0.8f)
                                },
                                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                            )
                            .clickable { channelPickerVisible = true },
                    ) {
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
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

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

                FilledTonalButton(
                    onClick = onShowUpdateSheet,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Show update bottom sheet")
                }
            }

            // ── Scrim (animated independently) ───────────────────────────
            if (scrimAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimAlpha)),
                )
            }

            // ── Channel picker overlay — destination of container transform ─
            AnimatedVisibility(
                visible = channelPickerVisible,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { channelPickerVisible = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .fillMaxWidth()
                            .sharedBounds(
                                rememberSharedContentState("channel_picker"),
                                animatedVisibilityScope = this@AnimatedVisibility,
                                enter = fadeIn(spring(stiffness = 1600f, dampingRatio = 1.0f)),
                                exit = fadeOut(spring(stiffness = 1600f, dampingRatio = 1.0f)),
                                boundsTransform = BoundsTransform { _, _ ->
                                    spring(stiffness = 380f, dampingRatio = 0.8f)
                                },
                                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                            )
                            // Consume touches so tapping inside the picker doesn't dismiss it
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {},
                    ) {
                        // Inner SharedTransitionLayout: channel list row ↔ info page
                        SharedTransitionLayout {
                            AnimatedContent(
                                targetState = infoChannel,
                                transitionSpec = {
                                    EnterTransition.None togetherWith ExitTransition.None using
                                            SizeTransform(clip = false) { _, _ ->
                                                spring(stiffness = 380f, dampingRatio = 0.8f)
                                            }
                                },
                                label = "channel_picker_content",
                            ) { channel ->
                                if (channel == null) {
                                    ChannelList(
                                        selectedChannel = state.selectedChannel,
                                        onChannelSelected = { ch ->
                                            onChannelSelected(ch)
                                            channelPickerVisible = false
                                        },
                                        onInfoClicked = { infoChannel = it },
                                        animatedContentScope = this,
                                    )
                                } else {
                                    ChannelInfoPage(
                                        channel = channel,
                                        onBack = { infoChannel = null },
                                        animatedContentScope = this,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Update flow bottom sheet ──────────────────────────────────────────
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
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

// ─── Channel picker: list ────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SharedTransitionScope.ChannelList(
    selectedChannel: UpdateChannel,
    onChannelSelected: (UpdateChannel) -> Unit,
    onInfoClicked: (UpdateChannel) -> Unit,
    animatedContentScope: AnimatedContentScope,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
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
            val rowShape = when (position) {
                GroupPosition.TOP -> RoundedCornerShape(28.dp, 28.dp, 8.dp, 8.dp)
                GroupPosition.MIDDLE -> RoundedCornerShape(8.dp)
                GroupPosition.BOTTOM -> RoundedCornerShape(8.dp, 8.dp, 28.dp, 28.dp)
                GroupPosition.SINGLE -> RoundedCornerShape(28.dp)
            }
            // Each row's Surface is the source of its own row→info container transform
            Surface(
                shape = rowShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .sharedBounds(
                        rememberSharedContentState("channel_info_$channel"),
                        animatedVisibilityScope = animatedContentScope,
                        enter = fadeIn(spring(stiffness = 1600f, dampingRatio = 1.0f)),
                        exit = fadeOut(spring(stiffness = 1600f, dampingRatio = 1.0f)),
                        boundsTransform = BoundsTransform { _, _ ->
                            spring(stiffness = 380f, dampingRatio = 0.8f)
                        },
                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    ),
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

// ─── Channel picker: info page ────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SharedTransitionScope.ChannelInfoPage(
    channel: UpdateChannel,
    onBack: () -> Unit,
    animatedContentScope: AnimatedContentScope,
) {
    // Surface matches the source row surface (same color/shape) so the container transform
    // morphs the row surface into the full-width info card
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .sharedBounds(
                rememberSharedContentState("channel_info_$channel"),
                animatedVisibilityScope = animatedContentScope,
                enter = fadeIn(spring(stiffness = 1600f, dampingRatio = 1.0f)),
                exit = fadeOut(spring(stiffness = 1600f, dampingRatio = 1.0f)),
                boundsTransform = BoundsTransform { _, _ ->
                    spring(stiffness = 380f, dampingRatio = 0.8f)
                },
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            ),
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
            FilledTonalButton(
                onClick = onBack,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        }
    }
}

// ─── Status + Download flow ───────────────────────────────────────────────────

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
                    Button(
                        onClick = onDownload,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.updates_download),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                DownloadPhase.DOWNLOADING -> {
                    DownloadingPill(
                        progress = state.downloadProgress,
                        cancelConfirm = state.cancelConfirmVisible,
                        onTap = onDownloadCircleTapped,
                        onStop = onStop,
                        onContinue = onContinue,
                    )
                }

                DownloadPhase.PROCESSING -> ProcessingPill()
                DownloadPhase.CANCELING -> CancelingPill()

                DownloadPhase.READY -> {
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
                            Button(
                                onClick = onInstall,
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp),
                            ) {
                                Text(
                                    stringResource(R.string.updates_proceed_to_install),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Download/processing/canceling pills ─────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DownloadingPill(
    progress: Float,
    cancelConfirm: Boolean,
    onTap: () -> Unit,
    onStop: () -> Unit,
    onContinue: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val swipeDelta = remember { Animatable(0f) }
    var swipeTriggered by remember { mutableStateOf<SwipeAction?>(null) }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = 80f, dampingRatio = 1.0f),
        label = "download_progress",
    )
    val stopFraction by animateFloatAsState(
        targetValue = (-swipeDelta.value / 80f).coerceIn(0f, 1f),
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

    val pillColor = lerp(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.errorContainer,
        stopFraction,
    )
    val contentColor = lerp(
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.onErrorContainer,
        stopFraction,
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = pillColor,
            onClick = onTap,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .pointerInput(cancelConfirm) {
                    if (!cancelConfirm) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    when {
                                        swipeDelta.value < -50f -> swipeTriggered = SwipeAction.STOP
                                        swipeDelta.value > 50f -> swipeTriggered = SwipeAction.CONTINUE
                                        else -> swipeDelta.animateTo(0f, spring(stiffness = 800f, dampingRatio = 0.6f))
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch { swipeDelta.animateTo(0f, spring(stiffness = 800f, dampingRatio = 0.6f)) }
                            },
                        ) { _, dragAmount ->
                            scope.launch {
                                val r = (1f - (kotlin.math.abs(swipeDelta.value) / 120f)).coerceAtLeast(0.3f)
                                swipeDelta.snapTo((swipeDelta.value + dragAmount * r).coerceIn(-80f, 80f))
                            }
                        }
                    }
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(40.dp),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.22f),
                )
            }
        }

        AnimatedVisibility(
            visible = cancelConfirm,
            enter = expandVertically(spring(stiffness = 380f, dampingRatio = 0.8f)) +
                    fadeIn(spring(stiffness = 1600f, dampingRatio = 1.0f)),
            exit = shrinkVertically(spring(stiffness = 380f, dampingRatio = 0.8f)) +
                    fadeOut(spring(stiffness = 1600f, dampingRatio = 1.0f)),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTonalButton(
                    onClick = onStop,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.updates_stop)) }
                OutlinedButton(
                    onClick = onContinue,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.updates_continue)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ProcessingPill() {
    var showAlmostDone by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            onClick = { showAlmostDone = true },
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.22f),
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
private fun CancelingPill() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().height(64.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            LoadingIndicator(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

// ─── Install card ─────────────────────────────────────────────────────────────

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
            Button(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.updates_install_now))
            }
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
        GroupPosition.TOP -> RoundedCornerShape(28.dp, 28.dp, 8.dp, 8.dp)
        GroupPosition.MIDDLE -> RoundedCornerShape(8.dp)
        GroupPosition.BOTTOM -> RoundedCornerShape(8.dp, 8.dp, 28.dp, 28.dp)
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

@Composable
private fun mainStatusText(state: UpdatesUiState): String? = when {
    state.checkStatus == CheckStatus.CHECKING -> stringResource(R.string.updates_status_checking)
    state.checkStatus == CheckStatus.UP_TO_DATE -> stringResource(R.string.updates_status_up_to_date)
    state.pendingVersion != null && state.downloadPhase == DownloadPhase.IDLE ->
        stringResource(R.string.updates_new_version_available, state.pendingVersion)
    else -> null
}

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
