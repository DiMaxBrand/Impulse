package eu.siacs.conversations.ui.glass

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.ColorScheme
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

// The sample is tiny: 16 by 4 pixels of the strip under the bar is plenty to tell bright from dark.
private const val COLS = 16
private const val ROWS = 4

/**
 * Whether what is under the top [height] of the screen is dark, read from the layers the glass looks
 * through: [background] first, then [foreground] (the messages) on top of it. Sampled about once a
 * second and only as a tiny image, with hysteresis so it does not flicker around the middle.
 */
@Composable
fun rememberUnderIsDark(
    background: LayerBackdrop,
    foreground: LayerBackdrop?,
    height: Dp,
    enabled: Boolean = true,
): State<Boolean> {
    val cropPx = with(LocalDensity.current) { height.roundToPx() }
    return produceState(initialValue = true, background, foreground, cropPx, enabled) {
        while (true) {
            delay(900)
            if (!enabled) continue
            val luma = try {
                sampleLuma(background, foreground, cropPx)
            } catch (_: Exception) {
                null
            }
            if (luma == null) continue
            // Dark behind it: light text. Bright behind it: dark text. Between the two, keep the last.
            if (value && luma > 0.58f) value = false
            else if (!value && luma < 0.42f) value = true
        }
    }
}

/**
 * The bar's text and icon colours for what is behind it: white over a dark backdrop, a near-black
 * over a bright one. Only the content colours change; the glass itself stays clear.
 */
fun onGlassColors(scheme: ColorScheme, underDark: Boolean): ColorScheme =
    scheme.copy(
        onSurface = if (underDark) Color.White else Color(0xFF1B1B1F),
        onSurfaceVariant = if (underDark) Color(0xFFE6E0E9) else Color(0xFF45464F),
    )

private suspend fun sampleLuma(background: LayerBackdrop, foreground: LayerBackdrop?, cropPx: Int): Float {
    val under = smallPixels(background, cropPx)
    // With no layer on top (the viewer's single layer), the bottom one is all there is.
    val over = foreground?.let { smallPixels(it, cropPx) } ?: return lumaOf(under)
    val merged = IntArray(under.size) { i ->
        val u = under[i]
        val o = over[i]
        val alpha = ((o ushr 24) and 0xFF) / 255f
        fun channel(shift: Int) = (mix(o, u, shift, alpha) * 255f).toInt()
        (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
    return lumaOf(merged)
}

private fun lumaOf(pixels: IntArray): Float {
    var sum = 0f
    for (c in pixels) {
        val r = ((c shr 16) and 0xFF) / 255f
        val g = ((c shr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        sum += 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
    return sum / pixels.size
}

/** One channel, top layer over the bottom one, 0..1. */
private fun mix(top: Int, bottom: Int, shift: Int, topAlpha: Float): Float {
    val t = (top ushr shift) and 0xFF
    val b = (bottom ushr shift) and 0xFF
    return (t * topAlpha + b * (1f - topAlpha)) / 255f
}

private suspend fun smallPixels(layer: LayerBackdrop, cropPx: Int): IntArray {
    // The layer is a whole screen, so this is the one expensive part: once a second, not per frame.
    val src = layer.graphicsLayer.toImageBitmap().asAndroidBitmap().let {
        if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it
    }
    val crop = Bitmap.createBitmap(src, 0, 0, src.width, minOf(cropPx, src.height).coerceAtLeast(1))
    val small = Bitmap.createScaledBitmap(crop, COLS, ROWS, true)
    return IntArray(COLS * ROWS).also { small.getPixels(it, 0, COLS, 0, 0, COLS, ROWS) }
}
