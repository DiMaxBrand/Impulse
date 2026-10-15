package eu.siacs.conversations.ui.glass

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.lens
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/*
 * The touch mechanics of the liquid glass experiment: how a glass piece lights up under the
 * finger, resists being dragged, and squashes with speed. Ported from the demo (catalog) app of the
 * Backdrop library, https://github.com/Kyant0/AndroidLiquidGlass (Apache License 2.0), keeping its
 * spring numbers: they are what makes it feel like the demo and not merely springy. Adapted to this
 * app: the Android runtime shader is used directly, the clock is SystemClock.
 */

/**
 * Watches a finger without ever consuming it: starts the moment it touches down (no touch-slop
 * wait), reports every movement, and ends on lift or cancel. Taps and the like keep working for
 * whatever else is on the same element. (The demo has the same helper under the same name.)
 */
suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onDragStart(down)
        var last = down.position
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                onDragCancel()
                return@awaitEachGesture
            }
            if (change.changedToUpIgnoreConsumed()) {
                onDragEnd()
                return@awaitEachGesture
            }
            if (!change.pressed) {
                onDragCancel()
                return@awaitEachGesture
            }
            val delta = change.position - last
            last = change.position
            if (delta != Offset.Zero) onDrag(change, delta)
        }
    }
}

/**
 * The glow of a pressed glass piece: the whole surface brightens a touch and a soft light follows
 * the finger, then both fade out when it lifts. [offset] is how far the finger has moved from where
 * it landed; the piece uses it to lean toward the finger (see [LiquidGlassButton]).
 *
 * [position] lets a piece put the light somewhere other than under the finger (the bottom tab
 * bar keeps it on the selected tab).
 */
class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
) {
    private val pressProgressAnimationSpec = spring<Float>(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring<Offset>(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero

    /** 0 at rest, 1 fully pressed; eased with a spring both ways. */
    val pressProgress: Float get() = pressProgressAnimation.value

    /** Finger position relative to where it first landed. */
    val offset: Offset get() = positionAnimation.value - startPosition

    // AGSL, so Android 13+. Below that the surface just brightens evenly.
    private val shader: android.graphics.RuntimeShader? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.graphics.RuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
            )
        } else {
            null
        }

    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = pressProgressAnimation.value
            if (progress > 0f) {
                val shader = shader
                if (shader != null) {
                    drawRect(Color.White.copy(0.08f * progress), blendMode = BlendMode.Plus)
                    val at = position(size, positionAnimation.value)
                    shader.setFloatUniform("size", size.width, size.height)
                    shader.setColorUniform("color", Color.White.copy(0.15f * progress).toArgb())
                    shader.setFloatUniform("radius", size.minDimension * 1.5f)
                    shader.setFloatUniform(
                        "position",
                        at.x.fastCoerceIn(0f, size.width),
                        at.y.fastCoerceIn(0f, size.height),
                    )
                    drawRect(ShaderBrush(shader), blendMode = BlendMode.Plus)
                } else {
                    drawRect(Color.White.copy(0.25f * progress), blendMode = BlendMode.Plus)
                }
            }
            drawContent()
        }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            inspectDragGestures(
                onDragStart = { down ->
                    startPosition = down.position
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                        launch { positionAnimation.snapTo(startPosition) }
                    }
                },
                onDragEnd = { release() },
                onDragCancel = { release() },
            ) { change, _ ->
                animationScope.launch { positionAnimation.snapTo(change.position) }
            }
        }

    private fun release() {
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
        }
    }
}

@Composable
fun rememberInteractiveHighlight(): InteractiveHighlight {
    val scope = rememberCoroutineScope()
    return remember(scope) { InteractiveHighlight(scope) }
}

/**
 * A value that follows a drag like something thick: it eases to where the finger sends it on a stiff
 * spring, while a looser spring tracks how fast it is moving so the piece can squash with speed, and
 * a pressed piece swells (and shrinks back when let go). Used for a slider thumb, a toggle thumb and
 * the selection bubble of the tab bar.
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimationSpec = spring<Float>(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring<Float>(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring<Float>(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring<Float>(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring<Float>(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier =
        Modifier.pointerInput(Unit) {
            inspectDragGestures(
                onDragStart = { down ->
                    onDragStarted(down.position)
                    press()
                },
                onDragEnd = {
                    onDragStopped()
                    release()
                },
                onDragCancel = {
                    onDragStopped()
                    release()
                },
            ) { _, dragAmount ->
                onDrag(size, dragAmount)
            }
        }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            withFrameNanos { }
            if (value != targetValue) {
                // Let go only once the value has nearly arrived, so the swell outlasts the move.
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        animationScope.launch {
            launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) { updateVelocity() } }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val targetValue = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(value, 0f))
        val targetVelocity =
            velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}

/**
 * The lens, kept inside what it can draw: it may not be taller than the smallest corner radius of
 * the shape it is bending light for, so a small or squarish bubble asks for less than a big pill.
 */
fun BackdropEffectScope.glassLens(
    refractionHeight: Float,
    refractionAmount: Float,
    chromaticAberration: Boolean = false,
) {
    val corner = (shape as? CornerBasedShape)?.topStart?.toPx(size, this) ?: 0f
    val height = min(refractionHeight, min(corner, size.minDimension / 2f))
    lens(
        refractionHeight = height,
        refractionAmount = min(refractionAmount, size.minDimension),
        chromaticAberration = chromaticAberration,
    )
}
