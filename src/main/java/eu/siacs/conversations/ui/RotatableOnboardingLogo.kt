package eu.siacs.conversations.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.min

/**
 * A small physical Easter egg: only a touch beginning on the bubble's tail engages the mechanism.
 * Rotation is accumulated, so a full lap does not clamp at 360°, and the spring always returns to
 * the true zero angle when released.
 */
@Composable
internal fun RotatableOnboardingLogo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = remember(context, view) { AngularTickHaptics(context, view) }
    val scope = rememberCoroutineScope()
    var rotation by remember { mutableFloatStateOf(0f) }
    var tailEngaged by remember { mutableStateOf(false) }
    var previousAngle by remember { mutableFloatStateOf(0f) }
    var lastDetent by remember { mutableFloatStateOf(0f) }
    var lastTickAt by remember { mutableStateOf(0L) }
    var lastMoveAt by remember { mutableStateOf(0L) }
    var springBack by remember { mutableStateOf<Job?>(null) }

    Image(
        painter = painterResource(R.drawable.impulse_round_logo),
        contentDescription = null,
        modifier = modifier
            .size(120.dp)
            .graphicsLayer { rotationZ = rotation }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { point ->
                        // The tail occupies the lower-right portion of the current Impulse mark.
                        tailEngaged = point.x > size.width * 0.58f && point.y > size.height * 0.52f
                        if (!tailEngaged) return@detectDragGestures
                        springBack?.cancel()
                        previousAngle = angleFromCenter(point, size.width.toFloat(), size.height.toFloat())
                        lastDetent = floor(rotation / DETENT_DEGREES)
                        lastMoveAt = android.os.SystemClock.uptimeMillis()
                    },
                    onDrag = { change, _ ->
                        if (!tailEngaged) return@detectDragGestures
                        val now = android.os.SystemClock.uptimeMillis()
                        val angle = angleFromCenter(change.position, size.width.toFloat(), size.height.toFloat())
                        val delta = shortestAngleDelta(angle - previousAngle)
                        previousAngle = angle
                        rotation += delta
                        val detent = floor(rotation / DETENT_DEGREES)
                        if (detent != lastDetent && now - lastTickAt >= MIN_TICK_INTERVAL_MS) {
                            val elapsed = (now - lastMoveAt).coerceAtLeast(1L)
                            haptics.tick(min(1f, 0.28f + abs(delta) / elapsed * 8f))
                            lastTickAt = now
                            lastDetent = detent
                        }
                        lastMoveAt = now
                        change.consume()
                    },
                    onDragCancel = { tailEngaged = false; springBack = springToZero(scope, rotation) { rotation = it } },
                    onDragEnd = { tailEngaged = false; springBack = springToZero(scope, rotation) { rotation = it } },
                )
            },
    )
}

private fun angleFromCenter(point: Offset, width: Float, height: Float): Float =
    Math.toDegrees(atan2((point.y - height / 2f).toDouble(), (point.x - width / 2f).toDouble())).toFloat()

private fun shortestAngleDelta(delta: Float): Float = ((delta + 540f) % 360f) - 180f

private fun springToZero(scope: kotlinx.coroutines.CoroutineScope, start: Float, onFrame: (Float) -> Unit): Job =
    scope.launch {
        val animation = Animatable(start)
        animation.animateTo(0f, spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessLow)) {
            onFrame(value)
        }
    }

private class AngularTickHaptics(private val context: Context, private val view: android.view.View) {
    fun tick(strength: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            if (vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK) == true) {
                vibrator.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, strength)
                        .compose(),
                )
                return
            }
        }
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

private const val DETENT_DEGREES = 15f
private const val MIN_TICK_INTERVAL_MS = 35L
