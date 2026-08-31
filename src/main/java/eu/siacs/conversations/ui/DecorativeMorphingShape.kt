package eu.siacs.conversations.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.material3.toPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.delay

/**
 * Purely decorative shape that never stops moving: rotates continuously clockwise and, every
 * [morphIntervalMs], morphs on to the next shape in [shapes] (wrapping back to the start),
 * cycling through the full set forever with no user interaction. Uses the same [Morph]-driven
 * redraw as the Developer Options shape catalog and the chat list's presence-shaped avatar
 * frame — just auto-advancing instead of tap-driven.
 *
 * When [randomStart] is true the cycle's starting point (which shape shows first, and the
 * rotation's initial angle) is randomized once per composition — several instances on the
 * same screen then desync from each other rather than all opening on the same shape at the
 * same angle.
 */
@Composable
fun AutoMorphingShape(
    shapes: List<RoundedPolygon>,
    color: Color,
    modifier: Modifier = Modifier,
    morphIntervalMs: Long = 3200,
    rotationDurationMs: Int = 24000,
    randomStart: Boolean = false,
) {
    if (shapes.isEmpty()) return

    val startIndex = remember(shapes, randomStart) {
        if (randomStart) shapes.indices.random() else 0
    }
    val startAngle = remember(randomStart) {
        if (randomStart) (0 until 360).random().toFloat() else 0f
    }

    val fromShape = remember { mutableStateOf(shapes[startIndex]) }
    val toShape = remember { mutableStateOf(shapes[startIndex]) }
    val morphProgress = remember { Animatable(1f) }

    // Auto-advance loop — no tap input, no queue: just steps to the next shape on a timer and
    // lets the previous morph finish before starting the next (spring duration is well under
    // morphIntervalMs at these defaults, so there's always a settled beat between morphs).
    LaunchedEffect(shapes, startIndex) {
        var index = startIndex
        while (true) {
            delay(morphIntervalMs)
            index = (index + 1) % shapes.size
            fromShape.value = toShape.value
            toShape.value = shapes[index]
            morphProgress.snapTo(0f)
            morphProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
    }

    // Always increases (0 -> 360, restart) — clockwise, never reverses.
    val infiniteTransition = rememberInfiniteTransition(label = "autoMorphingShapeRotation")
    val rotationProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rotationDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "autoMorphingShapeRotationValue",
    )
    val rotationDegrees = (startAngle + rotationProgress) % 360f

    val morph = remember(fromShape.value, toShape.value) { Morph(fromShape.value, toShape.value) }
    val progress = morphProgress.value
    val reusedPath = remember { androidx.compose.ui.graphics.Path() }
    val reusedMatrix = remember { android.graphics.Matrix() }

    Canvas(modifier = modifier) {
        rotate(degrees = rotationDegrees, pivot = Offset(size.width / 2f, size.height / 2f)) {
            val drawScale = 0.78f
            val margin = (1f - drawScale) / 2f
            reusedMatrix.reset()
            reusedMatrix.postScale(size.width * drawScale, size.height * drawScale)
            reusedMatrix.postTranslate(size.width * margin, size.height * margin)
            morph.toPath(progress, reusedPath)
            reusedPath.asAndroidPath().transform(reusedMatrix)
            clipPath(reusedPath) { drawRect(color) }
        }
    }
}

/**
 * A title rendered as glyph-shaped windows onto a live [AutoMorphingShape] scene, instead of a
 * plain text draw call — the letters themselves never get painted; what's visible inside their
 * outlines is whatever the morphing/rotating shape looks like at that instant, like looking
 * through cutout letters at moving shapes behind them.
 *
 * This exists as a workaround for OEM system-wide font-substitution engines (HyperOS/MIUI's
 * being the specific, documented case) that intercept text drawn through the normal
 * Typeface/Text pipeline and silently swap in the system font, discarding this app's embedded
 * variable font and its custom axes. [Paragraph.getPathForRange] computes the glyph outlines
 * once, from this composable's own [style] — nothing about that step is a text *draw* call, so
 * there's nothing for a font-substitution hook watching draw-time text calls to intercept. What
 * gets drawn afterward is pure vector clipping and shape fills, the same primitives the rest of
 * this screen's decoration already uses. Not proven to defeat every OEM's hook — some may
 * intercept earlier in text layout too — but it's the best available approach found, and applied
 * unconditionally (not just on HyperOS) since it should render identically everywhere the
 * substitution problem doesn't exist.
 *
 * Single-line only: measured without a width constraint, so a title long enough to need wrapping
 * on narrow screens will overflow rather than wrap. Fine for the short titles this is built for;
 * worth revisiting if used for longer strings.
 */
@Composable
fun GlyphClippedMorphingTitle(
    text: String,
    style: TextStyle,
    shapes: List<RoundedPolygon>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val layoutResult = remember(text, style, textMeasurer) {
        textMeasurer.measure(text = AnnotatedString(text), style = style)
    }
    val glyphPath = remember(layoutResult) { layoutResult.getPathForRange(0, text.length) }
    val density = LocalDensity.current
    val widthDp = with(density) { layoutResult.size.width.toDp() }
    val heightDp = with(density) { layoutResult.size.height.toDp() }
    // Oversized relative to the glyph box and centered, so the visible pattern reads as one
    // continuous scene behind the whole word rather than a separate tiny shape isolated per
    // letter.
    val patternSize = maxOf(widthDp, heightDp) * 1.6f

    Box(
        modifier = modifier
            .size(widthDp, heightDp)
            .drawWithContent { clipPath(glyphPath) { this@drawWithContent.drawContent() } },
        contentAlignment = Alignment.Center,
    ) {
        AutoMorphingShape(
            shapes = shapes,
            color = color,
            modifier = Modifier.size(patternSize),
        )
    }
}
