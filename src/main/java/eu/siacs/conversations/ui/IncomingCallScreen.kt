package eu.siacs.conversations.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.systemGestureExclusion
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import eu.siacs.conversations.R
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * State the (View-based) RtpSessionActivity pushes into the Compose incoming-call layer. Java
 * calls the generated setters; the composables read the snapshot state directly.
 */
class IncomingCallState {
    private var visibleState by mutableStateOf(false)
    private var sliderState by mutableStateOf(true)
    private var avatarState by mutableStateOf<ImageBitmap?>(null)
    private var hintState by mutableStateOf(false)

    var onAccept: Runnable? = null
    var onDecline: Runnable? = null
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

    internal val visible get() = visibleState
    internal val sliderVisible get() = sliderState
    internal val avatar get() = avatarState
    internal val hint get() = hintState
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

private const val SLIDER_COMMIT_FRACTION = 0.55f
private const val SLIDER_FLING_DP_PER_S = 900f

@Composable
internal fun IncomingCallContent(state: IncomingCallState) {
    if (!state.visible) return
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)) {
        val avatarSize = min(min(maxWidth.value, 320f), maxHeight.value * 0.45f).dp
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            MorphingCallAvatar(state.avatar, Modifier.size(avatarSize))
            Spacer(Modifier.weight(1f))
            if (state.sliderVisible) {
                if (state.hint) {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.call_slider_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
                CallSlider(
                    nudge = true,
                    onAccept = { state.onAccept?.run() },
                    onDecline = { state.onDecline?.run() },
                    onUsed = { state.onSliderUsed?.run() },
                )
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

/**
 * The caller's picture, clipped to a shape that keeps morphing between [CALL_AVATAR_SHAPES] and
 * slowly turns. Only the clip outline rotates -- the photo itself stays upright.
 */
@Composable
private fun MorphingCallAvatar(avatar: ImageBitmap?, modifier: Modifier = Modifier) {
    val shapes = CALL_AVATAR_SHAPES
    val fromShape = remember { mutableStateOf(shapes[0]) }
    val toShape = remember { mutableStateOf(shapes[0]) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        var index = 0
        while (true) {
            delay(1700)
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
        val scale = 0.76f
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
 * Pill-shaped answer/decline slider: a handle with a handset icon sits in the middle and sways
 * while idle. Dragging left turns it red with the handset laid flat (hang up); dragging right
 * turns it primary with the handset upright (answer). Past [SLIDER_COMMIT_FRACTION] on release it
 * commits; otherwise it springs back.
 */
@Composable
private fun CallSlider(
    nudge: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onUsed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val offset = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val answerDesc = stringResource(R.string.answer_call)
    val declineDesc = stringResource(R.string.dismiss_call)

    val sway = rememberInfiniteTransition(label = "callSliderSway")
    val swayDegrees by
        sway.animateFloat(
            initialValue = -14f,
            targetValue = 14f,
            animationSpec =
                infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "callSliderSwayValue",
        )

    BoxWithConstraints(
        modifier =
            Modifier.fillMaxWidth()
                .height(96.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh)
                .semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(answerDesc) {
                                onAccept()
                                true
                            },
                            CustomAccessibilityAction(declineDesc) {
                                onDecline()
                                true
                            },
                        )
                }
    ) {
        val handleWidth = 112.dp
        // Stops short of the track's ends by the same gap the handle has above and below it
        // ((96dp track - 72dp handle) / 2), so the margin is even all the way round.
        val edgeGap = 12.dp
        val maxTravelPx = with(density) { ((maxWidth - handleWidth) / 2 - edgeGap).toPx() }
        val p = if (maxTravelPx > 0f) (offset.value / maxTravelPx).coerceIn(-1f, 1f) else 0f

        // First-time hint: the handle teases a short slide right, then left, then rests, until the
        // user touches it. Stays well short of the commit threshold so it can never answer.
        LaunchedEffect(nudge, dragging, maxTravelPx) {
            if (!nudge || dragging || maxTravelPx <= 0f) return@LaunchedEffect
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
            if (committed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }

        // Side hints fade as the handle approaches them.
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_call_end_24dp),
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(28.dp).alpha(1f - (-p).coerceIn(0f, 1f)),
            )
            Icon(
                painterResource(R.drawable.ic_call_24dp),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(28.dp).alpha(1f - p.coerceIn(0f, 1f)),
            )
        }

        // Blending the container colour straight into red passes through a muddy brown around the
        // middle of the drag. So the red ramps in over the first ~40% of the travel (the brown
        // never lingers) and the target is the error red nudged 2% lighter so it reads vivid
        // rather than deep. Rightwards blend is unchanged.
        val leftT = ((-p) / 0.4f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        val vividRed = lerp(colors.error, Color.White, 0.02f)
        val handleColor: Color =
            if (p < 0f) lerp(colors.primaryContainer, vividRed, leftT)
            else lerp(colors.primaryContainer, colors.primary, p)
        val iconColor: Color =
            if (p < 0f) lerp(colors.onPrimaryContainer, colors.onError, leftT)
            else lerp(colors.onPrimaryContainer, colors.onPrimary, p)
        // Idle: gentle sway. Left: handset laid flat (hang-up pose). Right: settles upright.
        val iconRotation =
            swayDegrees * (1f - abs(p)) + if (p < 0f) 135f * -p else -20f * p

        Box(
            modifier =
                Modifier.align(Alignment.Center)
                    .offset { IntOffset(offset.value.roundToInt(), 0) }
                    .size(width = handleWidth, height = 72.dp)
                    .clip(CircleShape)
                    .background(handleColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_call_24dp),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(34.dp).rotate(iconRotation),
            )
        }

        // The whole track is the touch target, not just the handle -- a thumb that lands beside
        // the handle still drags it. Sits on top of the handle, which has no gestures of its own.
        val flingThresholdPx = with(density) { SLIDER_FLING_DP_PER_S.dp.toPx() }
        Box(
            Modifier.fillMaxSize()
                // Keep the system's edge back-gesture from stealing a drag that ends near the edge.
                .systemGestureExclusion()
                .draggable(
                    orientation = Orientation.Horizontal,
                    state =
                        rememberDraggableState { delta ->
                            // UNDISPATCHED so the snap happens right now. Dispatched, the last
                            // few deltas could run after onDragStopped's spring started and
                            // cancel it (Animatable lets the newest call win), leaving the
                            // handle parked until something else moved it.
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                offset.snapTo(
                                    (offset.value + delta).coerceIn(-maxTravelPx, maxTravelPx)
                                )
                            }
                        },
                    onDragStarted = {
                        dragging = true
                        onUsed()
                    },
                    onDragStopped = { velocity ->
                        dragging = false
                        val end = offset.value / maxTravelPx
                        val flungLeft = velocity <= -flingThresholdPx && end < -0.1f
                        val flungRight = velocity >= flingThresholdPx && end > 0.1f
                        when {
                            end <= -SLIDER_COMMIT_FRACTION || flungLeft -> {
                                offset.animateTo(
                                    -maxTravelPx,
                                    spring(stiffness = Spring.StiffnessMedium),
                                    initialVelocity = velocity,
                                )
                                onDecline()
                            }
                            end >= SLIDER_COMMIT_FRACTION || flungRight -> {
                                offset.animateTo(
                                    maxTravelPx,
                                    spring(stiffness = Spring.StiffnessMedium),
                                    initialVelocity = velocity,
                                )
                                onAccept()
                            }
                            else ->
                                // Carries the release velocity, so a flick back overshoots.
                                offset.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow,
                                    ),
                                    initialVelocity = velocity,
                                )
                        }
                    },
                )
        )
    }
}
