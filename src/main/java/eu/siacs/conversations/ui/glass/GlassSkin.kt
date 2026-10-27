package eu.siacs.conversations.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay

/*
 * A settings screen that can be Material or liquid glass, and turns from one into the other in
 * front of you. The host ([GlassSkinHost]) paints a coloured backdrop for the glass to show and
 * hands the screen a [GlassSkin]; the screen's usual rows ([GlassGroupRow] stands in for the grouped
 * Material row), switches and sliders check [LocalGlassSkin] and draw as glass when it is there.
 * Turning to glass is a jelly: every row squashes, springs past its size and wobbles into place,
 * one after another from the top, while a band of light sweeps across the screen.
 */

@Stable
class GlassSkin internal constructor(
    val stack: GlassStack,
    val backdrop: Backdrop,
    /** 0 = Material, 1 = glass, eased: how far the screen has turned. */
    internal val reveal: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    /** Changes every time the glass is put on, so rows wobble again. */
    internal val epoch: Int,
)

val LocalGlassSkin = staticCompositionLocalOf<GlassSkin?> { null }

/**
 * The glass backdrop of a [GlassSkinHost] screen, there even while the screen is Material (a piece
 * of glass can be brought out over it, as in the style picker's Easter egg). [boost] fades the
 * colour blobs in without turning the screen to glass.
 */
@Stable
class GlassHost internal constructor(
    val stack: GlassStack,
    val boost: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
)

val LocalGlassHost = staticCompositionLocalOf<GlassHost?> { null }

private val SweepEasing = CubicBezierEasing(0.2f, 0.0f, 0.1f, 1.0f)

@Composable
fun GlassSkinHost(
    glass: Boolean,
    celebrate: Int,
    haptics: Boolean,
    content: @Composable () -> Unit,
) {
    val reveal = remember { Animatable(if (glass) 1f else 0f) }
    LaunchedEffect(glass) {
        reveal.animateTo(if (glass) 1f else 0f, spring(stiffness = 120f, dampingRatio = 1f))
    }
    val layer = rememberLayerBackdrop()
    val stack = rememberGlassStack(layer)
    val boost = remember { Animatable(0f) }
    val host = remember(stack) { GlassHost(stack, boost) }
    val surface = MaterialTheme.colorScheme.surface
    val a = MaterialTheme.colorScheme.primaryContainer
    val b = MaterialTheme.colorScheme.tertiaryContainer
    val c = MaterialTheme.colorScheme.secondaryContainer
    Box(Modifier.fillMaxSize()) {
        // What the glass shows: the screen's colour with soft colour blobs, which fade in as it turns.
        Canvas(Modifier.fillMaxSize().layerBackdrop(layer)) {
            drawRect(surface)
            val r = kotlin.math.max(reveal.value, boost.value)
            if (r > 0.001f) {
                val w = size.width
                val h = size.height
                drawCircle(
                    Brush.radialGradient(listOf(a.copy(alpha = 0.95f * r), Color.Transparent), Offset(w * 0.15f, h * 0.12f), w * 0.8f),
                    radius = w * 0.8f, center = Offset(w * 0.15f, h * 0.12f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(b.copy(alpha = 0.9f * r), Color.Transparent), Offset(w * 0.95f, h * 0.45f), w * 0.85f),
                    radius = w * 0.85f, center = Offset(w * 0.95f, h * 0.45f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(c.copy(alpha = 0.9f * r), Color.Transparent), Offset(w * 0.2f, h * 0.85f), w * 0.9f),
                    radius = w * 0.9f, center = Offset(w * 0.2f, h * 0.85f),
                )
            }
        }
        val skinned = glass || reveal.value > 0.001f
        CompositionLocalProvider(
            LocalGlassSkin provides if (skinned) GlassSkin(stack, layer, reveal, celebrate) else null,
            LocalGlassHost provides host,
        ) {
            content()
        }
        GlassSweep(celebrate, haptics)
    }
}

/**
 * The band of light that crosses the screen when glass is put on: a bright diagonal stripe with a
 * thinner, tinted one close behind it (as light does through a lens), and a tap of haptics at the
 * start and as the stripe passes the middle.
 */
@Composable
private fun GlassSweep(trigger: Int, haptics: Boolean) {
    val progress = remember { Animatable(1f) }
    val haptic = LocalHapticFeedback.current
    val tint = MaterialTheme.colorScheme.primary
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        progress.snapTo(0f)
        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        progress.animateTo(1f, tween(1200, easing = SweepEasing))
    }
    LaunchedEffect(trigger) {
        if (trigger == 0 || !haptics) return@LaunchedEffect
        delay(480)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val p = progress.value
    if (p in 0.001f..0.999f) {
        Canvas(Modifier.fillMaxSize()) {
            val reach = size.width + size.height
            fun band(center: Float, half: Float, color: Color, alpha: Float) {
                val c = center * reach
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, color.copy(alpha = alpha), Color.Transparent),
                        start = Offset(c - half, c - half),
                        end = Offset(c + half, c + half),
                    ),
                )
            }
            val fade = (1f - p).coerceIn(0f, 1f)
            band(p * 1.3f - 0.15f, reach * 0.16f, Color.White, 0.55f * fade.coerceAtLeast(0.2f))
            band(p * 1.3f - 0.26f, reach * 0.07f, tint, 0.35f * fade.coerceAtLeast(0.2f))
        }
    }
}

/** The glass version of a grouped settings row. */
@Composable
fun GlassGroupRow(
    skin: GlassSkin,
    shape: CornerBasedShape,
    materialColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // The wobble: each row starts a little after the one above it, squashed, and springs past its
    // size (the low damping is what makes it jelly) before settling.
    val wobble = remember { Animatable(0f) }
    var delayMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(skin.epoch) {
        delay(delayMs)
        wobble.snapTo(0f)
        // Less jelly is also more damped.
        wobble.animateTo(1f, spring(dampingRatio = (0.34f + 0.3f * (1f - GlassTuning.jelly.coerceAtLeast(0f))).coerceIn(0.2f, 0.95f), stiffness = 190f))
    }
    // Glass is drawn only for the rows on (or just next to) the screen: a screen of a few dozen
    // pieces of glass was too much for a phone, and nobody sees the ones that are scrolled away.
    var onScreen by remember { androidx.compose.runtime.mutableStateOf(true) }
    val rootView = androidx.compose.ui.platform.LocalView.current
    Box(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                delayMs = ((it.positionInParent().y / 7f).toLong()).coerceIn(0L, 360L)
                val b = it.boundsInWindow()
                val margin = 300f
                val visibleNow = b.bottom > -margin && b.top < rootView.rootView.height + margin
                if (visibleNow != onScreen) onScreen = visibleNow
            },
    ) {
        // The Material row underneath, fading out as the glass fades in.
        Surface(
            shape = shape,
            color = materialColor,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = (1f - skin.reveal.value * 1.4f).coerceIn(0f, 1f) },
        ) {}
        LiquidGlassPanel(
            glass = skin.stack,
            shape = shape,
            style = LiquidGlassStyle.Row,
            standalone = true,
            visible = onScreen,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = skin.reveal.value.coerceIn(0f, 1f)
                    val w = wobble.value
                    val j = GlassTuning.jelly.coerceAtLeast(0f)
                    scaleX = 1f - 0.06f * j * (1f - w)
                    scaleY = 1f - 0.06f * j * (1f - w)
                },
            // The glass alone stretches (the content keeps its shape), wide and flat first.
            layerBlock = {
                val w = wobble.value
                val j = GlassTuning.jelly.coerceAtLeast(0f)
                scaleX = 1f + 0.06f * j * (1f - w)
                scaleY = 1f - 0.16f * j * (1f - w)
            },
            content = { content() },
        )
    }
}


/**
 * The "Impulse was updated" card opens with a drop of glass: it falls from the top of the screen to
 * the middle, and the drop itself is what grows into the card (the same shape getting bigger and
 * less round, on a spring that overshoots a little), then the content fades in. A tap on the dim
 * area around it calls [onDismiss]. [enabled] is the glass style; without it the card is simply there.
 */
@Composable
fun GlassDropCard(
    enabled: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val fall = remember { Animatable(if (enabled) 0f else 1f) }
    val grow = remember { Animatable(if (enabled) 0f else 1f) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        fall.animateTo(1f, tween(520, easing = androidx.compose.animation.core.CubicBezierEasing(0.5f, 0f, 0.9f, 0.6f)))
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        grow.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 70f))
    }
    val tint = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val density = LocalDensity.current
    val dropSize = with(density) { 30.dp.toPx() }
    val cardCorner = with(density) { 28.dp.toPx() }
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f * fall.value))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        // The card: the drop, grown. It is measured at its final size, so nothing reflows while it grows.
        Box(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { } }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val g = grow.value
                    val w = (dropSize + (placeable.width - dropSize) * g).toInt()
                    val h = (dropSize + (placeable.height - dropSize) * g).toInt()
                    layout(w, h) { placeable.place((w - placeable.width) / 2, (h - placeable.height) / 2) }
                }
                .graphicsLayer {
                    // Nothing of the card is seen while the drop is still falling (the drop is drawn below).
                    alpha = if (grow.value > 0f || fall.value >= 1f) 1f else 0f
                    clip = true
                    val round = kotlin.math.min(size.width, size.height) / 2f
                    shape = RoundedCornerShape(round + (cardCorner - round) * grow.value.coerceIn(0f, 1f))
                }
                .background(androidx.compose.ui.graphics.lerp(tint.copy(alpha = 0.55f), surface, grow.value.coerceIn(0f, 1f))),
        ) {
            Box(Modifier.graphicsLayer { alpha = ((grow.value - 0.4f) / 0.5f).coerceIn(0f, 1f) }) { content() }
        }
        if (fall.value < 1f) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2f
                val centreY = size.height / 2f
                val y = -20.dp.toPx() + (centreY + 20.dp.toPx()) * fall.value
                val r = dropSize / 2f
                val stretch = 1f + 0.5f * (1f - fall.value)
                withTransform({ scale(1f, stretch, Offset(x, y)) }) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.9f), tint.copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(x - r * 0.25f, y - r * 0.3f),
                            radius = r * 1.5f,
                        ),
                        radius = r * 1.4f, center = Offset(x, y),
                    )
                    drawCircle(Color.White.copy(alpha = 0.7f), radius = r * 0.28f, center = Offset(x - r * 0.35f, y - r * 0.4f))
                }
            }
        }
    }
}
