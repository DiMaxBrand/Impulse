package eu.siacs.conversations.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
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
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * "A button turns into a menu" (and "a search field turns into the whole search"), the way the glass
 * of iOS does it: not a popup appearing next to the button, but the button's own bubble growing and
 * moving into the new shape while its contents change. One glass container is drawn at a size and
 * place interpolated between where the button is and where the panel ends up; a spring drives the
 * interpolation, so it overshoots and settles like liquid. It is drawn in the same window as the rest
 * of the screen (a Popup would be a second window and could not show the screen behind it through
 * the glass), as an overlay at the root of the screen.
 */

/**
 * One glass container whose size and place are interpolated by [progress] (0 = at [from], 1 =
 * [target]). Both rectangles are in the overlay's own coordinates. The corners and the body colour
 * change with the progress: the surface opacity runs from [surfaceAlphaFrom] (a clear bubble) to the
 * [style]'s own (a calmer, more opaque panel, so text over a busy list stays readable). The blur and
 * the lens are the style's, kept the same for the whole motion so the effect is not rebuilt every
 * frame. [content] gets the container's animated size and is clipped to it; wrap it in
 * `requiredWidth`/`requiredSize` to keep its layout at the final size while the container is still
 * growing.
 *
 * [glassUntil]: a container that ends up covering the screen drops the blur and the lens once its
 * progress passes this, when it is nearly opaque anyway: blurring and bending a whole screen every
 * frame is what made the search stutter. Menus never do (the default is never).
 *
 * [target] is told the overlay's size and can ask for the height content wants at a width.
 */
@Composable
fun LiquidMorphContainer(
    progress: () -> Float,
    from: () -> Rect,
    target: (overlay: IntSize, heightForWidth: (Int) -> Int) -> Rect,
    glass: GlassStack,
    modifier: Modifier = Modifier,
    style: LiquidGlassStyle = LiquidGlassStyle.Menu,
    cornerFrom: Dp = 26.dp,
    cornerTo: Dp = 28.dp,
    surfaceAlphaFrom: Float = 0.28f,
    glassUntil: Float = Float.POSITIVE_INFINITY,
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
                            val p = progress().coerceIn(0f, 1f)
                            RoundedCornerShape(
                                with(density) { lerp(cornerFrom.toPx(), cornerTo.toPx(), p) }
                            )
                        },
                        effects = {
                            if (progress() <= glassUntil) {
                                if (style.vibrancy) vibrancy()
                                blur(style.blur.toPx())
                                glassLens(
                                    style.lensHeight.toPx(),
                                    style.lensAmount.toPx(),
                                    style.chromaticAberration,
                                )
                            }
                        },
                        onDrawSurface = {
                            val p = progress().coerceIn(0f, 1f)
                            drawRect(surface.copy(alpha = lerp(surfaceAlphaFrom, style.surfaceAlpha, p)))
                        },
                    ),
                contentAlignment = Alignment.TopStart,
                content = content,
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val glassBox = measurables.first()
        val overlay = IntSize(constraints.maxWidth, constraints.maxHeight)
        val p = progress() // may overshoot 1: that is the liquid part
        val a = from()
        val b = target(overlay) { width -> glassBox.maxIntrinsicHeight(width) }
        val w = lerp(a.width, b.width, p).roundToInt().coerceAtLeast(1)
        val h = lerp(a.height, b.height, p).roundToInt().coerceAtLeast(1)
        val x = lerp(a.left, b.left, p).roundToInt()
        val y = lerp(a.top, b.top, p).roundToInt()
        val placeable = glassBox.measure(Constraints.fixed(w, h))
        layout(overlay.width, overlay.height) { placeable.place(x, y) }
    }
}

/**
 * State of one glass menu: whether it is open, where the button that opens it is (in window
 * coordinates, set by [liquidMenuAnchor]) and the spring-driven [progress] of the morph.
 */
@Stable
class LiquidMenuState {
    var open by mutableStateOf(false)
    var anchor by mutableStateOf(Rect.Zero)
    val progress = Animatable(0f, 0.001f)

    /** For the button itself: it fades away as its bubble becomes the menu, and back when it closes. */
    val anchorAlpha: Float get() = 1f - (progress.value * 4f).coerceIn(0f, 1f)
}

@Composable
fun rememberLiquidMenuState(): LiquidMenuState = remember { LiquidMenuState() }

/** Marks the button that opens the menu, so the menu knows where to grow from. */
fun Modifier.liquidMenuAnchor(state: LiquidMenuState): Modifier =
    onGloballyPositioned { state.anchor = it.boundsInWindow() }

/**
 * The menu itself, to be placed once at the root of the screen (after everything it should cover,
 * inside the box whose [glass] stack holds the screen's content). The bubble of the anchor button
 * grows into a [menuWidth]-wide panel whose right edge lines up with the button's; [content] (the
 * items) fades in once the panel is mostly open. A tap anywhere else, or back, closes it.
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
    LaunchedEffect(state.open) {
        state.progress.animateTo(if (state.open) 1f else 0f, spring(dampingRatio = 0.78f, stiffness = 380f))
    }
    BackHandler(enabled = state.open) { state.open = false }
    // Derived, so the menu's items are not rebuilt on every frame of the opening animation.
    val visible by remember { derivedStateOf { state.open || state.progress.value > 0.001f } }
    if (!visible) return

    val density = LocalDensity.current
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
            progress = { state.progress.value },
            from = { state.anchor.translate(-origin) },
            target = { overlay, heightForWidth ->
                val width = min(with(density) { menuWidth.roundToPx() }, overlay.width - 2 * margin)
                // A long menu stops at the screen edge and scrolls (see the column below).
                val height = min(heightForWidth(width), overlay.height - 2 * margin)
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
        ) {
            Column(
                Modifier
                    .requiredWidth(menuWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
                    .graphicsLayer { alpha = ((state.progress.value - 0.35f) / 0.65f).coerceIn(0f, 1f) },
                content = content,
            )
        }
    }
}

/** A row of a glass menu: an icon and a label. */
@Composable
fun LiquidMenuItem(iconRes: Int, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick).padding(horizontal = 20.dp),
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
