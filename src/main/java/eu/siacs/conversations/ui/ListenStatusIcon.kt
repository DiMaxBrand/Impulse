package eu.siacs.conversations.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import eu.siacs.conversations.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Same slow-fast-slow feel as MessageStatusIcon's StandardEasing — kept as a private duplicate
 * rather than shared so this file has no compile-time dependency on that one's internals. */
private val StandardEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/**
 * The headphone badge that replaces the plain "Listening"/"Unknown" text labels for a voice
 * message's listen status (see ListenStatusManager). Only ever mounted by the caller for
 * [ListenStatusManager.State.LISTENING], [ListenStatusManager.State.LISTENED], or
 * [ListenStatusManager.State.UNKNOWN] — NOT_LISTENED shows no icon at all (falls back to the
 * plain checkmark) and PAUSED keeps its existing text label, so neither ever reaches here.
 *
 * - LISTENING: continuously pulses so it reads as "live" rather than a static glyph.
 * - LISTENED: one-time bounce (spatial spring, matching the checkmark's own delivered->read
 *   kick) while the tint eases from gray to green (effect spring — color has no business
 *   overshooting).
 * - UNKNOWN: one-time scale-in-then-settle bounce (bouncier than LISTENED's — the extrapolation
 *   genuinely lost track, so this should feel more like an alert) while the tint eases to red,
 *   same effect-spring language as LISTENED.
 */
@Composable
fun ListenStatusIcon(
    state: ListenStatusManager.State,
    grayColor: Color,
    listenedColor: Color,
    unknownColor: Color,
    modifier: Modifier = Modifier,
) {
    var currentState by remember { mutableStateOf(state) }
    val scale = remember { Animatable(1f) }
    // 0 = grayColor, 1 = this state's own target tint (listenedColor or unknownColor).
    val colorProgress = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "listen-status-pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "listen-status-pulse-scale",
    )

    // Same long-lived-effect shape as MessageStatusIcon: always finishes whatever leg is
    // currently in flight before reading the latest target, so a state change mid-bounce gets
    // picked up as the next leg instead of yanking the animatables mid-flight.
    val latestState = rememberUpdatedState(state)
    LaunchedEffect(Unit) {
        while (true) {
            val to = latestState.value
            val from = currentState
            if (from == to) {
                snapshotFlow { latestState.value }.first { it != currentState }
                continue
            }
            when (to) {
                ListenStatusManager.State.LISTENED -> {
                    coroutineScope {
                        launch {
                            scale.animateTo(1.32f, tween(160, easing = StandardEasing))
                            scale.animateTo(
                                1f,
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow,
                                ),
                            )
                        }
                        launch {
                            colorProgress.snapTo(0f)
                            colorProgress.animateTo(1f, spring(stiffness = 1600f, dampingRatio = 1.0f))
                        }
                    }
                }
                ListenStatusManager.State.UNKNOWN -> {
                    coroutineScope {
                        launch {
                            scale.snapTo(0f)
                            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
                        }
                        launch {
                            colorProgress.snapTo(0f)
                            colorProgress.animateTo(1f, spring(stiffness = 1600f, dampingRatio = 1.0f))
                        }
                    }
                }
                // Re-entering LISTENING (a genuine resume after LISTENED/UNKNOWN, e.g. the peer
                // re-opened the message) — no bounce spec for this leg, just settle back to the
                // resting look and let the continuous pulse above take over.
                ListenStatusManager.State.LISTENING -> {
                    scale.snapTo(1f)
                    colorProgress.snapTo(0f)
                }
                else -> {}
            }
            currentState = to
        }
    }

    val tint = when (currentState) {
        ListenStatusManager.State.LISTENED -> lerp(grayColor, listenedColor, colorProgress.value)
        ListenStatusManager.State.UNKNOWN -> lerp(grayColor, unknownColor, colorProgress.value)
        else -> grayColor
    }
    val appliedScale = if (currentState == ListenStatusManager.State.LISTENING) pulseScale else scale.value
    val contentDescription = when (currentState) {
        ListenStatusManager.State.LISTENING -> stringResource(R.string.listen_status_listening)
        ListenStatusManager.State.LISTENED -> stringResource(R.string.listen_status_listened)
        ListenStatusManager.State.UNKNOWN -> stringResource(R.string.listen_status_unknown)
        else -> null
    }

    Icon(
        painter = painterResource(R.drawable.ic_headphones_24dp),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.graphicsLayer(scaleX = appliedScale, scaleY = appliedScale),
    )
}
