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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.material3.toPath
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
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
            // 0.78 (11% margin per side) was tuned for the tap-driven shape catalog, where every
            // morph pair got hand-tested. This composable auto-morphs between *any* random pair
            // from the full 35-shape catalog (randomStart) with no chance to catch an overshoot
            // by eye first -- silhouettes as different as "Burst" -> "Circle" can bulge well past
            // an 11% margin mid-transition, visibly clipping a point off. Much wider margin here
            // to make that safe regardless of which pair lands.
            val drawScale = 0.58f
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
 * A title rendered by filling its glyph outlines directly, instead of a plain text draw call —
 * same solid color as normal text, just reached by a different path. Workaround for OEM
 * system-wide font-substitution engines (HyperOS/MIUI's being the documented case) that
 * intercept text drawn through the normal Typeface/Text pipeline and silently swap in the
 * system font, discarding this app's embedded variable font and its custom axes. Nothing about
 * filling a plain [Path] is a text *draw* call, so there's nothing for a substitution hook
 * watching draw-time text calls to intercept.
 *
 * A first version of this measured the text once via `remember { textMeasurer.measure(...) }`
 * and shipped a solid black bar instead of visible text on-device — `remember`'s calculation
 * block is not a snapshot-observed read, so if the custom font (loaded asynchronously from
 * resources) hadn't resolved yet at that exact composition, the baked-in "tofu"/missing-glyph
 * boxes never got a chance to redraw once loading finished. `Text()` never has this problem
 * because Compose's own text layout node reads font-resolution state directly during the
 * draw/layout phase, where snapshot observation *does* trigger a redraw on change. This version
 * measures inside the [Canvas]'s draw scope itself instead of via `remember` — the same draw
 * scope this file's [AutoMorphingShape] already reads animated [Animatable]/`State` values from
 * successfully — so a font resolving after first frame should trigger the same kind of
 * automatic redraw.
 *
 * Deliberately just a flat fill, not a moving pattern behind the letters: anything animated
 * (e.g. a morphing/rotating shape) risks a frame where part of a letter goes unlit mid-morph,
 * which is a real legibility problem for a title, not just a cosmetic one.
 *
 * Unlike the first version, this does not auto-size to the measured text — [modifier] must
 * supply an explicit size (the caller doesn't know the text's measured bounds ahead of the
 * font resolving either). Single-line only: measured without a width constraint, so a title
 * long enough to need wrapping on narrow screens will overflow rather than wrap.
 */
@Composable
fun GlyphFilledTitle(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val annotatedText = remember(text) { AnnotatedString(text) }

    Canvas(modifier = modifier) {
        // Deliberately not memoized via remember{} -- see the doc comment above. Measuring here,
        // inside the draw scope, is what makes this redraw automatically once an
        // asynchronously-loading custom font actually resolves.
        val layoutResult = textMeasurer.measure(text = annotatedText, style = style)
        val glyphPath = layoutResult.getPathForRange(0, text.length)
        drawPath(glyphPath, color = color)
    }
}
