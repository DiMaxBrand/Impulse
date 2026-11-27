package eu.siacs.conversations.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Decorative progress: dragging never changes the onboarding page. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OnboardingProgress(progress: Float, modifier: Modifier = Modifier, animation: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>? = null) {
    val target = progress.coerceIn(0f, 1f)
    val defaultAnimation = remember { Animatable(target) }
    val shown = animation ?: defaultAnimation
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(target) }
    var waveActive by remember { mutableStateOf(false) }
    var hasShownInitialValue by remember { mutableStateOf(false) }

    // Keep one wavy indicator in the composition. Its amplitude is animated to zero for the
    // ordinary linear state, which gives Material's own wave geometry time to grow and settle
    // rather than replacing one indicator with another in a single frame.
    val amplitude by animateDpAsState(
        targetValue = if (waveActive || dragging) 4.dp else 0.dp,
        animationSpec = tween(durationMillis = 260),
        label = "onboarding_progress_wave_amplitude",
    )

    LaunchedEffect(target) {
        // The same Animatable is shared by all pages. A new composable instance therefore still
        // sees an in-flight value from the previous page and must enter the wave before moving.
        val wasAlreadyVisible = hasShownInitialValue ||
            (shown.value > 0f && abs(shown.value - target) > 0.001f)
        hasShownInitialValue = true
        if (!dragging) {
            if (wasAlreadyVisible) waveActive = true
            shown.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 380f))
        }
        if (wasAlreadyVisible && !dragging) {
            // Holding the wave after the travel makes both the start and end feel intentional.
            delay(1_000)
            if (!dragging) waveActive = false
        }
    }
    Box(
        modifier.height(48.dp)
            .pointerInput(target) {
                detectTapGestures {
                    waveActive = true
                    scope.launch {
                        delay(1_000)
                        if (!dragging && !shown.isRunning) waveActive = false
                    }
                }
            }
            .pointerInput(target) {
                detectDragGestures(
                    onDragStart = { at ->
                        dragging = true
                        waveActive = true
                        dragValue = (at.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        scope.launch { shown.snapTo(dragValue) }
                    },
                    onDragEnd = {
                        dragging = false
                        scope.launch {
                            shown.animateTo(target, spring(dampingRatio = 0.7f, stiffness = 380f))
                            delay(1_000)
                            if (!dragging) waveActive = false
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch {
                            shown.animateTo(target, spring(dampingRatio = 0.7f, stiffness = 380f))
                            delay(1_000)
                            if (!dragging) waveActive = false
                        }
                    },
                    onDrag = { change, _ ->
                        dragValue = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        scope.launch { shown.snapTo(dragValue) }
                        change.consume()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        LinearWavyProgressIndicator(
            progress = { shown.value },
            modifier = Modifier.fillMaxWidth(),
            amplitude = { amplitude.value },
        )
    }
}
