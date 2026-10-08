package eu.siacs.conversations.ui

import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import eu.siacs.conversations.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PIP_W = 112.dp
private val PIP_H = 150.dp
private val EDGE = 16.dp
private val DOCK_BLOCK = 212.dp // mic+speaker row (72 + 28 gap) + 96dp dock + 16dp below
private val CONTROLS_BLOCK = DOCK_BLOCK
private val SIDE_BUTTON = 52.dp

/**
 * Video call layout: the other person fills the screen, you are a rounded self-view in a corner
 * that can be dragged around and tapped to swap. Status and call time sit top-left beside a
 * small avatar; the controls line up on the hang-up button; tapping empty space slides it all
 * away.
 *
 * The two WebRTC renderers are the activity's own views, hosted here. Every move between the
 * "main" and "corner" roles, and every appearing / disappearing, is an animation that simply
 * retargets from wherever it currently is, so quickly toggling a camera never snaps.
 */
@Composable
internal fun VideoCallContent(state: IncomingCallState) {
    if (state.systemPip) {
        SystemPipContent(state)
        return
    }
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var controlsVisible by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var swapped by remember { mutableStateOf(false) }

    val hasRemote = state.hasRemoteVideo
    val hasLocal = state.hasLocalVideo
    val anyVideo = hasRemote || hasLocal
    val both = hasRemote && hasLocal
    LaunchedEffect(both) { if (!both) swapped = false }
    // Their picture is the main one; yours takes over when theirs is off (or after a swap).
    val localMain = hasLocal && (!hasRemote || swapped)

    val spec = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    val localRole by animateFloatAsState(if (localMain) 0f else 1f, spec, label = "localRole")
    val remoteRole by animateFloatAsState(if (localMain) 1f else 0f, spec, label = "remoteRole")
    val localPres by animateFloatAsState(if (hasLocal) 1f else 0f, spec, label = "localPres")
    val remotePres by animateFloatAsState(if (hasRemote) 1f else 0f, spec, label = "remotePres")
    val hide by animateFloatAsState(if (controlsVisible) 0f else 1f, spec, label = "controlsHide")
    val compact by animateFloatAsState(if (anyVideo) 1f else 0f, spec, label = "headerCompact")

    LaunchedEffect(state.endMode) { if (state.endMode) controlsVisible = true }
    // Controls tuck themselves away a few seconds after the last touch while video is showing.
    LaunchedEffect(controlsVisible, tick, anyVideo, state.endMode) {
        if (controlsVisible && anyVideo && !state.endMode) {
            delay(6000)
            controlsVisible = false
        }
    }

    BoxWithConstraints(
        modifier =
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                        tick++
                    }
                )
            }
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val pipW = with(density) { PIP_W.toPx() }
        val pipH = with(density) { PIP_H.toPx() }
        val edge = with(density) { EDGE.toPx() }
        val topInset = WindowInsets.safeDrawing.getTop(density).toFloat()
        val bottomInset = WindowInsets.safeDrawing.getBottom(density).toFloat()
        var headerH by remember { mutableFloatStateOf(0f) }
        var headerW by remember { mutableFloatStateOf(0f) }
        val dockTop = h - bottomInset - with(density) { DOCK_BLOCK.toPx() }

        // ---- the corner self-view's resting place -------------------------------------------
        var side by remember { mutableIntStateOf(1) } // 1 = right, -1 = left
        var desiredY by remember { mutableFloatStateOf(0f) }
        var releaseTick by remember { mutableIntStateOf(0) }
        var dragging by remember { mutableStateOf(false) }
        var positioned by remember { mutableStateOf(false) }
        val pipAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

        // Never over the header text (left side) or the controls: just above them, or the whole
        // screen once they have slid away.
        fun restPos(): Offset {
            val x = if (side > 0) w - pipW - edge else edge
            val yMin =
                if (side < 0 && hide < 0.5f && anyVideo) topInset + edge + headerH + edge
                else topInset + edge
            val yMax = (if (hide < 0.5f) dockTop - edge else h - bottomInset - edge) - pipH
            return Offset(x, desiredY.coerceIn(yMin, max(yMin, yMax)))
        }

        LaunchedEffect(side, desiredY, releaseTick, hide < 0.5f, w, h, headerH, both) {
            if (w <= 0f || dragging) return@LaunchedEffect
            val target = restPos()
            if (!positioned) {
                pipAnim.snapTo(target)
                positioned = true
            } else {
                pipAnim.animateTo(
                    target,
                    spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                )
            }
        }

        // ---- background ---------------------------------------------------------------------
        val black = max(localPres * (1f - localRole), remotePres * (1f - remoteRole))
        Box(
            Modifier.fillMaxSize()
                .background(lerp(colors.surface, Color.Black, black.coerceIn(0f, 1f)))
        )

        // ---- no video at all: the big avatar and status, like an audio call ------------------
        if (compact < 0.99f) {
            Column(
                modifier =
                    Modifier.align(Alignment.Center).graphicsLayer {
                        alpha = 1f - compact
                        val s = 1f - 0.15f * compact
                        scaleX = s
                        scaleY = s
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val big = min(min(w / density.density * 0.8f, 320f), h / density.density * 0.4f).dp
                MorphingCallAvatar(state.avatar, state.established, state.reconnecting, Modifier.size(big))
                StatusLines(state, onVideo = false, centered = true)
            }
        }

        // ---- the two videos -----------------------------------------------------------------
        val pip = pipAnim.value
        fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
        val corner = with(density) { 24.dp.toPx() }
        VideoSurface(
            view = state.remoteVideoView,
            x = lerpF(0f, pip.x, remoteRole),
            y = lerpF(0f, pip.y, remoteRole),
            width = lerpF(w, pipW, remoteRole),
            height = lerpF(h, pipH, remoteRole),
            cornerPx = lerpF(0f, corner, remoteRole),
            presence = remotePres,
            overlay = remoteRole > 0.5f,
        )
        VideoSurface(
            view = state.localVideoView,
            x = lerpF(0f, pip.x, localRole),
            y = lerpF(0f, pip.y, localRole),
            width = lerpF(w, pipW, localRole),
            height = lerpF(h, pipH, localRole),
            cornerPx = lerpF(0f, corner, localRole),
            presence = localPres,
            overlay = localRole > 0.5f,
        )

        // Touch layer over the corner view: drag it anywhere (it follows the finger with a little
        // spring), let go and it slides to the nearest side; tap to swap with the main video.
        if (both) {
            Box(
                Modifier.offset { IntOffset(pip.x.roundToInt(), pip.y.roundToInt()) }
                    .size(PIP_W, PIP_H)
                    .pointerInput(Unit) {
                        var target = Offset.Zero
                        fun finish() {
                            dragging = false
                            side = if (target.x + pipW / 2f > w / 2f) 1 else -1
                            desiredY = target.y
                            releaseTick++
                            tick++
                        }
                        detectDragGestures(
                            onDragStart = {
                                dragging = true
                                target = pipAnim.value
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                target += amount
                                scope.launch {
                                    pipAnim.animateTo(
                                        target,
                                        spring(dampingRatio = 1f, stiffness = 700f),
                                    )
                                }
                            },
                            onDragEnd = { finish() },
                            onDragCancel = { finish() },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                swapped = !swapped
                                tick++
                            }
                        )
                    }
            )
        }

        // ---- header: avatar + status + time, top left ---------------------------------------
        val textColor = if (black > 0.5f) Color.White else colors.onSurface
        if (compact > 0.01f) {
            Row(
                modifier =
                    Modifier.align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(EDGE)
                        .onSizeChanged {
                            headerH = it.height.toFloat()
                            headerW = it.width.toFloat()
                        }
                        .graphicsLayer {
                            alpha = (compact * (1f - hide)).coerceIn(0f, 1f)
                            translationX = -((1f - compact) * 60.dp.toPx() + hide * (headerW + edge))
                        }
                        .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MorphingCallAvatar(
                    state.avatar,
                    state.established,
                    state.reconnecting,
                    Modifier.fillMaxHeight().aspectRatio(1f),
                    scale = 0.9f,
                )
                Box(Modifier.padding(start = 10.dp)) {
                    StatusLines(state, onVideo = black > 0.5f, centered = false)
                }
            }
        }

        // ---- controls -----------------------------------------------------------------------
        val sidesIn = remember { Animatable(0f) }
        LaunchedEffect(state.endMode) {
            sidesIn.animateTo(if (state.endMode) 0f else 1f, spec)
        }
        val flipPresence by
            animateFloatAsState(
                if (state.cameraSwitchable && hasLocal) 1f else 0f,
                spec,
                label = "flipPresence",
            )
        Box(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = EDGE)
                    .padding(bottom = 16.dp)
                    .height(CONTROLS_BLOCK - 16.dp)
        ) {
            // Microphone and speaker sit diagonally above the hang-up button, exactly as in an
            // audio call, and slide down with it. The hang-up button (and, after a failed call,
            // exit + retry) is the dock's handle.
            Column(
                modifier =
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().graphicsLayer {
                        translationY = hide * (CONTROLS_BLOCK.toPx() + 24.dp.toPx())
                        alpha = 1f - hide
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.animation.AnimatedVisibility(visible = !state.endMode) {
                    Row(
                        modifier = Modifier.padding(bottom = 28.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp),
                    ) {
                        CallToggleButton(
                            checked = !state.micOn,
                            icon =
                                if (state.micOn) R.drawable.ic_mic_24dp
                                else R.drawable.ic_mic_off_24dp,
                            description = stringResource(R.string.call_button_mute),
                            onClick = {
                                tick++
                                state.onToggleMic?.run()
                            },
                        )
                        CallToggleButton(
                            checked = false,
                            icon = state.audioIcon,
                            description = stringResource(R.string.audio_output_choose),
                            enabled = state.audioChoices >= 2,
                            onClick = {
                                tick++
                                state.onAudioOutput?.run()
                            },
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(96.dp)) {
                    CallDock(
                        active = true,
                        endMode = state.endMode,
                        secondaryIcon = state.secondaryIcon,
                        secondaryDescription = state.secondaryDescription,
                        switchRequest = false,
                        onAccept = {},
                        onDecline = {},
                        onHangUp = { state.onHangUp?.run() },
                        onExit = { state.onExit?.run() },
                        onSecondary = { state.onSecondary?.run() },
                        onUsed = {},
                    )
                }
            }
            // Only the camera buttons come in from the sides, left and right of the hang-up
            // button, and they leave sideways when the controls are dismissed.
            val half = w / 2f
            @Composable
            fun SideButton(slotDp: Float, presence: Float, content: @Composable () -> Unit) {
                val presenceNow = presence * sidesIn.value
                if (presenceNow <= 0.01f) return
                val direction = if (slotDp < 0) -1f else 1f
                Box(
                    Modifier.align(Alignment.BottomCenter)
                        .height(96.dp)
                        .graphicsLayer {
                            translationX =
                                slotDp.dp.toPx() +
                                    direction * half * ((1f - presenceNow) + hide)
                            alpha = (presenceNow * (1f - hide)).coerceIn(0f, 1f)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            }
            SideButton(-82f, 1f) {
                CallToggleButton(
                    checked = !hasLocal,
                    icon =
                        if (hasLocal) R.drawable.ic_videocam_24dp else R.drawable.ic_videocam_off_24dp,
                    description =
                        stringResource(
                            if (hasLocal) R.string.video_is_enabled_tap_to_disable
                            else R.string.video_is_disabled_tap_to_enable
                        ),
                    onClick = {
                        tick++
                        state.onToggleCamera?.run()
                    },
                    size = SIDE_BUTTON,
                )
            }
            SideButton(82f, flipPresence) {
                CallToggleButton(
                    checked = false,
                    icon = R.drawable.ic_flip_camera_android_24dp,
                    description = stringResource(R.string.flip_camera),
                    onClick = {
                        tick++
                        state.onFlipCamera?.run()
                    },
                    size = SIDE_BUTTON,
                )
            }
        }
    }
}

/** Status line and call time; [onVideo] draws them white with a soft shadow over video. */
@Composable
private fun StatusLines(state: IncomingCallState, onVideo: Boolean, centered: Boolean) {
    val colors = MaterialTheme.colorScheme
    val shadow = if (onVideo) Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f) else null
    val statusColor = if (onVideo) Color.White else colors.onSurfaceVariant
    val timeColor = if (onVideo) Color.White else colors.onSurface
    Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            text = state.status,
            style = MaterialTheme.typography.titleMedium.copy(shadow = shadow),
            color = statusColor,
            modifier = Modifier.padding(top = if (centered) 12.dp else 0.dp),
        )
        if (state.duration.isNotEmpty()) {
            Text(
                text = state.duration,
                style = MaterialTheme.typography.titleLarge.copy(shadow = shadow),
                fontFamily = FontFamily.Monospace,
                color = timeColor,
            )
        } else if (!centered) {
            // Keep the second line's height before the call has a time: the header avatar is
            // sized to these two lines, and with one it was a tiny dot (or looked missing).
            Text(
                text = " ",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/**
 * Hosts one of the activity's WebRTC renderers at the given rectangle. [presence] 0..1 fades it
 * and shrinks it slightly, so a camera switching off zooms out and fades rather than vanishing.
 */
@Composable
private fun VideoSurface(
    view: View?,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    cornerPx: Float,
    presence: Float,
    overlay: Boolean,
) {
    if (view == null || presence <= 0.01f) return
    val density = LocalDensity.current
    AndroidView(
        factory = {
            // A view can only have one parent; the activity detached it, but be safe on re-entry.
            (view.parent as? ViewGroup)?.removeView(view)
            view
        },
        modifier =
            Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(
                    with(density) { width.toDp() },
                    with(density) { height.toDp() },
                )
                .graphicsLayer {
                    alpha = presence.coerceIn(0f, 1f)
                    val s = 0.85f + 0.15f * presence
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(with(density) { cornerPx.coerceAtLeast(0f).toDp() })),
        update = { v ->
            // The self-view's surface sits above the main one.
            if (v.getTag(R.id.rtp_video_overlay_tag) != overlay) {
                (v as? SurfaceView)?.setZOrderMediaOverlay(overlay)
                v.setTag(R.id.rtp_video_overlay_tag, overlay)
            }
            // A SurfaceView is not clipped by the compose clip above; ask the view itself.
            v.outlineProvider =
                object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
                    }
                }
            v.clipToOutline = cornerPx > 0.5f
            v.invalidateOutline()
        },
    )
}

/** The system's small picture-in-picture window: just the other person, nothing else. */
@Composable
private fun SystemPipContent(state: IncomingCallState) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        VideoSurface(
            view = if (state.hasRemoteVideo) state.remoteVideoView else state.localVideoView,
            x = 0f,
            y = 0f,
            width = w,
            height = h,
            cornerPx = 0f,
            presence = 1f,
            overlay = false,
        )
    }
}
