package eu.siacs.conversations.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A Material Symbols Rounded-compatible microphone for an incoming "recording audio" activity.
 *
 * This deliberately is not a level meter: the recipient cannot hear the source audio. Its
 * capsule and two sides of the U-shaped support take a tiny, deterministic turn while the foot
 * remains still. At rest it has the same proportions, rounded caps, and 2 dp visual weight as
 * the app's regular Rounded microphone.
 */
@Composable
fun AnimatedRecordingMicrophone(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val motion = rememberInfiniteTransition(label = "recordingMic")
    // The capsule leads; each arm follows at a slightly different time. This makes the symbol
    // feel composed from parts, not like a single icon being scaled or bounced.
    val capsulePhase by motion.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(760, easing = LinearEasing), RepeatMode.Reverse),
        label = "recordingMicCapsule",
    )
    val leftArmPhase by motion.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(760, delayMillis = 105, easing = LinearEasing), RepeatMode.Reverse),
        label = "recordingMicLeftArm",
    )
    val rightArmPhase by motion.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(760, delayMillis = 210, easing = LinearEasing), RepeatMode.Reverse),
        label = "recordingMicRightArm",
    )

    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val xInset = (this.size.width - 24f * unit) / 2f
        val yInset = (this.size.height - 24f * unit) / 2f
        withTransform({ translate(xInset, yInset); scale(unit, unit) }) {
            // Material Symbols Rounded mic: the centre capsule. Its movement is deliberately
            // below one visual pixel at ordinary status-row sizes.
            val lift = capsulePhase * 0.42f
            val capsuleHeight = 12f + capsulePhase * 0.22f
            drawRoundRect(
                color = tint,
                topLeft = Offset(9f, 2f + lift),
                size = Size(6f, capsuleHeight),
                cornerRadius = CornerRadius(3f, 3f),
            )

            // The original rounded symbol is one filled U-shaped support. Keeping its left and
            // right strokes separate lets them open by a fraction of a dp in sequence without
            // changing the stable vertical foot or implying an audio level.
            val left = 6f - leftArmPhase * 0.28f
            val right = 18f + rightArmPhase * 0.28f
            val supportStroke = Stroke(width = 2.15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(
                path = Path().apply {
                    moveTo(left, 11.25f)
                    cubicTo(left, 15.35f, 8.65f, 17.75f, 12f, 17.75f)
                },
                color = tint,
                style = supportStroke,
            )
            drawPath(
                path = Path().apply {
                    moveTo(right, 11.25f)
                    cubicTo(right, 15.35f, 15.35f, 17.75f, 12f, 17.75f)
                },
                color = tint,
                style = supportStroke,
            )

            // Stable stem and foot anchor the animation to the original symbol's silhouette.
            drawLine(tint, Offset(12f, 17.85f), Offset(12f, 21f), strokeWidth = 2.15f, cap = StrokeCap.Round)
            drawLine(tint, Offset(8.8f, 21f), Offset(15.2f, 21f), strokeWidth = 2.15f, cap = StrokeCap.Round)
        }
    }
}
