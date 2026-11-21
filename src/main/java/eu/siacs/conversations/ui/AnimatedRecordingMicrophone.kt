package eu.siacs.conversations.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource

/**
 * The app's original rounded microphone for an incoming "recording audio" activity.
 *
 * This deliberately is not a level meter: the recipient cannot hear the source audio. The
 * complete original vector gently pulses by a few dp. Keeping the source vector and its fixed
 * icon bounds avoids platform-dependent hand-drawn geometry and clipping.
 */
@Composable
fun AnimatedRecordingMicrophone(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val motion = rememberInfiniteTransition(label = "recordingMic")
    val pulse by motion.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(780, easing = LinearEasing), RepeatMode.Reverse),
        label = "recordingMicPulse",
    )
    Icon(
        painter = painterResource(eu.siacs.conversations.R.drawable.ic_mic_24dp),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size).graphicsLayer {
            scaleX = pulse
            scaleY = pulse
        },
    )
}
