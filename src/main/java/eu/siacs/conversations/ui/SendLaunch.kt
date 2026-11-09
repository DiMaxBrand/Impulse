package eu.siacs.conversations.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import eu.siacs.conversations.ui.glass.GlassStack
import eu.siacs.conversations.ui.glass.pauseGlassInkWhile
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.roundToInt

internal object SendLaunchMotion {
    const val DURATION_MS = 880
    fun ease(value: Float): Float { val x = value.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
    fun material(progress: Float) = ease((progress - 0.40f) / 0.54f)
    fun wobble(progress: Float, amplitude: Float) =
        (sin(progress * PI * 4) * sin(progress * PI) * (1f - progress) * amplitude).toFloat()
    fun rect(from: Rect, to: Rect, progress: Float, density: Float): Rect {
        val travel = ease(progress / 0.83f)
        val size = ease(progress / 0.80f)
        val settle = (sin((progress - 0.55f).coerceAtLeast(0f) / 0.45f * PI) * (1f - progress) * 0.12f).toFloat()
        val width = (from.width + (to.width - from.width) * size) * (1f + settle)
        val height = (from.height + (to.height - from.height) * size) / (1f + settle)
        val cx = from.center.x + (to.center.x - from.center.x) * travel + wobble(progress, 22f * density)
        val cy = from.center.y + (to.center.y - from.center.y) * travel - (sin(progress * PI) * 12f * density).toFloat()
        return Rect(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f)
    }
    fun displacement(height: Float, hasAbove: Boolean, hasBelow: Boolean): Pair<Float, Float> =
        when {
            hasAbove && hasBelow -> -height / 2f to height / 2f
            hasAbove -> -height to 0f
            hasBelow -> 0f to height
            else -> 0f to 0f
        }
}

internal class SendLaunchState {
    class Flight(val uuid: String, val conversation: String, val source: Rect, val sourceText: String, val voice: Boolean,
        val onStart: (() -> Unit)? = null) {
        val progress = Animatable(0f)
        /** The actual composer/timer pixels captured before its state is cleared. */
        var sourceLayer: GraphicsLayer? = null
        var target by mutableStateOf(Rect.Zero)
        var layer by mutableStateOf<GraphicsLayer?>(null)
        var recorded by mutableStateOf(false)
        var prepared by mutableStateOf(false)
        var firstOfGroup = true
        var lastOfGroup = true
        var alignEnd = true
        var splitReserved = false
        var started = false
    }
    val flights = mutableStateListOf<Flight>()
    val rows = mutableStateMapOf<String, Rect>()
    var textOrigin by mutableStateOf(Rect.Zero)
    var voiceOrigin by mutableStateOf(Rect.Zero)
    var composerLayer by mutableStateOf<GraphicsLayer?>(null)
    var composerCaptureEpoch by mutableIntStateOf(0)
    private var before: Map<String, Rect> = emptyMap()
    private var rowDriver by mutableStateOf<Flight?>(null)

    fun contains(uuid: String) = flights.any { it.uuid == uuid }
    fun enqueue(uuid: String?, conversation: String?, source: Rect, text: String, voice: Boolean,
        onStart: (() -> Unit)? = null): Boolean {
        if (uuid == null || conversation == null || source.isEmpty || contains(uuid)) return false
        before = rows.mapValues { (key, rect) -> rect.translate(Offset(0f, offset(key))) }
        val flight = Flight(uuid, conversation, source, text, voice, onStart).also {
            // This layer was drawn while the composer still contained [text]. It must be held
            // rather than recreated from Text after state.setInput("") has run.
            it.sourceLayer = composerLayer
        }
        // A second send gets a new live layer so it cannot overwrite the first flight's pixels.
        composerCaptureEpoch++
        rowDriver = flight
        flights += flight
        return true
    }
    fun offset(key: String): Float {
        val driver = rowDriver ?: return 0f
        val old = before[key] ?: return 0f
        val current = rows[key] ?: return 0f
        return (old.top - current.top) * (1f - SendLaunchMotion.ease(driver.progress.value / 0.85f))
    }
    fun finish(flight: Flight) {
        flights.remove(flight)
        if (rowDriver === flight || flights.isEmpty()) { rowDriver = null; before = emptyMap() }
    }
    fun start(flight: Flight) {
        if (flight.started) return
        flight.started = true
        flight.onStart?.invoke()
    }
    fun clear(keepGeometry: Boolean = false) {
        flights.clear(); if (!keepGeometry) rows.clear()
        rowDriver = null; before = emptyMap()
    }
}

/**
 * Keep an exact rendered copy of the glass composer. Once a send is pending we deliberately stop
 * recording into this layer: the next composition has already cleared the typed text, but the
 * launch still needs the preceding field, shape and contents as its first frame.
 */
@Composable
internal fun launchComposerSourceModifier(state: SendLaunchState): Modifier {
    var keyedLayer: GraphicsLayer? = null
    key(state.composerCaptureEpoch) {
        keyedLayer = rememberGraphicsLayer()
    }
    val layer = requireNotNull(keyedLayer)
    DisposableEffect(state, layer) {
        state.composerLayer = layer
        onDispose { if (state.composerLayer === layer) state.composerLayer = null }
    }
    return Modifier.drawWithContent {
        if (state.composerLayer === layer) {
            layer.record(IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))) {
                this@drawWithContent.drawContent()
            }
        }
        drawContent()
    }
}

@Composable
internal fun launchRowModifier(state: SendLaunchState, key: String): Modifier {
    DisposableEffect(state, key) { onDispose { state.rows.remove(key) } }
    return Modifier.onGloballyPositioned { state.rows[key] = it.boundsInWindow(clipBounds = false) }
        .drawWithContent {
            // Draw-only displacement leaves destination coordinates stable while the other
            // rows make room; feeding a translated boundsInWindow back into the flight jitters.
            translate(top = state.offset(key)) { this@drawWithContent.drawContent() }
        }
}

@Composable
internal fun launchBubbleModifier(state: SendLaunchState?, item: String,
    first: Boolean, last: Boolean, alignEnd: Boolean): Modifier {
    val flight = state?.flights?.firstOrNull { it.uuid == item } ?: return Modifier
    val layer = rememberGraphicsLayer()
    SideEffect { flight.firstOfGroup = first; flight.lastOfGroup = last; flight.alignEnd = alignEnd }
    DisposableEffect(flight) { onDispose { flight.layer = null; state.finish(flight) } }
    return Modifier.onGloballyPositioned { flight.target = it.boundsInWindow(clipBounds = false) }
        .drawWithContent {
            layer.record(IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))) {
                this@drawWithContent.drawContent()
            }
            if (flight.layer !== layer) flight.layer = layer
            if (!flight.recorded) flight.recorded = true
            // The overlay draws this exact layer until the glass-to-material settle completes.
        }
}

@Composable
internal fun SendLaunchOverlay(state: SendLaunchState, glass: GlassStack?) {
    LaunchedEffect(glass, AppearanceState.fullEffects) { if (glass == null || !AppearanceState.fullEffects) state.clear(keepGeometry = true) }
    DisposableEffect(state) { onDispose { state.clear() } }
    if (glass == null || !AppearanceState.fullEffects || state.flights.isEmpty()) return
    pauseGlassInkWhile { state.flights.isNotEmpty() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInWindow(clipBounds = false).topLeft }) {
        state.flights.toList().forEach { flight ->
            key(flight.uuid) { LaunchActor(state, flight, glass, origin) }
        }
    }
}

@Composable
private fun LaunchActor(state: SendLaunchState, flight: SendLaunchState.Flight, glass: GlassStack, origin: Offset) {
    LaunchedEffect(flight) {
        val ready = withTimeoutOrNull(700) {
            snapshotFlow { flight.prepared && flight.recorded && !flight.target.isEmpty && flight.layer != null }.first { it }
        }
        if (ready == null) { state.start(flight); state.finish(flight); return@LaunchedEffect }
        state.start(flight)
        withFrameNanos { }
        flight.progress.animateTo(1f, tween(SendLaunchMotion.DURATION_MS, easing = LinearEasing))
        state.finish(flight)
    }
    val layer = flight.layer ?: return
    if (!flight.recorded || flight.target.isEmpty) return
    val density = LocalDensity.current
    val progress = flight.progress.value
    // Another rapid send can still be moving this row when its own launch finishes.
    // Meet its drawn position, rather than handing off to a differently displaced bubble.
    val destination = flight.target.translate(Offset(0f, state.offset(flight.uuid)))
    val rect = SendLaunchMotion.rect(flight.source, destination, progress, density.density).translate(-origin)
    val material = SendLaunchMotion.material(progress)
    val tail = SendLaunchMotion.ease((progress - 0.30f) / 0.60f)
    val shape: Shape = bubbleTailShape(flight.alignEnd,
        groupTopCorner = minOf(with(density) { (if (flight.firstOfGroup) 20.dp else 5.dp).toPx() }, rect.height / 2f, rect.width / 2f),
        largeCorner = minOf(with(density) { 20.dp.toPx() }, rect.height / 2f),
        tailWidth = if (flight.lastOfGroup) with(density) { 8.dp.toPx() } * tail.coerceAtLeast(0.001f) else 0.01f,
        tailHeight = if (flight.lastOfGroup) with(density) { 14.dp.toPx() } * tail.coerceAtLeast(0.001f) else 0.01f)
    val placement = Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
        .size(with(density) { rect.width.coerceAtLeast(1f).toDp() }, with(density) { rect.height.coerceAtLeast(1f).toDp() })
    Box(placement) {
        val sourceAlpha = (1f - SendLaunchMotion.ease(progress / 0.60f)).coerceIn(0f, 1f)
        // The captured panel is exact at progress 0. Its gently scaled pixels make the source
        // visibly leave the composer before the separately drawn tail and normal bubble take over.
        flight.sourceLayer?.let { sourceLayer ->
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = sourceAlpha }) {
                if (sourceLayer.size.width > 0 && sourceLayer.size.height > 0) {
                    scale(size.width / sourceLayer.size.width, size.height / sourceLayer.size.height, pivot = Offset.Zero) {
                        drawLayer(sourceLayer)
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = (1f - material) * (1f - sourceAlpha * 0.75f) }
            // The launch shape grows a real message tail. Backdrop's lens implementation accepts
            // only rounded rectangles, so applying it to this GenericShape crashes on attach.
            // Blur supports the tail and keeps the glass phase continuous into the material bubble.
            .drawBackdrop(glass.base, shape = { shape }, effects = { blur(2.dp.toPx()) }))
        if (flight.sourceLayer == null) {
            // A freshly attached screen can send before its first draw. Keep a safe fallback;
            // ordinary interaction takes the captured-layer path above.
            Text(flight.sourceText, Modifier.align(Alignment.Center).padding(horizontal = if (flight.voice) 0.dp else 12.dp)
                .graphicsLayer { alpha = (1f - progress / 0.55f).coerceIn(0f, 1f) },
                color = MaterialTheme.colorScheme.onSurface,
                style = if (flight.voice) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge,
                maxLines = if (flight.voice) 1 else 4, overflow = TextOverflow.Ellipsis)
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = material }) {
            if (layer.size.width > 0 && layer.size.height > 0) {
                scale(size.width / layer.size.width, size.height / layer.size.height, pivot = Offset.Zero) { drawLayer(layer) }
            }
        }
    }
}
