package eu.siacs.conversations.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlin.math.ceil

/*
 * Glass computed less often. Blur and refraction are worked out again on every frame for every glass
 * piece, which is what makes a phone lag while a list flies past or the keyboard slides. While that
 * is going on ([GlassPerformance.lite]) each piece instead shows a picture of itself, taken about
 * three times a second, and the pictures melt into one another: the glass is still there, only
 * worked out less often. Together with the heavy blur the list scroll adds (see glassBlur), the
 * lag behind is not something the eye can find.
 *
 * The regular path is capped too, but much more gently. At 60–120 Hz it samples at half the
 * display refresh rate; below 60 Hz it stays live, and above 120 Hz it uses two thirds. The app
 * still composes and presents at the native display rate—the cached picture is only the costly
 * blur/refraction pass.
 */

private class SnapshotState {
    var current by mutableStateOf<ImageBitmap?>(null)
    var previous by mutableStateOf<ImageBitmap?>(null)
    /** While true the piece draws itself live (into the layer a picture is taken of). */
    var capturing by mutableStateOf(false)
    val blend = Animatable(1f)
}

private val SHADOW_MARGIN = 32.dp
private const val CALM_CAPTURE_EVERY_MS = 330L
private const val CALM_LIVE_BEFORE_CAPTURE_MS = 70L
private const val CALM_BLEND_MS = 140

private data class SnapshotCadence(
    val captureEveryMs: Long,
    val liveBeforeCaptureMs: Long,
    val blendMs: Int,
)

private val CALM_CADENCE = SnapshotCadence(
    captureEveryMs = CALM_CAPTURE_EVERY_MS,
    liveBeforeCaptureMs = CALM_LIVE_BEFORE_CAPTURE_MS,
    blendMs = CALM_BLEND_MS,
)

/**
 * A single live frame is enough to record a fresh snapshot. Keeping one device frame between
 * captures leaves the renderer a full frame to present the rest of the UI at 60–120 Hz.
 */
private fun liveGlassCadence(refreshRate: Float): SnapshotCadence? {
    val rate = refreshRate.takeIf { it.isFinite() && it > 0f } ?: return null
    val sampledRate = when {
        rate < 60f -> return null
        rate <= 120f -> rate / 2f
        else -> rate * (2f / 3f)
    }
    // Round up: the cap must never accidentally run *faster* than the selected rate merely
    // because a frame interval cannot be represented by an integral number of milliseconds.
    val every = ceil(1000.0 / sampledRate).toLong().coerceAtLeast(1L)
    val live = ceil(1000.0 / rate).toLong().coerceAtLeast(1L)
    return SnapshotCadence(
        captureEveryMs = every.coerceAtLeast(live + 1L),
        liveBeforeCaptureMs = live,
        blendMs = (every - live).toInt().coerceIn(1, 40),
    )
}

@Composable
fun Modifier.glassSnapshot(geometryKey: Any? = null): Modifier {
    val displayRefreshRate = LocalView.current.display?.refreshRate ?: 60f
    val liveCadence = remember(displayRefreshRate) { liveGlassCadence(displayRefreshRate) }
    val layer = rememberGraphicsLayer()
    val state = remember(geometryKey) { SnapshotState() }
    LaunchedEffect(state, liveCadence) {
        snapshotFlow {
            when {
                GlassPerformance.lite && GlassTuning.lessOften -> CALM_CADENCE
                else -> liveCadence
            }
        }.collectLatest { cadence ->
            if (cadence == null) {
                state.current = null
                state.previous = null
                state.capturing = false
                return@collectLatest
            }
            while (isActive) {
                // A moment live, so the layer holds the piece as it is now, then a picture of it.
                state.capturing = true
                delay(cadence.liveBeforeCaptureMs)
                val picture = try {
                    if (layer.size.width > 0 && layer.size.height > 0) layer.toImageBitmap() else null
                } catch (e: Throwable) {
                    null
                }
                if (picture != null && picture.width > 0 && picture.height > 0) {
                    state.previous = state.current
                    state.current = picture
                    state.blend.snapTo(0f)
                    state.capturing = false
                    state.blend.animateTo(1f, tween(cadence.blendMs))
                } else {
                    state.capturing = false
                }
                delay((cadence.captureEveryMs - cadence.liveBeforeCaptureMs).coerceAtLeast(1L))
            }
        }
    }
    return this.drawWithContent {
        val cadence = if (GlassPerformance.lite && GlassTuning.lessOften) CALM_CADENCE else liveCadence
        if (cadence == null) {
            drawContent()
            return@drawWithContent
        }
        // Taken a little larger than the piece: its shadow and glow reach past the edge, and a
        // picture cut off at the edge would show them as rectangles.
        val margin = SHADOW_MARGIN.toPx()
        val layerSize = IntSize((size.width + 2 * margin).toInt(), (size.height + 2 * margin).toInt())
        val picture = state.current
        if (picture != null && !state.capturing) {
            val before = state.previous
            val blend = state.blend.value
            val offset = IntOffset(-margin.toInt(), -margin.toInt())
            if (before != null && blend < 1f) drawSnapshotPicture(before, offset, layerSize)
            drawSnapshotPicture(picture, offset, layerSize, alpha = if (before != null) blend else 1f)
        } else {
            layer.record(layerSize) {
                translate(margin, margin) { this@drawWithContent.drawContent() }
            }
            translate(-margin, -margin) { drawLayer(layer) }
        }
    }
}

// A name of its own: as "drawImage" a call with three positional arguments was taken for the
// library's (image, srcOffset, srcSize), which showed every picture moved by twice the margin.
private fun DrawScope.drawSnapshotPicture(image: ImageBitmap, offset: IntOffset, dstSize: IntSize, alpha: Float = 1f) {
    drawImage(
        image = image,
        dstOffset = offset,
        dstSize = dstSize,
        alpha = alpha,
    )
}
