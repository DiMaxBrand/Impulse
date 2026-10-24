package eu.siacs.conversations.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.ui.glass.GlassHost
import eu.siacs.conversations.ui.glass.LiquidGlassPanel
import eu.siacs.conversations.ui.glass.LiquidGlassStyle
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The Easter egg of the style picker: long-press "Liquid glass" and the card turns into a piece of
 * glass. A band of light sweeps across it from left to right, glass behind the band and the card
 * in front of it, until the card is gone and only the glass is left (nothing of the card is drawn
 * under it, so the glass does not show it). If the finger is still down when it has settled, the
 * glass is held: it follows the finger on a loose spring, stretching along its motion and wobbling
 * where a real drop would. Let go, and it wobbles back into place. Then it boils for a second, a
 * band of light sweeps back from right to left turning it into the card again, the boiling dies
 * down at the speed it came, and the card's edges (only the container, not its text) wobble a bit
 * once, as if the magic had gone into it.
 */

/** How much of the picker card is glass now (0..1): the options' rows give up their own surfaces as it grows. */
internal val LocalEggFrac = androidx.compose.runtime.staticCompositionLocalOf<() -> Float> { { 0f } }

@Stable
internal class StyleEgg {
    /** How far the glass reaches from the left, as a fraction of the card's width. */
    val frac = Animatable(0f)
    val pop = Animatable(0f)
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    val boil = Animatable(0f)
    val edge = Animatable(0f)
    var time by mutableFloatStateOf(0f)
    var running by mutableStateOf(false)
    var sweeping by mutableStateOf(false)

    /** Where the finger is, in the overlay's own coordinates; null while it is up. */
    var finger by mutableStateOf<Offset?>(null)

    suspend fun play(host: GlassHost?, haptic: HapticFeedback) = coroutineScope {
        if (running) return@coroutineScope
        running = true
        // The colour blobs the glass will show fade in behind everything.
        launch { host?.boost?.animateTo(1f, tween(500)) }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        // 1. Light sweeps left to right, turning the card into glass behind it.
        sweeping = true
        frac.snapTo(0f)
        frac.animateTo(1f, tween(SWEEP_MS, easing = SWEEP_EASING))
        sweeping = false
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        // It lands with a squash that springs back.
        pop.snapTo(1f)
        launch { pop.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 260f)) }
        delay(350)

        // 2. Still held? Then it is the finger's to play with.
        if (finger != null) {
            val anchor = finger!!
            var follow: kotlinx.coroutines.Job? = null
            var last = Offset.Zero
            while (finger != null) {
                val target = finger!! - anchor
                if ((target - last).getDistance() >= 1f) {
                    last = target
                    // A new target takes over where the last one was (velocity is kept).
                    follow?.cancel()
                    follow = launch { offset.animateTo(target, spring(dampingRatio = 0.5f, stiffness = 420f)) }
                }
                delay(16)
            }
            follow?.cancel()
            // Let go: it wobbles back to where it was.
            offset.animateTo(Offset.Zero, spring(dampingRatio = 0.28f, stiffness = 160f))
        }

        // 3. It boils for a second ...
        boil.snapTo(1f)
        delay(1000)
        // ... and light sweeps back from the right, turning it into the card again while the
        // boiling calms at the same speed it came.
        sweeping = true
        launch { boil.animateTo(0f, tween(SWEEP_MS, easing = SWEEP_EASING)) }
        frac.animateTo(0f, tween(SWEEP_MS, easing = SWEEP_EASING))
        sweeping = false
        launch { host?.boost?.animateTo(0f, tween(500)) }

        // 4. The card is back; its edges wobble once (the container only, not its text).
        edge.snapTo(1f)
        edge.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = 230f))
        running = false
    }

    private companion object {
        const val SWEEP_MS = 900
        val SWEEP_EASING = CubicBezierEasing(0.3f, 0f, 0.15f, 1f)
    }
}

/** The picker card as the egg needs it: the card and the glass under one outline, [content] on top of both. */
@Composable
internal fun EggCard(
    egg: StyleEgg,
    host: GlassHost?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // The clock for the boiling.
    LaunchedEffect(egg.running) {
        if (egg.running) {
            while (true) androidx.compose.runtime.withFrameNanos { egg.time = it / 1_000_000_000f }
        }
    }
    val edgeDp = (28f + 12f * egg.edge.value).coerceIn(10f, 44f).dp
    Box(
        modifier.graphicsLayer {
            val o = egg.offset.value
            translationX = o.x
            translationY = o.y
            // Stretched along its motion, as a drop on a loose spring would be.
            val v = egg.offset.velocity
            val sx = (abs(v.x) / 9000f).coerceAtMost(0.18f)
            val sy = (abs(v.y) / 9000f).coerceAtMost(0.18f)
            val b = egg.boil.value
            val t = egg.time
            val p = egg.pop.value
            scaleX = 1f + sx - sy * 0.6f + 0.03f * b * sin(t * 13f) + 0.06f * p
            scaleY = 1f + sy - sx * 0.6f + 0.03f * b * sin(t * 13f + 1.7f) - 0.07f * p
            rotationZ = 0.7f * b * sin(t * 8f) + (v.x / 9000f).coerceIn(-1.5f, 1.5f)
        },
    ) {
        // The card: right of the boundary only, and only its container (the wobble of its edges
        // does not touch what is written on it).
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val e = egg.edge.value
                    scaleX = 1f + 0.025f * e
                    scaleY = 1f - 0.025f * e
                }
                .drawWithContent {
                    clipRect(left = egg.frac.value * size.width) { this@drawWithContent.drawContent() }
                },
        ) {
            Surface(
                shape = RoundedCornerShape(edgeDp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.matchParentSize(),
            ) {}
        }
        // The glass: left of the boundary.
        if (host != null && (egg.frac.value > 0.001f || egg.running)) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawWithContent {
                        clipRect(right = egg.frac.value * size.width) { this@drawWithContent.drawContent() }
                    },
            ) {
                LiquidGlassPanel(
                    glass = host.stack,
                    shape = RoundedCornerShape(28.dp),
                    style = LiquidGlassStyle.Row,
                    modifier = Modifier.matchParentSize(),
                ) {}
            }
        }
        content()
        // The band of light at the boundary while it moves.
        if (egg.sweeping) {
            val tint = MaterialTheme.colorScheme.primary
            Canvas(Modifier.matchParentSize()) {
                val x = egg.frac.value * size.width
                val half = 70.dp.toPx()
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.7f), Color.Transparent),
                        startX = x - half, endX = x + half,
                    ),
                )
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, tint.copy(alpha = 0.35f), Color.Transparent),
                        startX = x - half * 2.4f, endX = x - half * 0.4f,
                    ),
                )
            }
        }
    }
}
