package eu.siacs.conversations.ui

import eu.siacs.conversations.R

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.sin

/** A display envelope only: no resampling, filtering or changes to the recorded audio. */
@Composable
fun RecordingTimeline(state: ConversationScreenState, recording: RecordingUiState.Active,
    listener: ConversationScreenListener, modifier: Modifier = Modifier, glass: Boolean = false) {
    val bars = state.recordingBars.value.ifEmpty { List(48) { 0f } }
    val blend = remember { Animatable(1f) }
    var from by remember { mutableStateOf(bars) }
    var to by remember { mutableStateOf(bars) }
    LaunchedEffect(bars) {
        from = List(bars.size) { i ->
            val a = from.getOrElse(i) { 0f }; val b = to.getOrElse(i) { 0f }
            a + (b - a) * blend.value
        }
        to = bars
        blend.snapTo(0f)
        blend.animateTo(1f, tween(if (AppearanceState.fullEffects) 160 else 0, easing = LinearEasing))
    }
    val paused = androidx.compose.animation.core.animateFloatAsState(if (recording.paused) 1f else 0f,
        tween(if (AppearanceState.fullEffects) 240 else 0), label = "recordingPause")
    val discard = remember { Animatable(0f) }
    LaunchedEffect(state.recordingDiscarding.value) {
        if (state.recordingDiscarding.value) discard.animateTo(1f,
            tween(if (AppearanceState.fullEffects) 300 else 0)) else discard.snapTo(0f)
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    var pulse by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(recording.paused, AppearanceState.fullEffects) {
        if (!AppearanceState.fullEffects || recording.paused) { pulse = 1f; return@LaunchedEffect }
        val start = android.os.SystemClock.uptimeMillis()
        while (true) {
            pulse = 0.65f + 0.35f * sin((android.os.SystemClock.uptimeMillis() - start) / 240.0).toFloat()
            delay(40)
        }
    }
    BoxWithConstraints(modifier.height(48.dp).drawWithContent {
        clipRect(left = if (discard.value > 0f) -28.dp.toPx() else 0f) { this@drawWithContent.drawContent() }
    }) {
        val timerWidth = 68.dp
        Canvas(Modifier.fillMaxSize().graphicsLayer {
            // The left clip is the bin's mouth: nothing can emerge on its far side.
            translationX = -(size.width + 28.dp.toPx()) * discard.value
            alpha = 1f - discard.value
        }) {
            val step = size.width / to.size.coerceAtLeast(1)
            val preview = state.recordingPreviewFraction.floatValue
            for (i in to.indices) {
                val value = from.getOrElse(i) { 0f } + (to[i] - from.getOrElse(i) { 0f }) * blend.value
                val height = 2.dp.toPx() + value * (size.height * 0.65f)
                val x = (i + 0.5f) * step
                // Wave continues behind labels; only its contrast fades beneath them.
                val timerDistance = if (glass || !recording.paused) x else size.width - x
                val transition = ((timerDistance - timerWidth.toPx() + 12.dp.toPx()) / 24.dp.toPx()).coerceIn(0f, 1f)
                val fade = 0.16f + 0.49f * transition
                drawRoundRect(if (recording.paused && i.toFloat() / to.size < preview) accent.copy(alpha = fade)
                    else ink.copy(alpha = fade), Offset(x, (size.height - height) / 2),
                    Size((step * 0.56f).coerceAtLeast(1f), height), CornerRadius(2.dp.toPx()))
            }
        }
        val shift = if (glass) 0.dp else (maxWidth - timerWidth).coerceAtLeast(0.dp) * paused.value
        Row(Modifier.offset(x = shift).width(timerWidth).fillMaxHeight().graphicsLayer {
            alpha = 1f - discard.value
        }, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (paused.value < 0.99f) Box(Modifier.size(7.dp).graphicsLayer {
                alpha = (1f - paused.value) * pulse
            }.clip(CircleShape).background(error))
            Text(String.format("%02d:%02d", recording.elapsedMs / 60000, recording.elapsedMs / 1000 % 60),
                style = MaterialTheme.typography.labelMedium, color = ink, maxLines = 1)
        }
        if (recording.paused && !state.recordingDiscarding.value) {
            IconButton(onClick = listener::onPreviewRecording, modifier = Modifier.align(Alignment.Center)
                .size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))) {
                Icon(painterResource(if (state.recordingPreviewPlaying.value) R.drawable.ic_pause_24dp
                    else R.drawable.ic_play_arrow_24dp), stringResource(R.string.recording_preview), tint = accent)
            }
        }
    }
}
