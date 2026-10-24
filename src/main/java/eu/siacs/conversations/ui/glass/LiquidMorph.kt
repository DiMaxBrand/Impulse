package eu.siacs.conversations.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tanh
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * "A button turns into a menu" (and "a search field turns into the whole search"), the way the glass
 * of iOS does it: not a popup appearing next to the button, but the button's own bubble growing and
 * moving into the new shape while its contents change. One glass container is drawn at a size and
 * place interpolated between where the button is and where the panel ends up. It is drawn in the
 * same window as the rest of the screen (a Popup would be a second window and could not show the
 * screen behind it through the glass), as an overlay at the root of the screen.
 *
 * It is meant to feel like jelly, not like a box on a spring: the width and the height have springs of
 * their own (the width is quick, the height follows a beat later and swings further), and when one
 * overshoots the other gives a little, so the volume seems to stay the same. And it can be handled:
 * pull the button toward where the menu will open and the menu starts to show, with a good deal of
 * resistance; let go and it either opens fully or goes back. Pull the open menu away and it stretches
 * and springs back; push it back toward its button and it squeezes into it and closes.
 */

/**
 * One glass container whose size and place are interpolated between [from] (progress 0) and
 * [target] (progress 1), the width and the left edge by [progressX], the height and the top edge by
 * [progressY] (the same value by default). Both rectangles are in the overlay's own coordinates. Past
 * 1 it goes further than the target, below 0 it shrinks below the start: that is the jelly. When one
 * of the two is over 1, the other dimension is trimmed a little, so the volume stays about the same.
 *
 * The corners and the body colour follow the average progress: the surface opacity runs from
 * [surfaceAlphaFrom] (a clear bubble) to the [style]'s own (a calmer, more opaque panel, so text over
 * a busy list stays readable). The blur and the lens are the style's, kept the same for the whole
 * motion so the effect is not rebuilt every frame. [content] gets the container's animated size and
 * is clipped to it; wrap it in `requiredWidth`/`requiredSize` to keep its layout at the final size
 * while the container is still growing.
 *
 * [glassUntil]: a container that ends up covering the screen drops the blur and the lens once its
 * progress passes this, when it is nearly opaque anyway: blurring and bending a whole screen every
 * frame is what made the search stutter. Menus never do (the default is never).
 *
 * [target] is told the overlay's size and can ask for the height content wants at a width.
 */
@Composable
fun LiquidMorphContainer(
    progressX: () -> Float,
    from: () -> Rect,
    target: (overlay: IntSize, heightForWidth: (Int) -> Int) -> Rect,
    glass: GlassStack,
    modifier: Modifier = Modifier,
    progressY: () -> Float = progressX,
    style: LiquidGlassStyle = LiquidGlassStyle.Menu,
    cornerFrom: Dp = 26.dp,
    cornerTo: Dp = 28.dp,
    surfaceAlphaFrom: Float = 0f,
    glassUntil: Float = Float.POSITIVE_INFINITY,
    // A rubber-banded nudge of the whole container toward a finger that pulls it (px).
    shift: () -> Offset = { Offset.Zero },
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val surface = MaterialTheme.colorScheme.surface
    val slot = glass.rememberSlot()
    Layout(
        content = {
            Box(
                Modifier
                    .layerBackdrop(slot.record)
                    .drawBackdrop(
                        backdrop = slot.backdrop,
                        shape = {
                            val p = ((progressX() + progressY()) / 2f).coerceIn(0f, 1f)
                            RoundedCornerShape(
                                with(density) { lerp(cornerFrom.toPx(), cornerTo.toPx(), p) }
                            )
                        },
                        effects = {
                            if ((progressX() + progressY()) / 2f <= glassUntil) {
                                if (style.saturation != 1f || style.contrast != 1f) {
                                    colorControls(0f, style.contrast, style.saturation)
                                }
                                if (style.vibrancy) vibrancy()
                                if (style.blur > 0.dp) blur(style.blur.toPx())
                                glassLens(
                                    style.lensHeight.toPx(),
                                    style.lensAmount.toPx(),
                                    style.chromaticAberration,
                                )
                            }
                        },
                        onDrawSurface = {
                            val p = ((progressX() + progressY()) / 2f).coerceIn(0f, 1f)
                            drawRect(surface.copy(alpha = lerp(surfaceAlphaFrom, style.surfaceAlpha, p)))
                        },
                    ),
                contentAlignment = Alignment.TopStart,
                content = {
                    style.frost?.let { frost ->
                        FrostedInterior(
                            backdrop = slot.backdrop,
                            frost = frost,
                            rim = style.frostRim,
                            outerCorner = { _, d ->
                                val p = ((progressX() + progressY()) / 2f).coerceIn(0f, 1f)
                                with(d) { lerp(cornerFrom.toPx(), cornerTo.toPx(), p) }
                            },
                            // A small bubble is still clear glass; the frost comes in as it opens.
                            visible = { (((progressX() + progressY()) / 2f - 0.35f) / 0.5f).coerceIn(0f, 1f) },
                        )
                    }
                    content()
                },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val glassBox = measurables.first()
        val overlay = IntSize(constraints.maxWidth, constraints.maxHeight)
        val px = progressX() // either may overshoot 1: that is the jelly
        val py = progressY()
        val a = from()
        val b = target(overlay) { width -> glassBox.maxIntrinsicHeight(width) }
        // When one dimension swings past its target, the other gives a little: same volume.
        val squashW = if (py > 1f) 1f - 0.35f * (py - 1f) else 1f
        val squashH = if (px > 1f) 1f - 0.35f * (px - 1f) else 1f
        val w = (lerp(a.width, b.width, px) * squashW).roundToInt().coerceAtLeast(1)
        val h = (lerp(a.height, b.height, py) * squashH).roundToInt().coerceAtLeast(1)
        val nudge = shift()
        val x = (lerp(a.left, b.left, px) + nudge.x).roundToInt()
        val y = (lerp(a.top, b.top, py) + nudge.y).roundToInt()
        val placeable = glassBox.measure(Constraints.fixed(w, h))
        layout(overlay.width, overlay.height) { placeable.place(x, y) }
    }
}

/**
 * State of one glass menu: whether it is open, where the button that opens it is (in window
 * coordinates, set by [liquidMenuAnchor]) and how far the morph has got, separately for width
 * ([progressX]) and height ([progressY]). They are driven by springs when the menu opens or closes
 * and by a finger when it is pulled.
 */
@Stable
class LiquidMenuState {
    var open by mutableStateOf(false)
    var anchor by mutableStateOf(Rect.Zero)

    val progressX = Animatable(0f, 0.001f)
    val progressY = Animatable(0f, 0.001f)

    /** How far the whole menu is nudged toward a finger that pulls it (px), springing back on release. */
    val shiftX = Animatable(0f)
    val shiftY = Animatable(0f)

    /** The button's icon, drawn inside the menu too while it is still mostly the button. */
    internal var anchorContent: (@Composable BoxScope.() -> Unit)? = null

    /** The two together: for the things that follow the morph as a whole (fades, corners). */
    val progress: Float get() = (progressX.value + progressY.value) / 2f

    /** For the button itself: it fades away as its bubble becomes the menu, and back when it closes. */
    // The real button is gone the moment the menu is its own bubble: the menu draws the button's
    // look (and icon) itself while it is small, so there is one glass piece, not two.
    val anchorAlpha: Float get() = if (open || progress > 0.001f) 0f else 1f

    // Set by the overlay: which way the panel opens, so a pull on the button (or on the panel) knows
    // which direction counts, and how big the open panel is.
    internal var alignEnd = true
    internal var opensUp = false
    internal var panelHeightPx = 1f

    /** True when the menu is longer than the screen and has to scroll (otherwise it must not, or the
     * scroll would swallow the vertical drag that stretches and squeezes it). */
    internal var panelTall by mutableStateOf(false)

    var settleRequests by mutableIntStateOf(0)
        private set

    /** Open or close, and animate there even if that is the state it already claims to be in (after a
     * pull that was let go of, the progress is somewhere in between). */
    fun releaseTo(value: Boolean) {
        open = value
        settleRequests++
    }

    /** Springs to the open or closed state from wherever the progress is. */
    internal suspend fun settle() = coroutineScope {
        // Whatever the finger nudged springs back, like a gel that is let go of.
        launch { shiftX.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 320f)) }
        launch { shiftY.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 320f)) }
        if (open) {
            // Opening: the width goes first and fast; the height follows a beat later, slower, and
            // swings further. The two never arrive together.
            launch { progressX.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 520f)) }
            launch {
                delay(40)
                progressY.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 280f))
            }
        } else {
            // Closing: the height comes back first, the width a beat after.
            launch { progressY.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 450f)) }
            launch {
                delay(50)
                progressX.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 380f))
            }
        }
    }

    internal suspend fun drag(x: Float, y: Float, shift: Offset = Offset.Zero) {
        progressX.snapTo(x)
        progressY.snapTo(y)
        shiftX.snapTo(shift.x)
        shiftY.snapTo(shift.y)
    }

    /** How far [offset] goes horizontally in the direction the menu grows (negative: the other way). */
    internal fun pullAlongX(offset: Offset): Float = offset.x * (if (alignEnd) -1f else 1f)

    /** The same, vertically. The two axes are separate: a sideways pull stretches the width, a
     * downward pull the height, a diagonal one both. */
    internal fun pullAlongY(offset: Offset): Float = offset.y * (if (opensUp) -1f else 1f)
}

@Composable
fun rememberLiquidMenuState(): LiquidMenuState = remember { LiquidMenuState() }

/**
 * True once the menu has taken the button's place (being pulled, opening or open). The button then
 * stops recording itself for the glass above ([LiquidGlassButton]'s `recording`), so the menu's glass
 * no longer shows the button's icon through itself.
 */
@Composable
fun rememberAnchorHidden(menu: LiquidMenuState): State<Boolean> =
    remember(menu) { derivedStateOf { menu.open || menu.progress > 0.001f } }

/** Marks the button that opens the menu, so the menu knows where to grow from. */
fun Modifier.liquidMenuAnchor(
    state: LiquidMenuState,
    // What the button shows (its icon), so the menu can show the same while it grows out of it.
    icon: (@Composable BoxScope.() -> Unit)? = null,
): Modifier {
    if (icon != null) state.anchorContent = icon
    return onGloballyPositioned { state.anchor = it.boundsInWindow() }
}

/**
 * Pulling the (closed) button the way the menu opens: the menu starts to show, growing with the
 * finger with a lot of resistance (the further it is pulled the less it follows). Let go after a
 * decent pull and it opens fully; after a small one it springs back into the button. A plain tap is
 * not a pull and still opens it as usual.
 */
@Composable
fun Modifier.liquidMenuPull(state: LiquidMenuState): Modifier {
    val scope = rememberCoroutineScope()
    return pointerInput(state) {
        val reach = 150.dp.toPx()
        var total = Offset.Zero
        detectDragGestures(
            onDragStart = { total = Offset.Zero },
            onDrag = { change, drag ->
                change.consume()
                total += drag
                // Each axis follows its own part of the finger's movement, with a lot of resistance.
                val py = 0.8f * tanh(max(state.pullAlongY(total), 0f) / reach)
                val px0 = 0.8f * tanh(max(state.pullAlongX(total), 0f) / (reach * 0.8f))
                // A pull along one axis still widens the other a little, or it would only be a sliver.
                val px = max(px0, 0.3f * py)
                val pyy = max(py, 0.3f * px0)
                // The whole button also follows the finger a little, with a rubber band.
                val cap = 18.dp.toPx()
                val nudge = Offset(cap * tanh(total.x / (cap * 3f)), cap * tanh(total.y / (cap * 3f)))
                scope.launch { state.drag(px, pyy, nudge) }
            },
            onDragEnd = { state.releaseTo(state.progress > 0.22f) },
            onDragCancel = { state.releaseTo(false) },
        )
    }
}

/**
 * The menu itself, to be placed once at the root of the screen (after everything it should cover,
 * inside the box whose [glass] stack holds the screen's content). The bubble of the anchor button
 * grows into a [menuWidth]-wide panel whose edge lines up with the button's; [content] (the items)
 * fades in once the panel is mostly open. A tap anywhere else, or back, closes it. The open panel
 * can be pulled: away from the button it stretches and springs back, toward it it squeezes into the
 * button and, let go of past the half, closes.
 */
@Composable
fun LiquidMenuOverlay(
    state: LiquidMenuState,
    glass: GlassStack,
    modifier: Modifier = Modifier,
    // The menu look: blurrier and more opaque than a button, so what is behind it shows less
    // clearly, but still bending the light (and any glass below it).
    style: LiquidGlassStyle = LiquidGlassStyle.Menu,
    menuWidth: Dp = 236.dp,
    // Which edge of the button the panel lines up with, and which way it grows: a button at the
    // end of a bar at the top opens down and to the left; one at the start of a bar at the bottom
    // opens up and to the right.
    alignEnd: Boolean = true,
    opensUp: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    SideEffect {
        state.alignEnd = alignEnd
        state.opensUp = opensUp
    }
    LaunchedEffect(state.open, state.settleRequests) { state.settle() }
    BackHandler(enabled = state.open) { state.open = false }
    // Derived, so the menu's items are not rebuilt on every frame of the opening animation.
    val visible by remember { derivedStateOf { state.open || state.progress > 0.001f } }
    if (!visible) return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            // Anything outside the panel closes it; the first tap is not passed on.
            .pointerInput(state.open) { if (state.open) detectTapGestures { state.open = false } },
    ) {
        val margin = with(density) { 8.dp.roundToPx() }
        LiquidMorphContainer(
            progressX = { state.progressX.value },
            progressY = { state.progressY.value },
            from = { state.anchor.translate(-origin) },
            target = { overlay, heightForWidth ->
                val width = min(with(density) { menuWidth.roundToPx() }, overlay.width - 2 * margin)
                val natural = heightForWidth(width)
                // A long menu stops at the screen edge and scrolls (see the column below).
                val height = min(natural, overlay.height - 2 * margin)
                val tall = natural > overlay.height - 2 * margin
                if (state.panelTall != tall) state.panelTall = tall
                state.panelHeightPx = height.toFloat()
                val a = state.anchor.translate(-origin)
                // Lined up with the button's edge, grown the way [opensUp] says, kept on screen.
                val wanted = if (alignEnd) a.right - width else a.left
                val left = wanted.coerceIn(margin.toFloat(), (overlay.width - width - margin).toFloat())
                val wantedTop = if (opensUp) a.bottom - height else a.top
                val top = wantedTop.coerceIn(
                    margin.toFloat(),
                    (overlay.height - height - margin).toFloat().coerceAtLeast(margin.toFloat()),
                )
                Rect(left, top, left + width, top + height)
            },
            glass = glass,
            style = style,
            shift = { Offset(state.shiftX.value, state.shiftY.value) },
        ) {
            val reach = with(density) { 120.dp.toPx() }
            val menuWidthPx = with(density) { menuWidth.toPx() }
            var total = Offset.Zero
            // Where on the panel the finger went down, 0 at the button's corner, 1 at the far one.
            var touch = Offset(0.5f, 0.5f)
            val cap = with(density) { 40.dp.toPx() }
            // The button's own look while the menu is still mostly the button: its icon, in the corner
            // the menu grows from, fading as the items come in.
            val icon = state.anchorContent
            if (icon != null) {
                val aw = with(density) { state.anchor.width.toDp() }
                val ah = with(density) { state.anchor.height.toDp() }
                Box(
                    Modifier
                        .align(
                            when {
                                alignEnd && opensUp -> Alignment.BottomEnd
                                alignEnd -> Alignment.TopEnd
                                opensUp -> Alignment.BottomStart
                                else -> Alignment.TopStart
                            }
                        )
                        .requiredSize(aw, ah)
                        .graphicsLayer { alpha = (1f - state.progress * 5f).coerceIn(0f, 1f) },
                    contentAlignment = Alignment.Center,
                    content = icon,
                )
            }
            Column(
                Modifier
                    .requiredWidth(menuWidth)
                    // Pulling the open panel. (Not when it scrolls: then the scroll has the drag.)
                    .pointerInput(state) {
                        detectDragGestures(
                            onDragStart = { at ->
                                total = Offset.Zero
                                val fx = (at.x / menuWidthPx).coerceIn(0f, 1f)
                                val fy = (at.y / state.panelHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                touch = Offset(if (alignEnd) 1f - fx else fx, if (opensUp) 1f - fy else fy)
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                total += drag
                                // Each axis on its own: toward the button squeezes it that way (all the
                                // way if need be), away stretches it up to ~16% past the open size, hard
                                // to pull. A diagonal drag does both at once.
                                // The grip decides how it moves: held near the far corner, that corner
                                // follows the finger and the button's corner hardly moves; held near
                                // the button's corner, the whole menu is dragged along and changes
                                // size less.
                                val gx = 0.6f + 0.4f * touch.x
                                val gy = 0.6f + 0.4f * touch.y
                                val px = axisProgress(state.pullAlongX(total) * gx, -1f, menuWidthPx, reach)
                                val py = axisProgress(state.pullAlongY(total) * gy, -1f, state.panelHeightPx, reach)
                                val wx = 0.1f + 0.3f * (1f - touch.x)
                                val wy = 0.1f + 0.3f * (1f - touch.y)
                                val nudge = Offset(wx * cap * tanh(total.x / cap), wy * cap * tanh(total.y / cap))
                                scope.launch { state.drag(px, py, nudge) }
                            },
                            onDragEnd = { state.releaseTo(state.progress > 0.55f) },
                            onDragCancel = { state.releaseTo(true) },
                        )
                    }
                    .then(if (state.panelTall) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(vertical = 8.dp)
                    .graphicsLayer { alpha = ((state.progress - 0.35f) / 0.65f).coerceIn(0f, 1f) },
                content = content,
            )
        }
    }
}

/** One axis of the open panel while it is dragged: [along] is the drag toward (negative: away from)
 * the button; [extent] the panel's size on that axis. */
private fun axisProgress(along: Float, @Suppress("UNUSED_PARAMETER") sign: Float, extent: Float, reach: Float): Float {
    val toward = -along
    return if (toward > 0f) (1f - toward / (extent * 0.9f)).coerceAtLeast(0f)
    else 1f + 0.16f * tanh(-toward / reach)
}

/**
 * A row of a glass menu: an icon and a label. Pressed, it gets a flat rounded highlight inside the
 * panel, not the circular ripple (the old, shadowy way a button lights up): inset from the panel's
 * edges, soft corners, one flat colour with no gradient, a little faint.
 */
@Composable
fun LiquidMenuItem(iconRes: Int, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 900f),
        label = "menuItemPress",
    )
    val highlight = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .drawBehind { drawRect(highlight.copy(alpha = 0.12f * press)) }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .height(52.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
