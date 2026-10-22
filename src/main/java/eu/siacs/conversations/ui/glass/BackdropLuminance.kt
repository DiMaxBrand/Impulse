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

class GlassInk internal constructor(
    /** White text when true, black when false. */
    val lightInk: Boolean,
    /** What is behind the piece, 0 black .. 1 white, animated over a second. */
    internal val level: Animatable<Float, AnimationVector1D>,
    /** Where the piece records what is behind it. */
    internal val layer: GraphicsLayer,
    internal val measured: Float?,
    internal val adaptive: Boolean,
)

@Composable
fun rememberGlassInk(adaptive: Boolean): GlassInk {
    val layer = rememberGraphicsLayer()
    val level = remember { Animatable(0.5f) }
    var lightInk by remember { mutableStateOf(true) }
    var measured by remember { mutableStateOf<Float?>(null) }
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
            val l = try {
                sampleLuma(layer, baseLuma)
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
    return GlassInk(lightInk, level, layer, measured, adaptive)
}

private suspend fun sampleLuma(layer: GraphicsLayer, baseLuma: Float): Float? {
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
    MaterialTheme(colorScheme = scheme) {
        Box(modifier, contentAlignment = alignment) {
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

/** The ink for a glass piece's content: dark text over a bright backdrop, white over a dark one. */
fun onGlassColors(scheme: ColorScheme, underDark: Boolean): ColorScheme =
    scheme.copy(
        onSurface = if (underDark) Color.White else Color(0xFF1B1B1F),
        onSurfaceVariant = if (underDark) Color(0xFFE6E0E9) else Color(0xFF45464F),
    )
