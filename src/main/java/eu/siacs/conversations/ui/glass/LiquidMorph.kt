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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import com.kyant.backdrop.Backdrop
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
 * [target]). Both rectangles are in the overlay's own coordinates. The container's corners, blur and
 * body colour change with the progress too: at rest it is a clear, lensing bubble; fully open, a
 * calmer, more opaque panel (so text over a busy list stays readable). [content] gets the container's
 * animated size and is clipped to it; wrap it in `requiredWidth`/`requiredSize` to keep its layout
 * at the final size while the container is still growing.
 *
 * [target] is told the overlay's size and can ask for the height content wants at a width.
 */
@Composable
fun LiquidMorphContainer(
    progress: () -> Float,
    from: () -> Rect,
    target: (overlay: IntSize, heightForWidth: (Int) -> Int) -> Rect,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    cornerFrom: Dp = 26.dp,
    cornerTo: Dp = 28.dp,
    surfaceAlphaTo: Float = 0.9f,
    blurTo: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val surface = MaterialTheme.colorScheme.surface
    Layout(
        content = {
            Box(
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = {
                        val p = progress().coerceIn(0f, 1f)
                        RoundedCornerShape(
                            with(density) { lerp(cornerFrom.toPx(), cornerTo.toPx(), p) }
                        )
                    },
                    effects = {
                        val p = progress().coerceIn(0f, 1f)
                        vibrancy()
                        blur(lerp(2f.dp.toPx(), blurTo.toPx(), p))
                        glassLens(
                            12f.dp.toPx() * (1f - p),
                            24f.dp.toPx() * (1f - p),
                            chromaticAberration = true,
                        )
                    },
                    onDrawSurface = {
                        val p = progress().coerceIn(0f, 1f)
                        drawRect(surface.copy(alpha = lerp(0.28f, surfaceAlphaTo, p)))
                    },
                ),
                contentAlignment = Alignment.TopStart,
                content = content,
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val glass = measurables.first()
        val overlay = IntSize(constraints.maxWidth, constraints.maxHeight)
        val p = progress() // may overshoot 1: that is the liquid part
        val a = from()
        val b = target(overlay) { width -> glass.maxIntrinsicHeight(width) }
        val w = lerp(a.width, b.width, p).roundToInt().coerceAtLeast(1)
        val h = lerp(a.height, b.height, p).roundToInt().coerceAtLeast(1)
        val x = lerp(a.left, b.left, p).roundToInt()
        val y = lerp(a.top, b.top, p).roundToInt()
        val placeable = glass.measure(Constraints.fixed(w, h))
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
 * inside the box whose [backdrop] holds the screen's content). The bubble of the anchor button
 * grows into a [menuWidth]-wide panel whose right edge lines up with the button's; [content] (the
 * items) fades in once the panel is mostly open. A tap anywhere else, or back, closes it.
 */
@Composable
fun LiquidMenuOverlay(
    state: LiquidMenuState,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    menuWidth: Dp = 236.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    LaunchedEffect(state.open) {
        state.progress.animateTo(if (state.open) 1f else 0f, spring(dampingRatio = 0.78f, stiffness = 380f))
    }
    BackHandler(enabled = state.open) { state.open = false }
    if (!state.open && state.progress.value <= 0.001f) return

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
                val height = heightForWidth(width)
                val a = state.anchor.translate(-origin)
                // Right edge on the button's right edge (the buttons that open menus sit at the end
                // of a bar), kept on screen.
                val left = (a.right - width).coerceIn(margin.toFloat(), (overlay.width - width - margin).toFloat())
                val top = a.top.coerceIn(margin.toFloat(), (overlay.height - height - margin).toFloat().coerceAtLeast(margin.toFloat()))
                Rect(left, top, left + width, top + height)
            },
            backdrop = backdrop,
        ) {
            Column(
                Modifier
                    .requiredWidth(menuWidth)
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
