package eu.siacs.conversations.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
    var waveRequest by remember { mutableIntStateOf(0) }
    var touched by remember { mutableStateOf(false) }

    LaunchedEffect(target) {
        if (!dragging) shown.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 380f))
    }
    LaunchedEffect(waveRequest) {
        if (waveRequest > 0) {
            touched = true
            delay(3_000)
            touched = false
        }
    }
    Box(
        modifier.height(48.dp)
            .pointerInput(target) {
                detectTapGestures { waveRequest++ }
            }
            .pointerInput(target) {
                detectDragGestures(
                    onDragStart = { at ->
                        dragging = true
                        dragValue = (at.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        scope.launch { shown.snapTo(dragValue) }
                    },
                    onDragEnd = {
                        dragging = false
                        waveRequest++
                        scope.launch { shown.animateTo(target, spring(dampingRatio = 0.7f, stiffness = 380f)) }
                    },
                    onDragCancel = {
                        dragging = false
                        waveRequest++
                        scope.launch { shown.animateTo(target, spring(dampingRatio = 0.7f, stiffness = 380f)) }
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
        if (dragging || touched || shown.isRunning) LinearWavyProgressIndicator(progress = { shown.value }, modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(progress = { shown.value }, modifier = Modifier.fillMaxWidth())
    }
}
