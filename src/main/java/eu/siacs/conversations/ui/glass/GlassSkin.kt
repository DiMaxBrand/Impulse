package eu.siacs.conversations.ui.glass

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
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
    val surface = MaterialTheme.colorScheme.surface
    val a = MaterialTheme.colorScheme.primaryContainer
    val b = MaterialTheme.colorScheme.tertiaryContainer
    val c = MaterialTheme.colorScheme.secondaryContainer
    Box(Modifier.fillMaxSize()) {
        // What the glass shows: the screen's colour with soft colour blobs, which fade in as it turns.
        Canvas(Modifier.fillMaxSize().layerBackdrop(layer)) {
            drawRect(surface)
            val r = reveal.value
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
        wobble.animateTo(1f, spring(dampingRatio = 0.34f, stiffness = 190f))
    }
    Box(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { delayMs = ((it.positionInParent().y / 7f).toLong()).coerceIn(0L, 360L) },
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
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = skin.reveal.value.coerceIn(0f, 1f)
                    val w = wobble.value
                    scaleX = 0.94f + 0.06f * w
                    scaleY = 0.94f + 0.06f * w
                },
            // The glass alone stretches (the content keeps its shape), wide and flat first.
            layerBlock = {
                val w = wobble.value
                scaleX = 1f + 0.06f * (1f - w)
                scaleY = 1f - 0.16f * (1f - w)
            },
            content = { content() },
        )
    }
}

