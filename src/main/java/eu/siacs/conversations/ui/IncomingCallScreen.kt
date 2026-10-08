package eu.siacs.conversations.ui

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import eu.siacs.conversations.R
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * State the (View-based) RtpSessionActivity pushes into the Compose call layer. Java calls the
 * generated setters; the composables read the snapshot state directly.
 *
 * One layer covers an audio call from ring to hang-up: while [sliderVisible] it is the incoming
 * call (avatar + answer/decline slider); once it is false it is the live call (smaller avatar,
 * controls, hang-up button) -- the same handle that was dragged becomes the hang-up button.
 */
class IncomingCallState {
    private var visibleState by mutableStateOf(false)
    private var sliderState by mutableStateOf(true)
    private var avatarState by mutableStateOf<ImageBitmap?>(null)
    private var hintState by mutableStateOf(false)
    private var micOnState by mutableStateOf(true)
    private var audioIconState by mutableIntStateOf(R.drawable.ic_volume_up_24dp)
    private var audioChoicesState by mutableIntStateOf(0)
    private var durationState by mutableStateOf("")
    private var statusState by mutableStateOf("")
    private var establishedState by mutableStateOf(false)
    private var reconnectingState by mutableStateOf(false)
    private var endModeState by mutableStateOf(false)
    private var secondaryIconState by mutableIntStateOf(R.drawable.ic_replay_24dp)
    private var secondaryDescState by mutableStateOf("")

    private var videoCallState by mutableStateOf(false)
    private var remoteVideoViewState by mutableStateOf<android.view.View?>(null)
    private var localVideoViewState by mutableStateOf<android.view.View?>(null)
    private var hasRemoteVideoState by mutableStateOf(false)
    private var hasLocalVideoState by mutableStateOf(false)
    private var cameraSwitchableState by mutableStateOf(false)
    private var systemPipState by mutableStateOf(false)

    private var switchRequestState by mutableStateOf(false)
    private var switchHintState by mutableStateOf(false)

    /** The other person asks to add video to this audio call: the slider comes back. */
    fun setSwitchRequest(request: Boolean, showHint: Boolean) {
        switchRequestState = request
        switchHintState = showHint
    }

    var onSwitchAccept: Runnable? = null
    var onSwitchDecline: Runnable? = null
    var onSwitchUsed: Runnable? = null
    var onExit: Runnable? = null
    var onSecondary: Runnable? = null
    var onToggleCamera: Runnable? = null
    var onFlipCamera: Runnable? = null

    /** Video call layout (full-screen video, corner self-view) instead of the audio one. */
    fun setVideoCall(video: Boolean) {
        videoCallState = video
    }

    /** The two WebRTC renderers; the Compose layer hosts them (the activity detaches them). */
    fun setVideoViews(remote: android.view.View?, local: android.view.View?) {
        remoteVideoViewState = remote
        localVideoViewState = local
    }

    fun setVideoPresence(remote: Boolean, local: Boolean) {
        hasRemoteVideoState = remote
        hasLocalVideoState = local
    }

    fun setCameraSwitchable(switchable: Boolean) {
        cameraSwitchableState = switchable
    }

    /** The system's picture-in-picture window: show nothing but the main video. */
    fun setSystemPip(pip: Boolean) {
        systemPipState = pip
    }

    /**
     * The call has ended without a connection (declined, busy, lost, failed ...). The hang-up
     * button slides left as "exit" and a second button -- retry or leave a voicemail -- slides
     * out from under it on the right.
     */
    fun setEndMode(endMode: Boolean, @DrawableRes secondaryIcon: Int, secondaryDescription: String) {
        endModeState = endMode
        secondaryIconState = secondaryIcon
        secondaryDescState = secondaryDescription
    }

    var onAccept: Runnable? = null
    var onDecline: Runnable? = null
    var onHangUp: Runnable? = null
    var onToggleMic: Runnable? = null
    var onAudioOutput: Runnable? = null
    /** Fired once the slider has actually been dragged, so the first-time hint can retire. */
    var onSliderUsed: Runnable? = null

    fun setHintVisible(visible: Boolean) {
        hintState = visible
    }

    fun setVisible(visible: Boolean) {
        visibleState = visible
    }

    fun setSliderVisible(visible: Boolean) {
        sliderState = visible
    }

    fun setAvatar(bitmap: Bitmap?) {
        avatarState = bitmap?.asImageBitmap()
    }

    fun setMicOn(on: Boolean) {
        micOnState = on
    }

    fun setAudioIcon(@DrawableRes icon: Int) {
        audioIconState = icon
    }

    fun setAudioChoices(count: Int) {
        audioChoicesState = count
    }

    /** The call status line ("Ringing", "Connecting", ...) -- the same text the toolbar shows. */
    fun setStatusText(text: String) {
        statusState = text
    }

    /** True while an established call has lost its link and is trying to get it back. */
    fun setReconnecting(reconnecting: Boolean) {
        reconnectingState = reconnecting
    }

    /** True once the call is actually up (connected / reconnecting). */
    fun setEstablished(established: Boolean) {
        establishedState = established
    }

    fun setDurationText(text: String) {
        durationState = text
    }

    internal val visible get() = visibleState
    internal val sliderVisible get() = sliderState
    internal val avatar get() = avatarState
    internal val hint get() = hintState
    internal val micOn get() = micOnState
    internal val audioIcon get() = audioIconState
    internal val audioChoices get() = audioChoicesState
    internal val duration get() = durationState
    internal val status get() = statusState
    internal val established get() = establishedState
    internal val reconnecting get() = reconnectingState
    internal val endMode get() = endModeState
    internal val switchRequest get() = switchRequestState
    internal val switchHint get() = switchHintState
    internal val videoCall get() = videoCallState
    internal val remoteVideoView get() = remoteVideoViewState
    internal val localVideoView get() = localVideoViewState
    internal val hasRemoteVideo get() = hasRemoteVideoState
    internal val hasLocalVideo get() = hasLocalVideoState
    internal val cameraSwitchable get() = cameraSwitchableState
    internal val systemPip get() = systemPipState
    internal val secondaryIcon get() = secondaryIconState
    internal val secondaryDescription get() = secondaryDescState
}

object IncomingCallHelper {
    @JvmStatic
    fun setup(composeView: ComposeView, state: IncomingCallState) {
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent { ImpulseExpressiveTheme { IncomingCallContent(state) } }
    }
}

// Shapes that read as friendly blobs without any spike or notch deep enough to cut into a face.
private val CALL_AVATAR_SHAPES: List<RoundedPolygon> by lazy {
    listOf(
        MaterialShapeHelpers.circle(),
        MaterialShapeHelpers.softBurst(),
        MaterialShapeHelpers.cookie9Sided(),
        MaterialShapeHelpers.flower(),
        MaterialShapeHelpers.pentagon(),
        MaterialShapeHelpers.puffy(),
        MaterialShapeHelpers.clover8Leaf(),
    )
}

// Same shape the chat list gives a contact who is in an ongoing call.
private val CALL_ESTABLISHED_SHAPE: RoundedPolygon get() = CALL_AVATAR_SHAPES[1]

// Easter egg: while the call is reconnecting the avatar runs through these, then starts over.
private val CALL_RECONNECTING_SHAPES: List<RoundedPolygon> by lazy {
    listOf(
        MaterialShapeHelpers.arrow(),
        MaterialShapeHelpers.slanted(),
        MaterialShapeHelpers.semiCircle(),
        MaterialShapeHelpers.gem(),
        MaterialShapeHelpers.pixelCircle(),
        MaterialShapeHelpers.pixelTriangle(),
        MaterialShapeHelpers.heart(),
    )
}

private val SWITCH_BLUE = Color(0xFF2F6FED)
private val SWITCH_GREEN = Color(0xFF2E9E57)

private const val SLIDER_COMMIT_FRACTION = 0.55f
private const val SLIDER_FLING_DP_PER_S = 900f

private val HANDLE_WIDTH = 112.dp
private val HANDLE_HEIGHT = 72.dp
private val HANGUP_SIZE = 88.dp
private val TRACK_HEIGHT = 96.dp

@Composable
internal fun IncomingCallContent(state: IncomingCallState) {
    if (!state.visible) return
    val active = !state.sliderVisible
    // Video calls (once answered / outgoing) use their own full-screen layout.
    if (state.videoCall && active) {
        VideoCallContent(state)
        return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)) {
        val baseAvatar = min(min(maxWidth.value, 320f), maxHeight.value * 0.45f).dp
        // The avatar gives up room to the controls once the call is live.
        val avatarSize by
            animateDpAsState(
                targetValue = if (active) baseAvatar * 0.7f else baseAvatar,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                label = "callAvatarSize",
            )
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            MorphingCallAvatar(state.avatar, state.established, state.reconnecting, Modifier.size(avatarSize))
            // Status line, cross-fading as the call moves through its states.
            androidx.compose.animation.AnimatedContent(
                targetState = state.status,
                transitionSpec = {
                    (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                        fadeOut(spring(stiffness = Spring.StiffnessMedium)))
                        .using(androidx.compose.animation.SizeTransform(clip = false))
                },
                label = "callStatus",
            ) { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            AnimatedVisibility(
                visible = state.duration.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + androidx.compose.animation.shrinkVertically(),
            ) {
                Text(
                    text = state.duration,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(
                visible = active && !state.endMode,
                enter =
                    fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                        slideInVertically(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)) {
                            it / 2
                        } +
                        scaleIn(initialScale = 0.85f),
                exit = fadeOut(),
            ) {
                Row(
                    modifier = Modifier.padding(bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    CallToggleButton(
                        checked = !state.micOn,
                        icon = if (state.micOn) R.drawable.ic_mic_24dp else R.drawable.ic_mic_off_24dp,
                        description = stringResource(R.string.call_button_mute),
                        onClick = { state.onToggleMic?.run() },
                    )
                    CallToggleButton(
                        checked = false,
                        icon = state.audioIcon,
                        description = stringResource(R.string.audio_output_choose),
                        enabled = state.audioChoices >= 2,
                        onClick = { state.onAudioOutput?.run() },
                    )
                }
            }
            val showHint = if (state.switchRequest) state.switchHint else (!active && state.hint)
            if (showHint) {
                Text(
                    text =
                        stringResource(
                            if (state.switchRequest) R.string.call_switch_hint
                            else R.string.call_slider_hint
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            CallDock(
                active = active,
                endMode = state.endMode,
                secondaryIcon = state.secondaryIcon,
                secondaryDescription = state.secondaryDescription,
                switchRequest = state.switchRequest,
                onAccept = {
                    if (state.switchRequest) state.onSwitchAccept?.run() else state.onAccept?.run()
                },
                onDecline = {
                    if (state.switchRequest) state.onSwitchDecline?.run() else state.onDecline?.run()
                },
                onHangUp = { state.onHangUp?.run() },
                onExit = { state.onExit?.run() },
                onSecondary = { state.onSecondary?.run() },
                onUsed = {
                    if (state.switchRequest) state.onSwitchUsed?.run() else state.onSliderUsed?.run()
                },
            )
            Spacer(Modifier.height(48.dp))
        }
    }
}

/**
 * Round toggle on the live-call screen. Off, it is a circle; switched on it eases into a
 * rounded square with the primary colour -- the Expressive "shape says state" treatment.
 */
@Composable
internal fun CallToggleButton(
    checked: Boolean,
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 72.dp,
) {
    val colors = MaterialTheme.colorScheme
    val corner by
        animateDpAsState(
            if (checked) size * 0.3f else size / 2,
            spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
            label = "toggleCorner",
        )
    val container by
        animateColorAsState(
            if (checked) colors.primary else colors.surfaceContainerHighest,
            label = "toggleContainer",
        )
    val content by
        animateColorAsState(
            if (checked) colors.onPrimary else colors.onSurface,
            label = "toggleContent",
        )
    Box(
        modifier =
            Modifier.size(size)
                .clip(RoundedCornerShape(corner.coerceAtLeast(0.dp)))
                .background(container)
                .semantics { contentDescription = description }
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(size * 0.42f),
        )
    }
}

/**
 * The caller's picture, clipped to a shape that keeps morphing between [CALL_AVATAR_SHAPES] and
 * slowly turns. Only the clip outline rotates -- the photo itself stays upright.
 */
@Composable
internal fun MorphingCallAvatar(
    avatar: ImageBitmap?,
    established: Boolean,
    reconnecting: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 0.76f,
) {
    val shapes = CALL_AVATAR_SHAPES
    val fromShape = remember { mutableStateOf(shapes[0]) }
    val toShape = remember { mutableStateOf(shapes[0]) }
    val progress = remember { Animatable(1f) }
    val establishedNow by rememberUpdatedState(established)
    val reconnectingNow by rememberUpdatedState(reconnecting)

    LaunchedEffect(Unit) {
        var index = 0
        while (true) {
            if (reconnectingNow) {
                // Starts straight away (no wait for the first shape), then a step every 1.4 s.
                var step = 0
                while (reconnectingNow) {
                    fromShape.value = toShape.value
                    toShape.value = CALL_RECONNECTING_SHAPES[step % CALL_RECONNECTING_SHAPES.size]
                    step++
                    progress.snapTo(0f)
                    progress.animateTo(
                        1f,
                        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
                    )
                    kotlinx.coroutines.withTimeoutOrNull(1400) {
                        snapshotFlow { reconnectingNow }.first { !it }
                    }
                }
                continue
            }
            if (establishedNow) {
                // Once the call is up it settles on the chat list's "ongoing call" shape and
                // stops changing (the slow turn goes on). A morph already under way finishes
                // first, since this only looks at the flag between steps.
                if (toShape.value !== CALL_ESTABLISHED_SHAPE) {
                    fromShape.value = toShape.value
                    toShape.value = CALL_ESTABLISHED_SHAPE
                    progress.snapTo(0f)
                    progress.animateTo(
                        1f,
                        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
                    )
                }
                snapshotFlow { establishedNow }.first { !it }
                continue
            }
            val becameEstablished =
                kotlinx.coroutines.withTimeoutOrNull(1700) {
                    snapshotFlow { establishedNow }.first { it }
                } != null
            if (becameEstablished) continue
            index = (index + 1) % shapes.size
            fromShape.value = toShape.value
            toShape.value = shapes[index]
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "callAvatarSpin")
    val rotation by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart),
            label = "callAvatarSpinValue",
        )

    val morph = remember(fromShape.value, toShape.value) { Morph(fromShape.value, toShape.value) }
    val path = remember { androidx.compose.ui.graphics.Path() }
    val matrix = remember { android.graphics.Matrix() }
    val fallback = MaterialTheme.colorScheme.primaryContainer

    Canvas(modifier = modifier) {
        // Margin so the bounciest mid-morph bulge and the rotation never touch the canvas edge.
        val margin = (1f - scale) / 2f
        matrix.reset()
        matrix.postScale(size.width * scale, size.height * scale)
        matrix.postTranslate(size.width * margin, size.height * margin)
        matrix.postRotate(rotation, size.width / 2f, size.height / 2f)
        morph.toPath(progress.value, path)
        path.asAndroidPath().transform(matrix)

        clipPath(path) {
            if (avatar == null) {
                drawRect(fallback)
            } else {
                val side = min(avatar.width, avatar.height)
                drawImage(
                    image = avatar,
                    srcOffset = IntOffset((avatar.width - side) / 2, (avatar.height - side) / 2),
                    srcSize = IntSize(side, side),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                )
            }
        }
    }
}

/**
 * The answer/decline slider and, once the call is live, the hang-up button -- one handle that
 * changes role instead of being swapped out.
 *
 * Idle, a pill handle with a handset icon sways in the middle of a track. Dragging left turns it
 * red with the handset laid flat (decline); dragging right commits an answer. On answer the
 * handle springs back to the middle while it turns red and the handset swings to the hang-up
 * pose; as soon as it has found its place the track fades and zooms out a little while the pill
 * morphs into a red squircle -- the hang-up button. Outgoing calls start in that final form.
 */
@Composable
internal fun CallDock(
    active: Boolean,
    endMode: Boolean,
    @DrawableRes secondaryIcon: Int,
    secondaryDescription: String,
    switchRequest: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onExit: () -> Unit,
    onSecondary: () -> Unit,
    onUsed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val switchNow by rememberUpdatedState(switchRequest)
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val activeNow by rememberUpdatedState(active)
    val offset = remember { Animatable(0f) }
    // 0 = normal, 1 = call ended without connecting: hang-up button on the left as "exit", a
    // second action button on the right.
    val endShift = remember { Animatable(if (endMode) 1f else 0f) }
    LaunchedEffect(endMode) {
        endShift.animateTo(
            if (endMode) 1f else 0f,
            spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        )
    }
    val endPath = remember { androidx.compose.ui.graphics.Path() }
    val endMatrix = remember { android.graphics.Matrix() }
    var dragging by remember { mutableStateOf(false) }
    // 0 = not decided yet, -1 = declined, 1 = answered. Guards against firing twice.
    var committedDirection by remember { mutableIntStateOf(0) }
    var answered by remember { mutableStateOf(false) }
    // A request to add video mid-call (switchRequest) brings the slider back: the hang-up button
    // opens up into the track again (the incoming-call animation in reverse) until it is answered.
    val hangup = (active && !switchRequest) || answered
    LaunchedEffect(switchRequest) {
        if (!switchRequest) {
            answered = false
            committedDirection = 0
        }
    }

    // Born in its final form when the call is already live (outgoing / re-created activity).
    val toRed = remember { Animatable(if (hangup) 1f else 0f) }
    val shapeMorph = remember { Animatable(if (hangup) 1f else 0f) }
    val trackGone = remember { Animatable(if (hangup) 1f else 0f) }

    val answerDesc = stringResource(R.string.answer_call)
    val declineDesc = stringResource(R.string.dismiss_call)
    val hangUpDesc = stringResource(R.string.hang_up)

    val sway = rememberInfiniteTransition(label = "callSliderSway")
    val swayDegrees by
        sway.animateFloat(
            initialValue = -14f,
            targetValue = 14f,
            animationSpec =
                infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "callSliderSwayValue",
        )

    // Pill (112x72, fully round) -> squircle (88x88, continuous corners: smoothing = 1 gives
    // the superellipse-like curvature instead of a plain circular corner). Built in pixels at
    // each end's real size so nothing is stretched mid-morph.
    val morph =
        remember(density) {
            val pill =
                with(density) {
                    RoundedPolygon.rectangle(
                        width = HANDLE_WIDTH.toPx(),
                        height = HANDLE_HEIGHT.toPx(),
                        rounding = CornerRounding(radius = HANDLE_HEIGHT.toPx() / 2f),
                    )
                }
            val squircle =
                with(density) {
                    RoundedPolygon.rectangle(
                        width = HANGUP_SIZE.toPx(),
                        height = HANGUP_SIZE.toPx(),
                        rounding = CornerRounding(radius = 32.dp.toPx(), smoothing = 1f),
                    )
                }
            Morph(pill, squircle)
        }
    val morphPath = remember { androidx.compose.ui.graphics.Path() }
    val morphMatrix = remember { android.graphics.Matrix() }

    LaunchedEffect(hangup) {
        if (hangup) {
            // Stage A: handle returns to the middle (fast, barely bouncy) while it turns red and
            // the handset swings to the hang-up pose.
            launch {
                offset.animateTo(
                    0f,
                    spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
                )
            }
            launch { toRed.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
            // Stage B starts the moment the handle has found its place, not after its tail.
            snapshotFlow { abs(offset.value) < 3f }.first { it }
            launch { trackGone.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
            shapeMorph.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
        } else {
            launch { trackGone.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
            launch { shapeMorph.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
            toRed.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    BoxWithConstraints(
        modifier =
            Modifier.fillMaxWidth()
                .height(TRACK_HEIGHT)
                .semantics {
                    if (!hangup) {
                        customActions =
                            listOf(
                                CustomAccessibilityAction(answerDesc) {
                                    answered = true
                                    onAccept()
                                    true
                                },
                                CustomAccessibilityAction(declineDesc) {
                                    onDecline()
                                    true
                                },
                            )
                    }
                }
    ) {
        // Stops short of the track's ends by the same gap the handle has above and below it
        // ((96dp track - 72dp handle) / 2), so the margin is even all the way round.
        val edgeGap = 12.dp
        val maxTravelPx = with(density) { ((maxWidth - HANDLE_WIDTH) / 2 - edgeGap).toPx() }
        val p = if (maxTravelPx > 0f) (offset.value / maxTravelPx).coerceIn(-1f, 1f) else 0f

        // The track: dissolves with a slight zoom-out once the handle has settled.
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    alpha = 1f - trackGone.value
                    val s = 1f - 0.08f * trackGone.value
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh)
        ) {
            // Side hints fade as the handle approaches them.
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(
                        if (switchRequest) R.drawable.ic_call_24dp else R.drawable.ic_call_end_24dp
                    ),
                    contentDescription = null,
                    tint = if (switchRequest) SWITCH_BLUE else colors.error,
                    modifier = Modifier.size(28.dp).graphicsLayer { alpha = 1f - (-p).coerceIn(0f, 1f) },
                )
                Icon(
                    painterResource(
                        if (switchRequest) R.drawable.ic_videocam_24dp else R.drawable.ic_call_24dp
                    ),
                    contentDescription = null,
                    tint = if (switchRequest) SWITCH_BLUE else colors.primary,
                    modifier = Modifier.size(28.dp).graphicsLayer { alpha = 1f - p.coerceIn(0f, 1f) },
                )
            }
        }

        // First-time hint / invitation: the handle teases a short slide right, then left, then
        // rests, until the user touches it. Stays well short of the commit threshold so it can
        // never answer.
        LaunchedEffect(hangup, dragging, committedDirection, maxTravelPx) {
            if (hangup || dragging || committedDirection != 0 || maxTravelPx <= 0f) {
                return@LaunchedEffect
            }
            val nudgeSpring = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
            delay(1200)
            while (true) {
                offset.animateTo(maxTravelPx * 0.4f, nudgeSpring)
                delay(250)
                offset.animateTo(-maxTravelPx * 0.4f, nudgeSpring)
                delay(250)
                offset.animateTo(0f, nudgeSpring)
                delay(2200)
            }
        }

        val committed = abs(p) >= SLIDER_COMMIT_FRACTION
        LaunchedEffect(committed) {
            if (committed && !hangup) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }

        // Blending the container colour straight into red passes through a muddy brown around the
        // middle of the drag. So the red ramps in over the first ~40% of the travel (the brown
        // never lingers) and the target is the error red nudged 2% lighter so it reads vivid
        // rather than deep. Rightwards blend is unchanged.
        val leftT = ((-p) / 0.4f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        val vividRed = lerp(colors.error, Color.White, 0.02f)
        // Switch-to-video request: blue pill; right turns it green (camera), left keeps it blue
        // (stay on audio).
        val dragColor: Color =
            if (switchRequest) {
                if (p > 0f) lerp(SWITCH_BLUE, SWITCH_GREEN, (p / 0.4f).coerceIn(0f, 1f)) else SWITCH_BLUE
            } else if (p < 0f) lerp(colors.primaryContainer, vividRed, leftT)
            else lerp(colors.primaryContainer, colors.primary, p)
        val dragIcon: Color =
            if (switchRequest) Color.White
            else if (p < 0f) lerp(colors.onPrimaryContainer, colors.onError, leftT)
            else lerp(colors.onPrimaryContainer, colors.onPrimary, p)
        // Idle: gentle sway. Left: handset laid flat (hang-up pose). Right: settles upright.
        val dragRotation =
            if (switchRequest) swayDegrees * (1f - abs(p))
            else swayDegrees * (1f - abs(p)) + if (p < 0f) 135f * -p else -20f * p
        // In switch mode the handle's icon crossfades from the handset to a camera on the right.
        val cameraAlpha = if (switchRequest) ((p - 0.15f) / 0.3f).coerceIn(0f, 1f) else 0f

        // On answer everything eases to the final hang-up look.
        val handleColor = lerp(dragColor, vividRed, toRed.value)
        val iconColor = lerp(dragIcon, colors.onError, toRed.value)
        val iconRotation = dragRotation * (1f - toRed.value) + 135f * toRed.value

        val handleW = androidx.compose.ui.util.lerp(HANDLE_WIDTH.value, HANGUP_SIZE.value, shapeMorph.value).dp
        val handleH = androidx.compose.ui.util.lerp(HANDLE_HEIGHT.value, HANGUP_SIZE.value, shapeMorph.value).dp
        val flingThresholdPx = with(density) { SLIDER_FLING_DP_PER_S.dp.toPx() }

        // Answer/decline fires from the composition's own scope (not the gesture's), and as soon
        // as the handle reaches an end -- not only on release. A release at the very screen edge
        // can arrive as a cancelled gesture or be swallowed by the system's edge gestures, and
        // when the commit waited for it, a drag all the way across did nothing.
        fun commit(left: Boolean, velocity: Float) {
            if (committedDirection != 0) return
            committedDirection = if (left) -1 else 1
            if (left && !switchNow) {
                scope.launch {
                    offset.animateTo(
                        -maxTravelPx,
                        spring(stiffness = Spring.StiffnessMedium),
                        initialVelocity = velocity,
                    )
                    // If the screen is still here a moment later, put the handle back.
                    delay(900)
                    offset.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                    committedDirection = 0
                }
                onDecline()
            } else {
                answered = true
                // Ride out the end of the drag with the release velocity; the hang-up
                // transition (LaunchedEffect above) then brings the handle home. In switch mode
                // both ends end up here: either answer sends the handle home as a hang-up button.
                if (left) onDecline() else onAccept()
                scope.launch {
                    // If the call never became active (e.g. the microphone permission prompt
                    // was dismissed), undo the transition so the slider can be used again.
                    delay(2500)
                    if (!activeNow || switchNow) {
                        answered = false
                        committedDirection = 0
                    }
                }
            }
        }

        // The second end-of-call button (retry / voicemail), drawn first so it starts out hidden
        // underneath the hang-up button and slides out to the right of it.
        val endShiftPx = with(density) { (HANGUP_SIZE / 2 + 12.dp).toPx() }
        if (endShift.value > 0.01f) {
            Box(
                modifier =
                    Modifier.align(Alignment.Center)
                        .offset { IntOffset((endShiftPx * endShift.value).roundToInt(), 0) }
                        .size(HANGUP_SIZE)
                        .graphicsLayer {
                            val s = 0.7f + 0.3f * endShift.value.coerceIn(0f, 1f)
                            scaleX = s
                            scaleY = s
                            alpha = endShift.value.coerceIn(0f, 1f)
                        }
                        .semantics { contentDescription = secondaryDescription }
                        .clickable(onClick = onSecondary),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    morph.toPath(1f, endPath)
                    endMatrix.reset()
                    endMatrix.postTranslate(size.width / 2f, size.height / 2f)
                    endPath.asAndroidPath().transform(endMatrix)
                    drawPath(endPath, colors.primary)
                }
                Icon(
                    painterResource(secondaryIcon),
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        // The handle itself is the only touch target -- the track around it ignores touches.
        // Canvas sized to the animated handle; the morph path is centred in it.
        Box(
            modifier =
                Modifier.align(Alignment.Center)
                    .offset { IntOffset((offset.value - endShiftPx * endShift.value).roundToInt(), 0) }
                    .size(width = handleW, height = handleH)
                    .semantics { if (hangup) contentDescription = hangUpDesc }
                    // Keep the system's edge back-gesture from stealing a drag near the edge.
                    .systemGestureExclusion()
                    .then(
                        if (hangup) {
                            Modifier.clickable(onClick = if (endMode) onExit else onHangUp)
                        } else {
                            Modifier.draggable(
                                orientation = Orientation.Horizontal,
                                state =
                                    rememberDraggableState { delta ->
                                        if (committedDirection != 0) return@rememberDraggableState
                                        // UNDISPATCHED so the snap happens right now. Dispatched,
                                        // the last few deltas could run after the release spring
                                        // started and cancel it (Animatable lets the newest call
                                        // win), leaving the handle parked.
                                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                            offset.snapTo(
                                                (offset.value + delta)
                                                    .coerceIn(-maxTravelPx, maxTravelPx)
                                            )
                                        }
                                        val reached = offset.value / maxTravelPx
                                        if (reached <= -0.98f) commit(left = true, velocity = 0f)
                                        else if (reached >= 0.98f) commit(left = false, velocity = 0f)
                                    },
                                onDragStarted = {
                                    dragging = true
                                    onUsed()
                                },
                                onDragStopped = { velocity ->
                                    dragging = false
                                    if (committedDirection != 0) return@draggable
                                    val end = offset.value / maxTravelPx
                                    val flungLeft = velocity <= -flingThresholdPx && end < -0.1f
                                    val flungRight = velocity >= flingThresholdPx && end > 0.1f
                                    when {
                                        end <= -SLIDER_COMMIT_FRACTION || flungLeft ->
                                            commit(left = true, velocity = velocity)
                                        end >= SLIDER_COMMIT_FRACTION || flungRight ->
                                            commit(left = false, velocity = velocity)
                                        else ->
                                            // Carries the release velocity: a flick back overshoots.
                                            scope.launch {
                                                offset.animateTo(
                                                    0f,
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow,
                                                    ),
                                                    initialVelocity = velocity,
                                                )
                                            }
                                    }
                                },
                            )
                        }
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                morph.toPath(shapeMorph.value, morphPath)
                morphMatrix.reset()
                morphMatrix.postTranslate(size.width / 2f, size.height / 2f)
                morphPath.asAndroidPath().transform(morphMatrix)
                drawPath(morphPath, handleColor)
            }
            // End of a call that never got going: the hang-up handset gives way to an "exit" cross.
            Icon(
                painterResource(
                    if (endShift.value > 0.5f) R.drawable.ic_clear_24dp else R.drawable.ic_call_24dp
                ),
                contentDescription = null,
                tint = iconColor,
                modifier =
                    Modifier.size(34.dp)
                        .rotate(iconRotation * (1f - endShift.value.coerceIn(0f, 1f)))
                        .graphicsLayer { alpha = 1f - cameraAlpha },
            )
            if (cameraAlpha > 0.01f) {
                Icon(
                    painterResource(R.drawable.ic_videocam_24dp),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(34.dp).graphicsLayer { alpha = cameraAlpha },
                )
            }
        }
    }
}
