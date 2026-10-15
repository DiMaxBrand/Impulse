package eu.siacs.conversations.ui.glass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Glass is expensive: blur and refraction run over everything behind it every frame. While a list is
 * scrolling faster than the eye can follow the effect, they are switched off ([lite]) and glass
 * pieces draw as plain tinted bubbles; at a normal pace they are back. A single flag for the whole
 * app, because there is only ever one list scrolling under glass at a time.
 */
object GlassPerformance {
    var lite by mutableStateOf(false)
        internal set

    /** More than this many screens per second counts as fast. */
    const val FAST = 1.0f
    /** Back to full glass only once it has slowed below this (so it does not flicker). */
    const val SLOW = 0.6f
}

/**
 * Measures how fast a list under glass scrolls, in screens per second, and flips
 * [GlassPerformance.lite]. Put the returned modifier on a parent of the list: it sees every scroll
 * delta of the child, drags and flings alike. Sampled four times a second (cheap: it is one addition
 * per delta), so it reacts within a quarter of a second rather than a whole one.
 */
@Composable
fun rememberGlassScrollMeter(): Modifier {
    val distance = remember { floatArrayOf(0f) }
    val viewport = remember { intArrayOf(1) }
    LaunchedEffect(Unit) {
        var last = System.nanoTime()
        while (true) {
            delay(250)
            val now = System.nanoTime()
            val seconds = (now - last) / 1e9f
            last = now
            val screens = distance[0] / viewport[0].coerceAtLeast(1)
            distance[0] = 0f
            val speed = screens / seconds
            if (!GlassPerformance.lite && speed > GlassPerformance.FAST) GlassPerformance.lite = true
            else if (GlassPerformance.lite && speed < GlassPerformance.SLOW) GlassPerformance.lite = false
        }
    }
    DisposableEffect(Unit) { onDispose { GlassPerformance.lite = false } }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                distance[0] += abs(consumed.y)
                return Offset.Zero
            }
        }
    }
    return Modifier.onSizeChanged { viewport[0] = it.height }.nestedScroll(connection)
}
