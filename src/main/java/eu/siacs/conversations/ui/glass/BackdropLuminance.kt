package eu.siacs.conversations.ui.glass

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sign

/*
 * Adaptive luminance glass, done the way the Backdrop demo does it (AdaptiveLuminanceGlassContent):
 * each glass piece records what is behind it into its own [GlassInk.layer] while it draws (see the
 * onDrawBackdrop of LiquidGlassButton / LiquidGlassPanel), and about once a second that layer is
 * shrunk to 5 x 5 pixels and averaged. That one number drives both the glass (brighter or darker)
 * and the text on it (black or white), so the two always agree.
 */

/** DEBUG: the last sampling failure; the readout only shows while something is wrong. */
var lastSampleError by mutableStateOf<String?>(null)

/**
 * Whether the piece should record what is behind it on its next draw. Recording every piece on every
 * frame was a lot of GPU work for a value that is read about three times a second, so a piece only
 * records when its sampler has asked. (Not snapshot state: it is read while drawing.)
 */
internal class RecordRequest {
    @Volatile var due = true
    fun take(): Boolean { val d = due; due = false; return d }
}

class GlassInk internal constructor(
    /** White text when true, black when false. */
    val lightInk: Boolean,
    /** What is behind the piece, 0 black .. 1 white, animated over a second. */
    internal val level: Animatable<Float, AnimationVector1D>,
    /** Where the piece records what is behind it. */
    internal val layer: GraphicsLayer,
    internal val measured: Float?,
    internal val adaptive: Boolean,
    internal val request: RecordRequest,
    /** What is behind the piece as a grid of brightness cells (see [InkRegion]); null until measured. */
    internal val grid: LumaGrid? = null,
)

/** Brightness (0 black .. 1 white) of the backdrop in [cols] x [rows] cells over a piece, row by row. */
internal class LumaGrid(val cols: Int, val rows: Int, val data: FloatArray) {
    /** The mean over the part of the piece given as fractions (0..1) of its width and height. */
    fun mean(l: Float, t: Float, r: Float, b: Float): Float {
        val c0 = (l.coerceIn(0f, 1f) * cols).toInt().coerceIn(0, cols - 1)
        val c1 = (kotlin.math.ceil(r.coerceIn(0f, 1f) * cols).toInt() - 1).coerceIn(c0, cols - 1)
        val r0 = (t.coerceIn(0f, 1f) * rows).toInt().coerceIn(0, rows - 1)
        val r1 = (kotlin.math.ceil(b.coerceIn(0f, 1f) * rows).toInt() - 1).coerceIn(r0, rows - 1)
        var sum = 0f
        var n = 0
        for (y in r0..r1) for (x in c0..c1) { sum += data[y * cols + x]; n++ }
        return if (n > 0) sum / n else 0.5f
    }
}

private const val GRID_COLS = 24
private const val GRID_ROWS = 8

@Composable
fun rememberGlassInk(adaptive: Boolean): GlassInk {
    val layer = rememberGraphicsLayer()
    val request = remember { RecordRequest() }
    val level = remember { Animatable(0.5f) }
    var lightInk by remember { mutableStateOf(true) }
    var measured by remember { mutableStateOf<Float?>(null) }
    var grid by remember { mutableStateOf<LumaGrid?>(null) }
    // A transparent part of what is behind counts as the theme's background, not as black.
    val baseLuma = MaterialTheme.colorScheme.background.luminance()
    LaunchedEffect(layer, adaptive, baseLuma) {
        if (!adaptive) return@LaunchedEffect
        while (isActive) {
            // While the list flies past nobody reads the text; skip the readback and catch up after.
            if (GlassPerformance.lite) {
                delay(300)
                continue
            }
            // Ask for a fresh recording and give a frame time to draw it.
            request.due = true
            delay(60)
            val l = try {
                sampleLuma(layer, baseLuma) { grid = it }
            } catch (e: Exception) {
                lastSampleError = e.javaClass.simpleName + ": " + e.message
                null
            }
            if (l == null) {
                // Not drawn yet: try again soon.
                delay(250)
                continue
            }
            measured = l
            // Hysteresis around the middle, so the text does not flicker on a mid-grey backdrop.
            if (lightInk && l > 0.54f) lightInk = false
            else if (!lightInk && l < 0.46f) lightInk = true
            // The glass follows quickly (the demo's one second is for a still backdrop; here the list
            // scrolls under the piece); this also paces the loop, about three samples a second.
            level.animateTo(l, tween(durationMillis = 300))
        }
    }
    return GlassInk(lightInk, level, layer, measured, adaptive, request, grid)
}

private suspend fun sampleLuma(layer: GraphicsLayer, baseLuma: Float, onGrid: (LumaGrid) -> Unit): Float? {
    if (layer.size.width <= 0 || layer.size.height <= 0) return null
    val src = layer.toImageBitmap().asAndroidBitmap().let {
        if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it
    }
    if (src.width <= 0 || src.height <= 0) return null
    val small = Bitmap.createScaledBitmap(src, 5, 5, true)
    val pixels = IntArray(25).also { small.getPixels(it, 0, 5, 0, 0, 5, 5) }
    var sum = 0f
    for (c in pixels) {
        val a = ((c ushr 24) and 0xFF) / 255f
        val r = ((c shr 16) and 0xFF) / 255f
        val g = ((c shr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
        sum += l * a + baseLuma * (1f - a)
    }
    // The same picture as a finer grid, for the text that sits on only a part of the piece.
    val cells = Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(src, GRID_COLS * 3, GRID_ROWS * 3, true), GRID_COLS, GRID_ROWS, true)
    val raw = IntArray(GRID_COLS * GRID_ROWS).also { cells.getPixels(it, 0, GRID_COLS, 0, 0, GRID_COLS, GRID_ROWS) }
    onGrid(LumaGrid(GRID_COLS, GRID_ROWS, FloatArray(raw.size) { i ->
        val c = raw[i]
        val a = ((c ushr 24) and 0xFF) / 255f
        val l = 0.2126f * ((c shr 16) and 0xFF) / 255f + 0.7152f * ((c shr 8) and 0xFF) / 255f + 0.0722f * (c and 0xFF) / 255f
        l * a + baseLuma * (1f - a)
    }))
    return sum / pixels.size
}

/** Brightness of the glass for a backdrop of this luminance (no lift on the bright side: milky). */
internal fun glassBrightness(level: Float): Float {
    val x = level * 2f - 1f
    val l = sign(x) * x * x
    return if (l > 0f) 0.1f else 0.1f + (-0.2f - 0.1f) * (-l)
}

/** Contrast of the glass: kept whole (lowering it greyed the glass). */
@Suppress("UNUSED_PARAMETER")
internal fun glassContrast(level: Float): Float = 1f

/** The content of a glass piece with its ink; themed as usual on a screen that does not adapt. */
@Composable
fun GlassInkBox(
    modifier: Modifier,
    alignment: Alignment,
    ink: GlassInk,
    content: @Composable BoxScope.() -> Unit,
) {
    // The ink fades instead of jumping, but quickly: black to white passes through grey, which reads
    // worst, so the fade is short enough that the grey is only a flash.
    val target = onGlassColors(MaterialTheme.colorScheme, ink.lightInk)
    val onSurface by animateColorAsState(target.onSurface, tween(durationMillis = 250), label = "inkOnSurface")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, tween(durationMillis = 250), label = "inkOnSurfaceVariant")
    val scheme = if (ink.adaptive) {
        MaterialTheme.colorScheme.copy(onSurface = onSurface, onSurfaceVariant = onSurfaceVariant)
    } else {
        MaterialTheme.colorScheme
    }
    val field = remember { InkField() }
    field.grid = ink.grid
    field.lightInk = ink.lightInk
    field.adaptive = ink.adaptive
    MaterialTheme(colorScheme = scheme) {
        androidx.compose.runtime.CompositionLocalProvider(LocalInkField provides field) {
        Box(modifier.onGloballyPositioned { field.coords = it }, contentAlignment = alignment) {
            content()
            // DEBUG: only while no brightness has been measured; disappears once it works.
            if (ink.adaptive && ink.measured == null) {
                Text(
                    text = "--" + (lastSampleError?.let { " E:" + it.take(60) } ?: ""),
                    fontSize = 7.sp,
                    color = Color.Red,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
        }
    }
}

/** What a [GlassInkBox] knows about the backdrop behind it, for the [InkRegion]s inside. */
internal class InkField {
    var grid by mutableStateOf<LumaGrid?>(null)
    var lightInk by mutableStateOf(true)
    var adaptive by mutableStateOf(false)
    var coords: androidx.compose.ui.layout.LayoutCoordinates? = null
}

internal val LocalInkField = androidx.compose.runtime.compositionLocalOf<InkField?> { null }

/**
 * Text (or an icon) that looks at what is behind IT, not behind the whole glass piece: it takes its
 * ink from the brightness of just the cells of the piece it covers. A name over a dark message can
 * be white while the line under it, over a light part, stays dark. Outside an adaptive piece it
 * changes nothing.
 */
@Composable
fun InkRegion(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val field = LocalInkField.current
    if (field == null || !field.adaptive) {
        Box(modifier) { content() }
        return
    }
    var fractions by remember { mutableStateOf<FloatArray?>(null) }
    var light by remember { mutableStateOf(field.lightInk) }
    val g = field.grid
    val f = fractions
    if (g != null && f != null) {
        val l = g.mean(f[0], f[1], f[2], f[3])
        // The same hysteresis as the piece itself.
        if (light && l > 0.54f) light = false else if (!light && l < 0.46f) light = true
    } else if (light != field.lightInk && g == null) {
        light = field.lightInk
    }
    val target = onGlassColors(MaterialTheme.colorScheme, light)
    val onSurface by animateColorAsState(target.onSurface, tween(durationMillis = 250), label = "regionOnSurface")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, tween(durationMillis = 250), label = "regionOnSurfaceVariant")
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(onSurface = onSurface, onSurfaceVariant = onSurfaceVariant)) {
        Box(
            modifier.onGloballyPositioned { c ->
                val root = field.coords
                if (root != null && root.isAttached && c.isAttached) {
                    val b = root.localBoundingBoxOf(c)
                    val w = root.size.width.toFloat().coerceAtLeast(1f)
                    val h = root.size.height.toFloat().coerceAtLeast(1f)
                    val n = floatArrayOf(b.left / w, b.top / h, b.right / w, b.bottom / h)
                    val o = fractions
                    if (o == null || (0..3).any { kotlin.math.abs(o[it] - n[it]) > 0.01f }) fractions = n
                }
            },
        ) { content() }
    }
}

/** The ink for a glass piece's content: dark text over a bright backdrop, white over a dark one. */
fun onGlassColors(scheme: ColorScheme, underDark: Boolean): ColorScheme =
    scheme.copy(
        onSurface = if (underDark) Color.White else Color(0xFF1B1B1F),
        onSurfaceVariant = if (underDark) Color(0xFFE6E0E9) else Color(0xFF45464F),
    )
