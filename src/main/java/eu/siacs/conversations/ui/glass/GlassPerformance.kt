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
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.ime
import kotlin.math.abs

/**
 * Glass is expensive: blur and refraction run over everything behind it every frame. While a list is
 * scrolling faster than the eye can follow the effect, they are switched off ([lite]) and glass
 * pieces draw as plain tinted bubbles; at a normal pace they are back. A single flag for the whole
 * app, because there is only ever one list scrolling under glass at a time.
 */
object GlassPerformance {
    internal var scrollFast by mutableStateOf(false)

    /** True while the keyboard slides in or out: every frame resizes the screen and the glass over it. */
    internal var keyboardMoving by mutableStateOf(false)

    /** Glass draws as plain tinted bubbles while the list flies past or the keyboard moves. */
    val lite: Boolean get() = scrollFast || keyboardMoving

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
            if (!GlassPerformance.scrollFast && speed > GlassPerformance.FAST) GlassPerformance.scrollFast = true
            else if (GlassPerformance.scrollFast && speed < GlassPerformance.SLOW) GlassPerformance.scrollFast = false
        }
    }
    DisposableEffect(Unit) { onDispose { GlassPerformance.scrollFast = false } }
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

/**
 * While the keyboard moves, the screen is resized on every frame and each glass piece would redo its
 * blur and refraction over it, which made the keyboard open slowly and the messages, the status bar
 * and the gesture bar flicker behind it. So glass goes plain ([GlassPerformance.lite]) from the first
 * change of the keyboard's height until it has been still for a moment, and is back after that.
 */
@Composable
fun rememberGlassKeyboardMeter() {
    val ime = androidx.compose.foundation.layout.WindowInsets.ime
    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(Unit) {
        snapshotFlow { ime.getBottom(density) }.collectLatest {
            GlassPerformance.keyboardMoving = true
            delay(350)
            GlassPerformance.keyboardMoving = false
        }
    }
    DisposableEffect(Unit) { onDispose { GlassPerformance.keyboardMoving = false } }
}
