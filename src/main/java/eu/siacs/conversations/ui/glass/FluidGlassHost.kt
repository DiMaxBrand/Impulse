package eu.siacs.conversations.ui.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/*
 * The real glass of the app on the fluid layer (FluidGlass.kt): a [FluidGlassHost] is the area (a bar,
 * a row of buttons) whose glass elements are drawn as one body, and each LiquidGlassButton or
 * LiquidGlassPanel whose GlassStack has a host registers itself there instead of drawing a glass node
 * of its own. The element keeps its content, its press and its click; the host draws the glass behind
 * it, bridging elements that are close.
 */

/**
 * How far a pressed element follows a dragging finger in fluid glass: the old buttons moved ~5% of the
 * finger's travel, so little that two bubbles never got close enough to join. Three times that (still squeezed
 * through tanh, so a long drag does not carry it far).
 */
internal const val FLUID_LEAN = 0.15f

/** One glass element registered in a host. */
@Stable
class FluidElement(val id: Int) {
    /** The element's place and size in the host (layout: not including its press swell). */
    var rect by mutableStateOf(Rect.Zero)
    var corner by mutableFloatStateOf(0f)
    var visible by mutableStateOf(true)
    var frost by mutableFloatStateOf(0f)
    var frostRim by mutableFloatStateOf(0f)
    var interactive: InteractiveHighlight? = null
    var ink: GlassInk? = null

    /** A colour over the glass (Unspecified: none). */
    var tint by mutableStateOf(androidx.compose.ui.graphics.Color.Unspecified)

    /** 0..1: how much of itself the element is now (it grows out of nothing). Null: all of it. */
    var presence: (() -> Float)? = null

    /** How far the element is moved by something that does not change its layout (a pull, a press): px. */
    var shift: (() -> Offset)? = null

    /** Px the glass reaches out to the right of the element (the message field pressing on the send button). */
    var stretch: (() -> Float)? = null

    /** Where the corner radius comes from while it changes (a menu growing out of a button): px. */
    var cornerProvider: (() -> Float)? = null

    /** How frosted the element is while that changes (a menu opening: clear as a button, frosted as a panel). */
    var frostProvider: (() -> Float)? = null

    /** Where the colour over the glass comes from while it changes. */
    var tintProvider: (() -> androidx.compose.ui.graphics.Color)? = null

    /** The element's own layout, kept so it can be placed again once the host's layout is known. */
    var coords: LayoutCoordinates? = null
    var cornerPx = 0f
}

@Stable
class FluidGlassHost(
    private val density: Float,
    val style: LiquidGlassStyle = LiquidGlassStyle.Bar,
    reachDp: Float = 5f,
    softnessDp: Float = 10f,
    responseSeconds: Float = 0.3f,
) : FluidScene {
    private var nextId = 0
    private val elements = mutableStateListOf<FluidElement>()
    private val shapesById = HashMap<Int, FluidShape>()
    private val bridges = FluidBridges().also {
        it.reach = reachDp * density
        it.softness = softnessDp * density
        it.response = responseSeconds
    }

    /** The host's own layout, which element places are measured against. */
    var hostCoords: LayoutCoordinates? = null

    override var tick by mutableIntStateOf(0)
        private set
    override var frostActive by mutableStateOf(false)
        private set

    /** How the frosted elements look (the first one that says). */
    var frostStyle by mutableStateOf<LiquidGlassStyle?>(null)

    private val wakeups = Channel<Unit>(Channel.CONFLATED)

    fun wake() {
        wakeups.trySend(Unit)
    }

    fun newElement(): FluidElement {
        val e = FluidElement(nextId++)
        elements.add(e)
        wake()
        return e
    }

    fun remove(e: FluidElement) {
        elements.remove(e)
        shapesById.remove(e.id)
        bridges.forget(e.id)
        wake()
    }

    /** An element was laid out: where it is in the host now. */
    fun place(e: FluidElement, coords: LayoutCoordinates, corner: Float) {
        e.coords = coords
        e.cornerPx = corner
        placeElement(e)
    }

    private fun placeElement(e: FluidElement) {
        val host = hostCoords ?: return
        val coords = e.coords ?: return
        if (!host.isAttached || !coords.isAttached) return
        val at = host.localPositionOf(coords, Offset.Zero)
        val r = Rect(at, Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
        if (r != e.rect) e.rect = r
        if (e.cornerPx != e.corner) e.corner = e.cornerPx
    }

    /** The host was laid out (or moved): every element is placed against it again. */
    fun hostPlaced(coords: LayoutCoordinates) {
        hostCoords = coords
        for (e in elements) placeElement(e)
        wake()
    }

    override fun bridgeBlend(a: FluidShape, b: FluidShape): Float = bridges.blend(a, b)

    /**
     * How much of an element's own content (icon, text) shows. Where two elements overlap, the glass flows into one
     * body, so nothing but the content has a front and a back: the one that is pressed (else the later one) keeps its
     * content, the other's fades out as the overlap grows, instead of being covered by the other's.
     */
    fun contentAlpha(e: FluidElement): Float {
        tick // follow the bridges' steps
        val mine = shapesById[e.id] ?: return 1f
        var alpha = 1f
        for (o in elements) {
            if (o === e || !o.visible) continue
            val other = shapesById[o.id] ?: continue
            val wins = if (other.press != mine.press) other.press > mine.press else o.id > e.id
            if (!wins) continue
            val overlap = -bridges.gapBetween(mine, other)
            // Hidden once its centre is covered (a bubble under a growing menu), or as the overlap grows.
            val centre = -shapeSd(other, mine.cx, mine.cy)
            if (overlap <= 0f && centre <= 0f) continue
            val byOverlap = overlap / (0.55f * min(mine.hw, mine.hh))
            val byCentre = centre / (0.5f * min(mine.hw, mine.hh))
            val t = max(byOverlap, byCentre).coerceIn(0f, 1f)
            alpha = min(alpha, 1f - t * t * (3f - 2f * t))
        }
        return alpha
    }

    override fun bounds(): Rect? {
        val all = shapes()
        if (all.isEmpty()) return Rect.Zero
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (s in all) {
            // The box that holds the (turned) shape, not the circle around it: a wide bubble is not a big square.
            val c = kotlin.math.abs(kotlin.math.cos(s.angle))
            val sn = kotlin.math.abs(kotlin.math.sin(s.angle))
            val ex = c * s.hw + sn * s.hh + 2f
            val ey = sn * s.hw + c * s.hh + 2f
            l = min(l, s.cx - ex); t = min(t, s.cy - ey)
            r = max(r, s.cx + ex); b = max(b, s.cy + ey)
        }
        return Rect(l, t, r, b)
    }

    override fun shapes(): List<FluidShape> {
        val out = ArrayList<FluidShape>(elements.size)
        for (e in elements) {
            if (!e.visible) continue
            val r = e.rect
            if (r.width <= 0f || r.height <= 0f) continue
            val presence = (e.presence?.invoke() ?: 1f).coerceIn(0f, 1.2f)
            if (presence < 0.02f) continue
            val stretch = e.stretch?.invoke() ?: 0f
            val shift = e.shift?.invoke() ?: Offset.Zero
            val sh = shapesById.getOrPut(e.id) { FluidShape(e.id) }
            val ia = e.interactive
            var sx = 1f
            var sy = 1f
            var tx = 0f
            var ty = 0f
            if (ia != null) {
                val g = pressGeometry(r.width, r.height, density, ia.pressProgress, ia.offset, FLUID_LEAN)
                sx = g[0]; sy = g[1]; tx = g[2]; ty = g[3]
            }
            sh.cx = r.center.x + tx + stretch / 2f + shift.x
            sh.cy = r.center.y + ty + shift.y
            sh.hw = r.width / 2f * sx * presence + stretch / 2f
            sh.hh = r.height / 2f * sy * presence
            sh.radius = min((e.cornerProvider?.invoke() ?: e.corner) * min(sx, sy), min(sh.hw, sh.hh))
            sh.angle = 0f
            val tc = e.tintProvider?.invoke() ?: e.tint
            if (tc.isSpecified && tc.alpha > 0f) {
                sh.tintR = tc.red; sh.tintG = tc.green; sh.tintB = tc.blue; sh.tintA = tc.alpha
            } else {
                sh.tintA = 0f
            }
            sh.frost = e.frostProvider?.invoke() ?: e.frost
            // A thin element (a banner, the message field) must not be all rim: the clear rim is capped to a share of its half-height.
            sh.frostRim = min(e.frostRim, min(sh.hw, sh.hh) * 0.4f)
            sh.brightness = e.ink?.let { glassBrightness(it.level.value) } ?: 0f
            sh.press = ia?.pressProgress ?: 0f
            if (ia != null) {
                val t = ia.touchAt(Size(r.width, r.height))
                sh.touchX = r.left + t.x
                sh.touchY = r.top + t.y
            }
            out.add(sh)
        }
        return out
    }

    /** What is behind each element, recorded for its ink about three times a second (see rememberGlassInk). */
    override fun sampleInks(
        originX: Float,
        originY: Float,
        scope: androidx.compose.ui.graphics.drawscope.DrawScope,
        draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
    ) {
        for (e in elements) {
            val ink = e.ink ?: continue
            if (!ink.adaptive || !e.visible) continue
            val r = e.rect
            if (r.width < 1f || r.height < 1f) continue
            if (!ink.request.take()) continue
            with(scope) {
                ink.layer.record(androidx.compose.ui.unit.IntSize(r.width.toInt(), r.height.toInt())) {
                    // The backdrop is drawn in the layer's own coordinates: the area's, less where the layer starts.
                    translate(originX - r.left, originY - r.top) { draw() }
                }
            }
        }
    }

    private fun signature(): Long {
        var h = 17L
        for (s in shapes()) {
            h = h * 31 + s.cx.toBits()
            h = h * 31 + s.cy.toBits()
            h = h * 31 + s.hw.toBits()
            h = h * 31 + s.hh.toBits()
            h = h * 31 + s.id
        }
        return h
    }

    private fun step(dt: Float): Boolean {
        val moving = bridges.step(dt, shapes(), false)
        val frosted = elements.any { it.visible && (it.frostProvider?.invoke() ?: it.frost) > 0.002f }
        if (frosted != frostActive) frostActive = frosted
        tick++
        return moving
    }

    /** Runs the bridges while anything moves; start it once per host. */
    suspend fun run() = coroutineScope {
        launch { snapshotFlow { signature() }.collect { wake() } }
        for (signal in wakeups) {
            var last = 0L
            var calm = 0
            while (calm < 4) {
                val frame = withFrameNanos { it }
                val dt = if (last == 0L) 1f / 60f else ((frame - last) / 1_000_000_000f).coerceAtMost(1f / 30f)
                last = frame
                calm = if (step(dt)) 0 else calm + 1
            }
        }
    }
}

/**
 * The press and drag transform of a glass button, as numbers: scale x, scale y, move x, move y (px).
 * The button's own layer and the fluid layer's shape both take it from here, so they cannot differ.
 */
internal fun pressGeometry(width: Float, height: Float, density: Float, progress: Float, offset: Offset, derivative: Float = 0.05f): FloatArray {
    val scale = 1f + (4f * density / height) * progress
    val maxOffset = min(width, height)
    val initialDerivative = derivative
    val tx = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
    val ty = maxOffset * tanh(initialDerivative * offset.y / maxOffset)
    val maxDragScale = 4f * density / height
    val offsetAngle = atan2(offset.y, offset.x)
    val maxDim = max(width, height)
    val sx = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / maxDim) * (width / height).coerceAtMost(1f)
    val sy = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / maxDim) * (height / width).coerceAtMost(1f)
    return floatArrayOf(sx, sy, tx, ty)
}

/** A host for one screen's glass bars, or null when merging glass is off (the old glass is used then). */
@Composable
fun rememberFluidHost(enabled: Boolean, reachDp: Float = 5f): FluidGlassHost? {
    val density = LocalDensity.current.density
    return remember(enabled) { if (enabled) FluidGlassHost(density, LiquidGlassStyle.Bar, reachDp = reachDp) else null }
}

/** Whether the developer flag for merging glass is on (read once per screen). */
@Composable
fun rememberMergingGlass(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember {
        eu.siacs.conversations.utils.FeatureFlagPreferences(context).isEnabled(eu.siacs.conversations.FeatureFlag.MERGING_GLASS)
    }
}

/** The area whose glass elements are drawn as one body: the glass behind [content], the elements in it. */
@Composable
fun FluidGlassHost(
    base: Backdrop,
    host: FluidGlassHost,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    LaunchedEffect(host) { host.run() }
    val density = LocalDensity.current
    val style = host.style
    val frost = host.frostStyle
    val surface = MaterialTheme.colorScheme.surface
    val look = remember(style, frost, surface) {
        with(density) {
            FluidLook(
                refractionHeight = style.lensHeight,
                refractionAmount = style.lensAmount,
                fringes = style.chromaticAberration,
                blur = style.blur,
                saturation = if (style.vibrancy) 1.25f else style.saturation,
                shadow = true,
                frostBlur = frost?.blur ?: 18.dp,
                veil = surface.copy(alpha = frost?.surfaceAlpha ?: 0f),
                frostSaturation = frost?.saturation ?: 0.68f,
                frostContrast = frost?.contrast ?: 0.9f,
            )
        }
    }
    Box(modifier.onGloballyPositioned { host.hostPlaced(it) }) {
        FluidGlassLayer(base, host, look, Modifier.matchParentSize())
        content()
    }
}

/** A glass button that is an element of a fluid host: its content, press and click, and no glass of its own. */
@Composable
internal fun FluidButton(
    fluid: FluidGlassHost,
    adaptive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    shape: CornerBasedShape,
    isInteractive: Boolean,
    recording: Boolean,
    interactiveHighlight: InteractiveHighlight,
    extraLayerBlock: (GraphicsLayerScope.() -> Unit)?,
    tint: androidx.compose.ui.graphics.Color,
    presence: (() -> Float)?,
    shift: (() -> Offset)?,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val element = remember(fluid) { fluid.newElement() }
    DisposableEffect(element) { onDispose { fluid.remove(element) } }
    val ink = rememberGlassInk(adaptive)
    SideEffect {
        element.ink = ink
        element.visible = recording
        element.interactive = if (isInteractive) interactiveHighlight else null
        element.frost = 0f
        element.tint = tint
        element.presence = presence
        element.shift = shift
        fluid.wake()
    }
    Box(
        modifier
            .onGloballyPositioned { c ->
                val size = Size(c.size.width.toFloat(), c.size.height.toFloat())
                fluid.place(element, c, shape.topStart.toPx(size, density))
            }
            .graphicsLayer {
                alpha = fluid.contentAlpha(element)
                if (isInteractive) {
                    val g = pressGeometry(size.width, size.height, this.density, interactiveHighlight.pressProgress, interactiveHighlight.offset, FLUID_LEAN)
                    scaleX = g[0]
                    scaleY = g[1]
                    translationX = g[2]
                    translationY = g[3]
                }
                extraLayerBlock?.invoke(this)
            }
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .then(if (isInteractive) Modifier.then(interactiveHighlight.gestureModifier) else Modifier),
        contentAlignment = Alignment.Center,
        content = { GlassInkBox(Modifier.matchParentSize(), Alignment.Center, ink, content) },
    )
}

/**
 * The glass of a menu growing out of a button, as an element of the host's body (the button's neighbours
 * can bridge to it): the box has the menu's size and place at every moment, and the glass is the host's.
 */
@Composable
internal fun FluidMenuBox(
    fluid: FluidGlassHost,
    style: LiquidGlassStyle,
    corner: () -> Float,
    frostAmount: () -> Float,
    tint: () -> androidx.compose.ui.graphics.Color,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val element = remember(fluid) { fluid.newElement() }
    DisposableEffect(element) { onDispose { fluid.remove(element) } }
    SideEffect {
        element.visible = true
        element.interactive = null
        element.frostRim = with(density) { style.frostRim.toPx() }
        element.cornerProvider = corner
        element.tintProvider = tint
        element.frostProvider = if (style.frost != null) frostAmount else null
        if (style.frost != null && fluid.frostStyle == null) fluid.frostStyle = style.frost
        fluid.wake()
    }
    Box(
        Modifier.onGloballyPositioned { c -> fluid.place(element, c, 0f) },
        contentAlignment = Alignment.TopStart,
        content = content,
    )
}

/** A glass panel that is an element of a fluid host. */
@Composable
internal fun FluidPanel(
    fluid: FluidGlassHost,
    adaptive: Boolean,
    modifier: Modifier,
    shape: CornerBasedShape,
    style: LiquidGlassStyle,
    contentAlignment: Alignment,
    clipContent: Boolean,
    visible: Boolean,
    tint: androidx.compose.ui.graphics.Color,
    stretch: (() -> Float)?,
    presence: (() -> Float)?,
    shift: (() -> Offset)?,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val element = remember(fluid) { fluid.newElement() }
    DisposableEffect(element) { onDispose { fluid.remove(element) } }
    val ink = rememberGlassInk(adaptive)
    SideEffect {
        element.ink = ink
        element.visible = visible
        element.interactive = null
        element.tint = tint
        element.stretch = stretch
        element.presence = presence
        element.shift = shift
        element.frost = if (style.frost != null) 1f else 0f
        element.frostRim = with(density) { style.frostRim.toPx() }
        if (style.frost != null && fluid.frostStyle == null) fluid.frostStyle = style.frost
        fluid.wake()
    }
    Box(
        modifier.onGloballyPositioned { c ->
            val size = Size(c.size.width.toFloat(), c.size.height.toFloat())
            fluid.place(element, c, shape.topStart.toPx(size, density))
        },
        contentAlignment = contentAlignment,
    ) {
        GlassInkBox(
            (if (clipContent) Modifier.matchParentSize().clip(shape) else Modifier.clip(shape))
                .graphicsLayer { alpha = fluid.contentAlpha(element) },
            contentAlignment,
            ink,
            content,
        )
    }
}
