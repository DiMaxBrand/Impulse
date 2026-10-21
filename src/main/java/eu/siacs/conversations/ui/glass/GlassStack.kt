package eu.siacs.conversations.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.min

/*
 * Glass seeing glass. A glass piece normally samples only the screen behind it ([base]); another
 * glass piece dragged across it would be invisible to it, so the top one refracted the page and not
 * the one underneath. (The library's own demo app has the same limitation.)
 *
 * A [GlassStack] fixes that for all the pieces of one screen. Every piece takes a slot in
 * the order it was first composed, which is the order it is drawn in, so later means on top. It
 * records its finished look (glass, rim, shadow and what is drawn on it) into a layer of its own,
 * and the backdrop it samples is the screen plus the layers of the slots below it. A piece never
 * sees itself or anything above it. Where two pieces overlap, the upper one bends and blurs the lower
 * one: double refraction.
 */
@Stable
class GlassStack internal constructor(
    internal val base: Backdrop,
    // Whether each piece adapts its glass and text to what is behind it (see rememberGlassInk).
    val adaptive: Boolean = false,
) {
    private val layers = mutableStateListOf<LayerBackdrop?>()

    internal fun allocate(layer: LayerBackdrop): Int {
        layers.add(layer)
        return layers.lastIndex
    }

    internal fun release(index: Int) {
        if (index in layers.indices) layers[index] = null
        // Keep the list short: a menu that opens and closes a hundred times leaves no trail.
        while (layers.isNotEmpty() && layers.last() == null) layers.removeAt(layers.lastIndex)
    }

    /** The screen plus every slot below [index]. */
    internal fun below(index: Int): Backdrop =
        object : Backdrop {
            override val isCoordinatesDependent: Boolean = true

            override fun DrawScope.drawBackdrop(
                density: Density,
                coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?,
            ) {
                with(base) { drawBackdrop(density, coordinates, layerBlock) }
                for (i in 0 until min(index, layers.size)) {
                    val layer = layers[i] ?: continue
                    with(layer) { drawBackdrop(density, coordinates, layerBlock) }
                }
            }
        }
}

/** The stack for a screen whose content (what glass shows through itself) is recorded in [base]. */
@Composable
fun rememberGlassStack(base: Backdrop, adaptive: Boolean = false): GlassStack =
    remember(base, adaptive) { GlassStack(base, adaptive) }

/** A piece's place in a [GlassStack]: what it looks through, and where it records itself. */
@Immutable
class GlassSlot internal constructor(val backdrop: Backdrop, val record: LayerBackdrop)

@Composable
internal fun GlassStack.rememberSlot(): GlassSlot {
    val stack = this
    val own = rememberLayerBackdrop()
    val index = remember(stack, own) { stack.allocate(own) }
    DisposableEffect(stack, index) { onDispose { stack.release(index) } }
    return remember(stack, index) { GlassSlot(stack.below(index), own) }
}

/**
 * How one glass piece looks. The options are the ones the library offers per piece, so a menu can
 * differ from a button without any of the code differing. The glass is only the backdrop (blurred
 * and bent); there is no tint layer over it by default:
 *
 *  - [blur]: how much the screen behind is blurred.
 *  - [surfaceAlpha]: the opacity of the surface colour laid over the glass. More opacity, and the
 *    content behind shows less clearly.
 *  - [lensHeight] / [lensAmount]: how wide a band along the rim bends the light, and how strongly;
 *    [chromaticAberration] splits the colours in it.
 *  - [vibrancy]: more saturated colours behind the glass.
 */
@Immutable
data class LiquidGlassStyle(
    val blur: Dp = 2.dp,
    val surfaceAlpha: Float = 0f,
    val lensHeight: Dp = 12.dp,
    val lensAmount: Dp = 24.dp,
    val chromaticAberration: Boolean = true,
    val vibrancy: Boolean = true,
) {
    companion object {
        /** Buttons and bubbles: clear glass with a strong rim. */
        val Button = LiquidGlassStyle()

        /** The chat's top bar: the demo's button glass. Its legibility comes from the brightness
         * adjustment (see rememberGlassInk), not from smearing the backdrop with blur. */
        val Bar = LiquidGlassStyle()

        /** Panels that hold content (a text field, a banner): the demo's glass without the colour
         * fringes. */
        val Panel = LiquidGlassStyle(chromaticAberration = false)

        /** Menus: the demo's glass as well; the menu's size, not its blur, is what sets it apart. */
        val Menu = LiquidGlassStyle(chromaticAberration = false)

        /** Large surfaces that cover most of the screen: nearly solid, and cheap (a small blur and
         * the plain lens: both cost in proportion to the area). */
        val Sheet = LiquidGlassStyle(
            blur = 8.dp,
            surfaceAlpha = 0.96f,
            lensHeight = 12.dp,
            lensAmount = 24.dp,
            chromaticAberration = false,
        )
    }
}

/** The colour of the "surface" layer for a style, from the theme. */
@Composable
fun LiquidGlassStyle.surfaceColor(): Color =
    androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha)
