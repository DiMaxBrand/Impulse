package eu.siacs.conversations.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import eu.siacs.conversations.FeatureFlag
import eu.siacs.conversations.utils.FeatureFlagPreferences
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.tanh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The "liquid glass" experiment (feature flag [FeatureFlag.LIQUID_GLASS], off by default). Glass is
 * drawn with the Kyant0 backdrop library: whatever sits behind a glass element is copied into it,
 * blurred and bent at the edges. The pieces here are the shared parts -- the glass surface itself,
 * and the stretch physics that makes it feel like a drop of liquid; the screens using them are
 * [LiquidTopRow] (chat list) and the glass variant of the chat's message bar.
 */
object LiquidGlass {
    /** The refraction is an AGSL runtime shader, which needs Android 13. Below that the flag does
     * nothing and every screen draws its usual Material look. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** How far a finger may drag a bubble, in dp, before it stops following (rubber band). */
    internal const val DRAG_LIMIT_DP = 56
}

/** True when the flag is on and the phone can draw the effect. Read once per screen opening. */
@Composable
fun rememberLiquidGlassEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        LiquidGlass.supported && FeatureFlagPreferences(context).isEnabled(FeatureFlag.LIQUID_GLASS)
    }
}

/**
 * The same effect stack on every glass element: a touch more colour, a soft blur, then the lens
 * that bends the picture along the rim. (The library wants them in exactly this order.) A top-level
 * value, so the modifier sees the same lambda every time and does not redo its work.
 */
private val GlassEffects: BackdropEffectScope.() -> Unit = {
    vibrancy()
    blur(6.dp.toPx())
    // The lens may not be taller than the smallest corner radius of the shape.
    val corner = (shape as? CornerBasedShape)?.topStart?.toPx(size, this) ?: 0f
    val height = min(14.dp.toPx(), min(corner, size.minDimension / 2f))
    lens(
        refractionHeight = height,
        refractionAmount = min(28.dp.toPx(), size.minDimension),
    )
}

private val GlassHighlight = { Highlight(width = 1.dp, alpha = 0.85f) }
private val GlassShadow = { Shadow(radius = 14.dp, color = Color.Black.copy(alpha = 0.14f)) }

/**
 * A glass bubble. [backdrop] is what shows through it (a `layerBackdrop` of the content behind);
 * [state] makes it squash, stretch and spring back; [tint] is laid over the glass to give it body.
 * The bubble is as big as its [content] and the incoming constraints make it.
 */
@Composable
fun LiquidGlassSurface(
    backdrop: Backdrop,
    shape: CornerBasedShape,
    modifier: Modifier = Modifier,
    state: LiquidState = rememberLiquidState(),
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
    content: @Composable BoxScope.() -> Unit,
) {
    // The outer box takes the touches in a frame that never moves; only the inner one is
    // stretched, or the finger would be chasing the thing it is dragging.
    Box(modifier = modifier, propagateMinConstraints = true) {
        Box(
            modifier = Modifier.liquidGlass(backdrop, shape, state, tint),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/**
 * Turns whatever this is applied to into glass over [backdrop]. A modifier, not a wrapper, so an
 * existing Material piece (a button, a surface) can become glass without being rebuilt: give it a
 * transparent container and add this. With a null [backdrop] it does nothing, which lets one piece
 * of UI serve both the ordinary look and the experiment.
 */
fun Modifier.liquidGlass(
    backdrop: Backdrop?,
    shape: CornerBasedShape,
    state: LiquidState,
    tint: Color,
): Modifier =
    if (backdrop == null) this
    else drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = GlassEffects,
        highlight = GlassHighlight,
        shadow = GlassShadow,
        layerBlock = state.layerBlock,
        onDrawSurface = { drawRect(tint) },
    )

/** The usual body colour of a glass bubble: a little of the surface colour over the glass. */
@Composable
fun glassTint(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f)

@Composable
fun rememberLiquidState(): LiquidState {
    val scope = rememberCoroutineScope()
    return remember { LiquidState(scope) }
}

/**
 * The physics of a glass bubble. A press swells it a little; dragging pulls it toward the finger
 * and stretches it along the drag (squeezing it across, so it keeps its volume); letting go springs
 * it back with a wobble. [pulse] gives it a one-off squish from outside, e.g. when a text line wraps.
 */
@Stable
class LiquidState internal constructor(private val scope: CoroutineScope) {
    private val press = Animatable(0f)
    private val dragX = Animatable(0f)
    private val dragY = Animatable(0f)
    private val pulseX = Animatable(0f)
    private val pulseY = Animatable(0f)
    // A resting size other than 1, set from outside (the send button shrinks as the field grows).
    private val base = Animatable(1f)

    /** Applied to the bubble (and handed to the backdrop, so the glass samples the right place). */
    val layerBlock: GraphicsLayerScope.() -> Unit = {
        val maxStretch = 18.dp.toPx()
        val limit = LiquidGlass.DRAG_LIMIT_DP.dp.toPx()
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val ex = (abs(dragX.value) / limit).coerceIn(0f, 1f)
        val ey = (abs(dragY.value) / limit).coerceIn(0f, 1f)
        val swell = base.value * (1f + 0.06f * press.value)
        scaleX = swell * (1f + (ex - 0.35f * ey) * maxStretch / w) * (1f + pulseX.value)
        scaleY = swell * (1f + (ey - 0.35f * ex) * maxStretch / h) * (1f + pulseY.value)
        translationX = dragX.value * 0.5f
        translationY = dragY.value * 0.5f
        // Pinned on the side away from the finger, so the bubble reaches toward it.
        transformOrigin = TransformOrigin(
            (0.5f - 0.5f * ex * sign(dragX.value)).coerceIn(0f, 1f),
            (0.5f - 0.5f * ey * sign(dragY.value)).coerceIn(0f, 1f),
        )
    }

    internal fun setPressed(down: Boolean) {
        scope.launch {
            press.animateTo(if (down) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 500f))
        }
    }

    internal fun dragTo(dx: Float, dy: Float) {
        scope.launch {
            dragX.snapTo(dx)
            dragY.snapTo(dy)
        }
    }

    internal fun settle() {
        scope.launch { dragX.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 260f)) }
        scope.launch { dragY.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 260f)) }
    }

    /** Settles at a new resting size, with a bouncy spring so it overshoots and wobbles into place. */
    fun scaleTo(target: Float) {
        scope.launch { base.animateTo(target, spring(dampingRatio = 0.45f, stiffness = 300f)) }
    }

    /** A one-off squish: [x] and [y] are fractions of the size (negative = squeezed). It springs back. */
    fun pulse(x: Float = 0f, y: Float = 0f) {
        scope.launch {
            pulseX.snapTo(x)
            pulseX.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 220f))
        }
        scope.launch {
            pulseY.snapTo(y)
            pulseY.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 220f))
        }
    }
}

/** A drag follows the finger less and less the further it goes. */
private fun rubber(distance: Float, limit: Float): Float = limit * tanh(distance / limit)

/**
 * Tap, with the bubble stretching under the finger. For a bubble that is the whole button; the tap
 * runs [onClick] only when the finger did not travel (a drag is just a stretch).
 */
@Composable
fun Modifier.liquidTouch(state: LiquidState, onClick: () -> Unit): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    return pointerInput(state) {
        val limit = LiquidGlass.DRAG_LIMIT_DP.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            state.setPressed(true)
            var moved = false
            var clicked = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUp()) {
                    clicked = !moved
                    break
                }
                if (!change.pressed) break
                val delta = change.position - down.position
                if (!moved && delta.getDistance() > viewConfiguration.touchSlop) moved = true
                if (moved) {
                    state.dragTo(rubber(delta.x, limit), rubber(delta.y, limit))
                    change.consume()
                }
            }
            state.setPressed(false)
            state.settle()
            if (clicked) currentOnClick()
        }
    }
}

/**
 * Only the stretch, no tap: for a bubble that holds its own buttons or a text field. It watches the
 * touches before the children do and never consumes them, so the children work as usual.
 */
@Composable
fun Modifier.liquidFeel(state: LiquidState): Modifier = pointerInput(state) {
    val limit = LiquidGlass.DRAG_LIMIT_DP.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        state.setPressed(true)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val delta = change.position - down.position
            if (delta.getDistance() > viewConfiguration.touchSlop) {
                state.dragTo(rubber(delta.x, limit), rubber(delta.y, limit))
            }
        }
        state.setPressed(false)
        state.settle()
    }
}
