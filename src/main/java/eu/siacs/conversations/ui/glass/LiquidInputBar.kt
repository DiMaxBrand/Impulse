package eu.siacs.conversations.ui.glass

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.ui.AttachmentPreviewStrip
import eu.siacs.conversations.ui.ComposerBanner
import eu.siacs.conversations.ui.ComposerTextField
import eu.siacs.conversations.ui.ConversationScreenListener
import eu.siacs.conversations.ui.ConversationScreenState
import eu.siacs.conversations.ui.RecordingUiState
import eu.siacs.conversations.utils.UIHelper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The chat's message bar in the liquid glass experiment. This is its own bar, written for glass,
 * not the Material bar with glass drawn over it: the Material one sits in a Surface that clips its
 * children (which cut the glass's shadow into a hard rectangle and the stretching bubbles at its
 * edges) and carries its own shapes. Here nothing clips: every piece is a separate bubble in a row
 * over the message list, with nothing but empty space around it.
 *
 *   idle:       [ attach ] [ message field .................. ] [ mic / send ]
 *   recording:  [ discard ] [ 00:12 ]                     [ pause ] [ send ]
 *
 * Going from one to the other is one continuous motion: the paperclip becomes the discard button,
 * the field narrows to the timer pill on its left side, a pause bubble grows out of the send
 * bubble's side. The field's text input is the same one the Material bar uses.
 */

private val BubbleSize = 48.dp
private val TimerWidth = 112.dp

@Composable
fun LiquidInputBar(
    state: ConversationScreenState,
    listener: ConversationScreenListener,
    backdrop: GlassStack,
    onBarHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val conversation = state.conversation.value
    val revision = state.revision.intValue
    // Same rule as the Material bar: a read-only channel shows a note instead, unless a private
    // message target is set.
    val readOnly = remember(conversation, revision) {
        try {
            conversation != null &&
                conversation.getNextCounterpart() == null &&
                conversation.getMode() == Conversational.MODE_MULTI &&
                !conversation.mucOptions.participating()
        } catch (_: Exception) {
            false
        }
    }
    val attachMenu = rememberLiquidMenuState()

    // A box over the whole list area, so the menu can grow upward over the list; the bar is at its
    // bottom. It handles no touches of its own, the list underneath still scrolls.
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { onBarHeight(it.height) },
        ) {
            if (readOnly) {
                ReadOnlyBubble(backdrop)
            } else {
                BarPanels(state, listener, backdrop, conversation)
                ComposerRow(state, listener, backdrop, attachMenu, conversation)
            }
        }
        if (!readOnly) {
            // The paperclip grows into the menu: the panel lines up with the button's left edge
            // and opens upward.
            LiquidMenuOverlay(attachMenu, backdrop, alignEnd = false, opensUp = true) {
                LiquidMenuItem(R.drawable.ic_image_24dp, stringResource(R.string.attachment_choice_gallery)) {
                    attachMenu.open = false
                    listener.onAttachImage()
                }
                LiquidMenuItem(R.drawable.ic_camera_alt_24dp, stringResource(R.string.attachment_choice_camera)) {
                    attachMenu.open = false
                    listener.onTakePhoto()
                }
                LiquidMenuItem(R.drawable.ic_attach_file_24dp, stringResource(R.string.attachment_choice_file)) {
                    attachMenu.open = false
                    listener.onAttachFile()
                }
                LiquidMenuItem(R.drawable.ic_location_pin_24dp, stringResource(R.string.attachment_choice_location)) {
                    attachMenu.open = false
                    listener.onShareLocation()
                }
                LiquidMenuItem(R.drawable.ic_contacts_24dp, stringResource(R.string.attach_phone_contact)) {
                    attachMenu.open = false
                    listener.onSharePhoneContact()
                }
                LiquidMenuItem(R.drawable.ic_person_24dp, stringResource(R.string.attach_impulse_contact)) {
                    attachMenu.open = false
                    listener.onShareImpulseContact()
                }
                // Support only: a phone with the support account connected, in a one-to-one chat.
                val trackContext = androidx.compose.ui.platform.LocalContext.current
                val showTrack = remember(conversation) {
                    conversation != null &&
                        conversation.getMode() == Conversational.MODE_SINGLE &&
                        eu.siacs.conversations.utils.SupportAccounts.hasSupportAccount(trackContext)
                }
                if (showTrack) {
                    LiquidMenuItem(R.drawable.ic_report_24dp, stringResource(R.string.track_menu_label)) {
                        attachMenu.open = false
                        state.trackCardVisible.value = true
                    }
                    LiquidMenuItem(R.drawable.ic_system_update_24dp, stringResource(R.string.support_check_menu_label)) {
                        attachMenu.open = false
                        listener.onSupportCheckUpdates()
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyBubble(backdrop: GlassStack) {
    LiquidGlassPanel(
        glass = backdrop,
        shape = CircleShape,
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lock_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.read_only_channel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The reply/edit banner and the staged attachments, each in a glass panel over the bar. */
@Composable
private fun BarPanels(
    state: ConversationScreenState,
    listener: ConversationScreenListener,
    backdrop: GlassStack,
    conversation: eu.siacs.conversations.entities.Conversation?,
) {
    val revision = state.revision.intValue
    val privateTarget = remember(conversation, revision) {
        conversation?.getNextCounterpart() != null && conversation.getMode() == Conversational.MODE_MULTI
    }
    if (state.replyingTo.value != null || state.correcting.value != null || privateTarget) {
        LiquidGlassPanel(
            glass = backdrop,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            ComposerBanner(state, listener)
        }
    }
    if (state.attachments.isNotEmpty()) {
        LiquidGlassPanel(
            glass = backdrop,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            AttachmentPreviewStrip(state)
        }
    }
}

@Composable
private fun ComposerRow(
    state: ConversationScreenState,
    listener: ConversationScreenListener,
    backdrop: GlassStack,
    attachMenu: LiquidMenuState,
    conversation: eu.siacs.conversations.entities.Conversation?,
) {
    val context = LocalContext.current

    val recording = state.recordingState.value
    val isRecording = recording is RecordingUiState.Active
    // The last live recording state, so the timer and the pause icon stay put while the recording
    // part of the bar is still sliding away after it ended.
    var lastRecording by remember { mutableStateOf<RecordingUiState.Active?>(null) }
    if (recording is RecordingUiState.Active) lastRecording = recording

    val text = state.inputText.value
    val hasText = text.isNotBlank()
    val correcting = state.correcting.value != null
    val hasAttachments = state.attachments.isNotEmpty()
    val showMic = !hasText && !correcting && !hasAttachments

    // 0 = typing, 1 = recording; the one value the whole bar's transition hangs on.
    val rec = remember { Animatable(if (isRecording) 1f else 0f) }
    LaunchedEffect(isRecording) {
        rec.animateTo(if (isRecording) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 380f))
    }

    // How full the last line of text is; as it nears the right edge the field reaches toward the
    // send bubble and presses on it, and lets go on the spring when the line wraps.
    var fill by remember { mutableFloatStateOf(0f) }
    val push by animateFloatAsState(
        targetValue = ((fill - 0.82f) / 0.18f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "fieldPush",
    )
    val onLayoutInfo = remember<(Int, Float) -> Unit> {
        { _, f -> if (abs(f - fill) > 0.01f) fill = f }
    }

    val hintText =
        if (correcting) stringResource(R.string.send_corrected_message)
        else conversation?.let { UIHelper.getMessageHint(context, it) }
            ?: stringResource(R.string.send_message)

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        AttachOrDiscardBubble(
            backdrop = backdrop,
            attachMenu = attachMenu,
            rec = rec,
            onClick = { if (isRecording) listener.onCancelRecording() else attachMenu.open = true },
        )
        FieldBubble(
            state = state,
            listener = listener,
            backdrop = backdrop,
            text = text,
            hintText = hintText,
            typingEnabled = !isRecording,
            rec = rec,
            recording = lastRecording,
            push = { push },
            onLayoutInfo = onLayoutInfo,
        )
        PauseBubble(
            backdrop = backdrop,
            rec = rec,
            paused = lastRecording?.paused == true,
            onClick = { listener.onPauseRecording() },
        )
        SendBubble(
            backdrop = backdrop,
            iconRes = when {
                isRecording -> R.drawable.ic_send_24dp
                showMic -> R.drawable.ic_mic_24dp
                correcting -> R.drawable.ic_done_24dp
                else -> R.drawable.ic_send_24dp
            },
            isSend = isRecording || !showMic,
            enabled = isRecording || showMic || hasText || hasAttachments,
            push = { push },
            onClick = {
                when {
                    isRecording -> listener.onSendRecording()
                    hasAttachments -> listener.onCommitAttachments()
                    showMic -> listener.onStartRecording()
                    hasText -> listener.onSendTextMessage(text.trim())
                }
            },
        )
    }
}

/** The paperclip, which turns into the discard button while a voice message is recorded. */
@Composable
private fun AttachOrDiscardBubble(
    backdrop: GlassStack,
    attachMenu: LiquidMenuState,
    rec: Animatable<Float, AnimationVector1D>,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val anchorHidden by rememberAnchorHidden(attachMenu)
    LiquidGlassButton(
        onClick = onClick,
        glass = backdrop,
        recording = !anchorHidden,
        modifier = Modifier.size(BubbleSize)
            .liquidMenuAnchor(attachMenu) {
                Icon(
                    painter = painterResource(R.drawable.ic_attach_file_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            // Pulling the paperclip is for the menu only; while recording it is the discard button.
            .then(if (rec.value < 0.01f) Modifier.liquidMenuPull(attachMenu) else Modifier)
            .graphicsLayer { alpha = attachMenu.anchorAlpha },
        // The body goes red as it becomes the discard button.
        tint = colors.error.copy(alpha = 0.5f * rec.value),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_attach_file_24dp),
            contentDescription = stringResource(R.string.attach_file),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer {
                alpha = 1f - rec.value
                rotationZ = -45f * rec.value
            },
        )
        Icon(
            painter = painterResource(R.drawable.ic_delete_24dp),
            contentDescription = stringResource(R.string.cancel),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer {
                alpha = rec.value
                rotationZ = 45f * (1f - rec.value)
            },
        )
    }
}

/**
 * The message field, and while recording its narrowed form: the timer. One glass piece whose width
 * runs from "all the room there is" to the width of the timer, anchored on the left, and whose
 * height follows the text on a springy curve (a new line makes it bulge and settle like a drop
 * that just got bigger). Near the end of a line it reaches toward the send bubble ([push]).
 */
@Composable
private fun RowScope.FieldBubble(
    state: ConversationScreenState,
    listener: ConversationScreenListener,
    backdrop: GlassStack,
    text: String,
    hintText: String,
    typingEnabled: Boolean,
    rec: Animatable<Float, AnimationVector1D>,
    recording: RecordingUiState.Active?,
    push: () -> Float,
    onLayoutInfo: (Int, Float) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.weight(1f).padding(horizontal = 8.dp)) {
        val fullWidth = maxWidth

        // How tall the text wants to be, and the height actually shown, which chases it.
        var naturalHeightPx by remember { mutableIntStateOf(0) }
        val shownHeight = remember { Animatable(0f) }
        val minHeightPx = with(density) { BubbleSize.toPx() }
        val target = max(naturalHeightPx.toFloat(), minHeightPx)
        LaunchedEffect(target) {
            if (shownHeight.value == 0f) shownHeight.snapTo(target)
            else shownHeight.animateTo(target, spring(dampingRatio = 0.6f, stiffness = 420f))
        }
        val timerWidthPx = with(density) { TimerWidth.toPx() }

        LiquidGlassPanel(
            glass = backdrop,
            modifier = Modifier.layout { measurable, constraints ->
                val full = constraints.maxWidth
                val w = lerp(full.toFloat(), min(timerWidthPx, full.toFloat()), rec.value).roundToInt()
                val h = max(shownHeight.value, minHeightPx).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(constraints.maxWidth, h) { placeable.place(0, 0) }
            },
            clipContent = true,
            // Only the glass reaches toward the send bubble (wider by up to 10dp, pinned on the
            // left); the letters are not touched, they do not stretch with it.
            layerBlock = {
                val grow = push() * 10.dp.toPx()
                scaleX = 1f + grow / size.width.coerceAtLeast(1f)
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
        ) {
            // The text input is laid out at the full width the whole time, so nothing in it
            // reflows while the piece narrows to the timer, and at its own full height: measured
            // inside the container's current (fixed) height it could never be taller than the
            // container, so the container never learned it needed to grow, and the text scrolled
            // instead of the field expanding.
            Box(
                Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .width(fullWidth)
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .graphicsLayer { alpha = 1f - rec.value }
                    .onSizeChanged { naturalHeightPx = it.height },
            ) {
                ComposerTextField(
                    state = state,
                    listener = listener,
                    text = text,
                    hintText = hintText,
                    enabled = typingEnabled,
                    onLayoutInfo = onLayoutInfo,
                )
            }
            if (rec.value > 0.01f) {
                TimerContent(recording, rec)
            }
        }
    }
}

/** A red dot, blinking while it records, and the time. */
@Composable
private fun TimerContent(recording: RecordingUiState.Active?, rec: Animatable<Float, AnimationVector1D>) {
    if (recording == null) return
    val minutes = (recording.elapsedMs / 60000).toInt()
    val seconds = ((recording.elapsedMs % 60000) / 1000).toInt()
    val dotAlpha =
        if (!recording.paused) {
            val transition = rememberInfiniteTransition(label = "recDot")
            val alpha by transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
                label = "recDotAlpha",
            )
            alpha
        } else {
            0.2f
        }
    Row(
        Modifier.width(TimerWidth).height(BubbleSize).graphicsLayer { alpha = rec.value },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = dotAlpha))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = String.format("%02d:%02d", minutes, seconds),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Pause / resume. It has no room while typing and grows out of the send bubble's side when recording starts. */
@Composable
private fun PauseBubble(
    backdrop: GlassStack,
    rec: Animatable<Float, AnimationVector1D>,
    paused: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .layout { measurable, constraints ->
                val size = BubbleSize.roundToPx()
                val gap = 8.dp.roundToPx()
                val w = ((size + gap) * rec.value).roundToInt().coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(size, size))
                // Right-aligned, so it slides out from beside the send bubble.
                layout(w, size) { placeable.place(w - size, 0) }
            }
            .graphicsLayer {
                alpha = rec.value.coerceIn(0f, 1f)
                val s = 0.6f + 0.4f * rec.value.coerceIn(0f, 1f)
                scaleX = s
                scaleY = s
            },
    ) {
        LiquidGlassButton(onClick = onClick, glass = backdrop, modifier = Modifier.size(BubbleSize)) {
            Icon(
                painter = painterResource(if (paused) R.drawable.ic_play_arrow_24dp else R.drawable.ic_pause_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * The mic, which becomes the send button once there is something to send. It stays a glass circle
 * throughout; only its colour and its icon change. When the field presses on it ([push]) it gives
 * way a little.
 */
@Composable
private fun SendBubble(
    backdrop: GlassStack,
    iconRes: Int,
    isSend: Boolean,
    enabled: Boolean,
    push: () -> Float,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val sendProgress by animateFloatAsState(
        targetValue = if (isSend) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "sendTint",
    )
    LiquidGlassButton(
        onClick = { if (enabled) onClick() },
        glass = backdrop,
        modifier = Modifier.size(BubbleSize).graphicsLayer { alpha = if (enabled) 1f else 0.5f },
        tint = colors.primary.copy(alpha = 0.78f * sendProgress.coerceIn(0f, 1f)),
        extraLayerBlock = {
            val p = push()
            translationX += p * 6.dp.toPx()
            val s = 1f - 0.08f * p
            scaleX *= s
            scaleY *= s
        },
    ) {
        Crossfade(targetState = iconRes, animationSpec = tween(160), label = "sendIcon") { icon ->
            val isMic = icon == R.drawable.ic_mic_24dp
            Icon(
                painter = painterResource(icon),
                contentDescription = stringResource(
                    if (isMic) R.string.attachment_choice_recording else R.string.send_message
                ),
                tint = if (isMic) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
