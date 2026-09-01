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

// A GlyphFilledTitle composable (rendering a title's glyph outlines directly via
// Paragraph.getPathForRange(), as a HyperOS/MIUI font-substitution workaround) was tried and
// removed here -- it shipped a solid, illegible black bar on-device instead of visible text.
// Suspected cause: textMeasurer.measure() was called eagerly inside remember(), likely before
// the async-loaded custom font resolved, baking in "tofu"/missing-glyph boxes that a normal
// Text() composable (which recomposes automatically when its FontFamily finishes loading)
// would never show. Not re-attempted without a way to verify on-device first -- see
// NotificationSetupScreen.kt's title comment and CLAUDE.md/TODO.md for the open problem.
