package eu.siacs.conversations.ui.glass

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.abs
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
fun rememberUnderGrid(
    background: LayerBackdrop,
    foreground: LayerBackdrop?,
    enabled: Boolean = true,
    // What a transparent part of the background shows: the theme's background, not black.
    base: Color = Color.White,
): State<UnderGrid?> =
    produceState<UnderGrid?>(initialValue = null, background, foreground, enabled, base) {
        while (true) {
            if (enabled) {
                val grid = try {
                    sampleGrid(background, foreground, base.toArgb())
                } catch (_: Exception) {
                    null
                }
                if (grid != null) value = grid
            }
            delay(900)
        }
    }

/**
 * What one glass piece does with its brightness, decided together: the glass gets a light tint when
 * the screen under it is in the middle (dark ink then reads on it), a dark one when it is dimly in the
 * middle (light ink reads), and nothing when it is clearly bright or dark. [dark] is the ink.
 */
class GlassInk internal constructor(
    val dark: Boolean,
    val fill: State<Color>,
    internal val place: MutableState<Rect>,
)

@Composable
fun rememberGlassInk(under: State<UnderGrid?>?): GlassInk {
    val place = remember { mutableStateOf(Rect.Zero) }
    val plan by remember(under) { derivedStateOf { under?.value?.lumaIn(place.value)?.let { planFor(it) } } }
    // Hysteresis on the brightness the ink actually sees (after the tint), so it does not flicker.
    var dark by remember(under) { mutableStateOf(true) }
    LaunchedEffect(plan?.effective) {
        val e = plan?.effective ?: return@LaunchedEffect
        if (dark && e > 0.55f) dark = false
        else if (!dark && e < 0.45f) dark = true
    }
    val fill = remember(under) { derivedStateOf { plan?.let { it.tint.copy(alpha = it.alpha) } ?: Color.Transparent } }
    return GlassInk(dark, fill, place)
}

private class Plan(val tint: Color, val alpha: Float, val effective: Float)

private fun planFor(raw: Float): Plan {
    // Strongest in the middle, none at the ends; at most 30% of the tint.
    val alpha = 0.3f * (1f - abs(raw - 0.5f) * 2f).coerceIn(0f, 1f)
    val light = raw >= 0.5f
    val effective = if (light) raw * (1f - alpha) + alpha else raw * (1f - alpha)
    return Plan(if (light) Color.White else Color.Black, alpha, effective)
}

/** The content of a glass piece with its ink; themed as usual when the screen has no grid. */
@Composable
fun GlassInkBox(
    modifier: Modifier,
    alignment: Alignment,
    ink: GlassInk,
    sampled: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = if (sampled) onGlassColors(MaterialTheme.colorScheme, ink.dark) else MaterialTheme.colorScheme
    MaterialTheme(colorScheme = scheme) {
        Box(modifier, contentAlignment = alignment, content = content)
    }
}

/** The ink for a glass piece's content: dark text over a bright backdrop, white over a dark one. */
fun onGlassColors(scheme: ColorScheme, underDark: Boolean): ColorScheme =
    scheme.copy(
        onSurface = if (underDark) Color.White else Color(0xFF1B1B1F),
        onSurfaceVariant = if (underDark) Color(0xFFE6E0E9) else Color(0xFF45464F),
    )

private suspend fun sampleGrid(background: LayerBackdrop, foreground: LayerBackdrop?, base: Int): UnderGrid {
    val under = smallPixels(background, base)
    // With no layer on top (the viewer's single layer), the bottom one is all there is.
    val over = foreground?.let { smallPixels(it, null) }
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

/** [top] over an opaque [bottom], opaque. */
private fun over(top: Int, bottom: Int): Int {
    val a = ((top ushr 24) and 0xFF) / 255f
    fun ch(shift: Int) = (((top ushr shift) and 0xFF) * a + ((bottom ushr shift) and 0xFF) * (1f - a)).toInt()
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** One channel, top layer over the bottom one, 0..1. */
private fun mix(top: Int, bottom: Int, shift: Int, topAlpha: Float): Float {
    val t = (top ushr shift) and 0xFF
    val b = (bottom ushr shift) and 0xFF
    return (t * topAlpha + b * (1f - topAlpha)) / 255f
}

private suspend fun smallPixels(layer: LayerBackdrop, base: Int?): Small {
    val src = layer.graphicsLayer.toImageBitmap().asAndroidBitmap().let {
        if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it
    }
    val small = Bitmap.createScaledBitmap(src, COLS, ROWS, true)
    val pixels = IntArray(COLS * ROWS).also { small.getPixels(it, 0, COLS, 0, 0, COLS, ROWS) }
    // A transparent part of the background shows the theme's background underneath it.
    if (base != null) for (i in pixels.indices) pixels[i] = over(pixels[i], base)
    return Small(pixels, src.width, src.height)
}
