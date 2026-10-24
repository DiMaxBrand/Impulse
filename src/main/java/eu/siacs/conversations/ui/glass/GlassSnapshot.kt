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
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

/*
 * Glass computed less often. Blur and refraction are worked out again on every frame for every glass
 * piece, which is what makes a phone lag while a list flies past or the keyboard slides. While that
 * is going on ([GlassPerformance.lite]) each piece instead shows a picture of itself, taken about
 * three times a second, and the pictures melt into one another: the glass is still there, only
 * worked out less often. Together with the heavy blur the list scroll adds (see glassBlur), the
 * lag behind is not something the eye can find. Outside those moments nothing changes: the piece
 * draws itself as always, with no extra layer.
 */

private class SnapshotState {
    var current by mutableStateOf<ImageBitmap?>(null)
    var previous by mutableStateOf<ImageBitmap?>(null)
    /** While true the piece draws itself live (into the layer a picture is taken of). */
    var capturing by mutableStateOf(false)
    val blend = Animatable(1f)
}

private const val CAPTURE_EVERY_MS = 330L
private const val LIVE_BEFORE_CAPTURE_MS = 70L
private const val BLEND_MS = 140

@Composable
fun Modifier.glassSnapshot(): Modifier {
    val layer = rememberGraphicsLayer()
    val state = remember { SnapshotState() }
    LaunchedEffect(Unit) {
        snapshotFlow { GlassPerformance.lite }.collectLatest { slow ->
            if (!slow) {
                state.current = null
                state.previous = null
                state.capturing = false
                return@collectLatest
            }
            while (isActive) {
                // A moment live, so the layer holds the piece as it is now, then a picture of it.
                state.capturing = true
                delay(LIVE_BEFORE_CAPTURE_MS)
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
                    state.blend.animateTo(1f, tween(BLEND_MS))
                } else {
                    state.capturing = false
                }
                delay(CAPTURE_EVERY_MS - LIVE_BEFORE_CAPTURE_MS)
            }
        }
    }
    return this.drawWithContent {
        if (!GlassPerformance.lite) {
            drawContent()
            return@drawWithContent
        }
        val picture = state.current
        if (picture != null && !state.capturing) {
            val dst = IntSize(size.width.toInt(), size.height.toInt())
            val before = state.previous
            val blend = state.blend.value
            if (before != null && blend < 1f) drawImage(before, dstSize = dst)
            drawImage(picture, dstSize = dst, alpha = if (before != null) blend else 1f)
        } else {
            layer.record(IntSize(size.width.toInt(), size.height.toInt())) { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
    }
}

private fun DrawScope.drawImage(image: ImageBitmap, dstSize: IntSize, alpha: Float = 1f) {
    drawImage(
        image = image,
        dstSize = dstSize,
        alpha = alpha,
    )
}
