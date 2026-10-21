package eu.siacs.conversations.ui.glass

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

// The grid the screen is judged on: one cell per 1/24 of its width and 1/12 of its height. Coarse on
// purpose; each glass piece only needs to know whether the part of the screen under it is bright.
private const val COLS = 24
private const val ROWS = 12

/** Brightness (0 black .. 1 white) of each cell of the screen, as the glass sees it. */
class UnderGrid(private val luma: FloatArray, private val widthPx: Int, private val heightPx: Int) {
    /** The average brightness of the cells under [rect] (window pixels), or null if it is empty. */
    fun lumaIn(rect: Rect): Float? {
        if (rect.isEmpty || widthPx <= 0 || heightPx <= 0) return null
        val c0 = (rect.left / widthPx * COLS).toInt().coerceIn(0, COLS - 1)
        val c1 = (rect.right / widthPx * COLS).toInt().coerceIn(c0, COLS - 1)
        val r0 = (rect.top / heightPx * ROWS).toInt().coerceIn(0, ROWS - 1)
        val r1 = (rect.bottom / heightPx * ROWS).toInt().coerceIn(r0, ROWS - 1)
        var sum = 0f
        var n = 0
        for (r in r0..r1) for (c in c0..c1) {
            sum += luma[r * COLS + c]
            n++
        }
        return sum / n
    }
}

/**
 * The brightness grid of what the glass looks through (in draw order: [background] first, then
 * [foreground] on top of it, or nothing above it). Sampled about once a second as a tiny image, the
 * one expensive step; off when [enabled] is false.
 */
@Composable
fun rememberUnderGrid(background: LayerBackdrop, foreground: LayerBackdrop?, enabled: Boolean = true): State<UnderGrid?> =
    produceState<UnderGrid?>(initialValue = null, background, foreground, enabled) {
        while (true) {
            if (enabled) {
                val grid = try {
                    sampleGrid(background, foreground)
                } catch (_: Exception) {
                    null
                }
                if (grid != null) value = grid
            }
            delay(900)
        }
    }

/**
 * The text and icon colours for a glass piece: each piece reads the brightness under itself, not the
 * whole screen's, so the same bar can have a white icon over a dark photo and a dark one over a
 * bright bubble. Hysteresis keeps a piece at the middle of the range from flickering.
 *
 * Without a grid (no under-sampling on this screen) the piece keeps the theme's colours.
 */
@Composable
fun GlassInk(
    modifier: Modifier,
    alignment: Alignment,
    under: State<UnderGrid?>?,
    content: @Composable BoxScope.() -> Unit,
) {
    var place by remember { mutableStateOf(Rect.Zero) }
    val luma by remember(under) { derivedStateOf { under?.value?.lumaIn(place) } }
    var dark by remember { mutableStateOf(true) }
    LaunchedEffect(luma) {
        val l = luma ?: return@LaunchedEffect
        // Dark behind it: light ink. Bright behind it: dark ink. In between, keep the last.
        if (dark && l > 0.58f) dark = false
        else if (!dark && l < 0.42f) dark = true
    }
    val scheme = if (under == null) MaterialTheme.colorScheme else onGlassColors(MaterialTheme.colorScheme, dark)
    MaterialTheme(colorScheme = scheme) {
        Box(
            modifier.onGloballyPositioned { place = it.boundsInWindow() },
            contentAlignment = alignment,
            content = content,
        )
    }
}

/** The ink for a glass piece's content: dark text over a bright backdrop, white over a dark one. */
fun onGlassColors(scheme: ColorScheme, underDark: Boolean): ColorScheme =
    scheme.copy(
        onSurface = if (underDark) Color.White else Color(0xFF1B1B1F),
        onSurfaceVariant = if (underDark) Color(0xFFE6E0E9) else Color(0xFF45464F),
    )

private suspend fun sampleGrid(background: LayerBackdrop, foreground: LayerBackdrop?): UnderGrid {
    val under = smallPixels(background)
    // With no layer on top (the viewer's single layer), the bottom one is all there is.
    val over = foreground?.let { smallPixels(it) }
    val luma = FloatArray(COLS * ROWS)
    for (i in luma.indices) {
        val u = under.pixels[i]
        val o = over?.pixels?.get(i) ?: u
        val alpha = if (over == null) 1f else ((o ushr 24) and 0xFF) / 255f
        val r = mix(o, u, 16, alpha)
        val g = mix(o, u, 8, alpha)
        val b = mix(o, u, 0, alpha)
        luma[i] = 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
    return UnderGrid(luma, under.width, under.height)
}

private class Small(val pixels: IntArray, val width: Int, val height: Int)

/** One channel, top layer over the bottom one, 0..1. */
private fun mix(top: Int, bottom: Int, shift: Int, topAlpha: Float): Float {
    val t = (top ushr shift) and 0xFF
    val b = (bottom ushr shift) and 0xFF
    return (t * topAlpha + b * (1f - topAlpha)) / 255f
}

private suspend fun smallPixels(layer: LayerBackdrop): Small {
    val src = layer.graphicsLayer.toImageBitmap().asAndroidBitmap().let {
        if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it
    }
    val small = Bitmap.createScaledBitmap(src, COLS, ROWS, true)
    val pixels = IntArray(COLS * ROWS).also { small.getPixels(it, 0, COLS, 0, 0, COLS, ROWS) }
    return Small(pixels, src.width, src.height)
}
