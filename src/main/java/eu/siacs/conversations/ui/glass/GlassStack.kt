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

    /**
     * When set, the buttons and panels that take this stack are elements of that fluid host (one body of glass
     * for the whole area, with bridges between elements that are close) instead of glass nodes of their own.
     */
    var fluid: FluidGlassHost? = null
        private set

    /** The same screen as this stack, whose elements are drawn by [host]. */
    fun withFluid(host: FluidGlassHost): GlassStack = GlassStack(base, adaptive).also { it.fluid = host }

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

/** A place for a piece that nothing is drawn over: it sees the screen only, and records nothing. */
@Composable
internal fun GlassStack.rememberStandaloneSlot(): GlassSlot {
    val stack = this
    val own = rememberLayerBackdrop()
    return remember(stack, own) { GlassSlot(stack.base, own) }
}

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
    /** 1 keeps the colours behind the glass as they are; less turns them paler (frosted glass). */
    val saturation: Float = 1f,
    /** 1 keeps the contrast of what is behind; less lets it fall back, so text behind stops competing. */
    val contrast: Float = 1f,
    /** A frosted layer over a clear piece (see FrostedInterior), fading out over [frostRim] at the edge, so the rim stays clear glass and refracts. */
    val frost: LiquidGlassStyle? = null,
    val frostRim: Dp = 16.dp,
) {
    companion object {
        /** Buttons and bubbles: clear glass with a strong rim. */
        val Button = LiquidGlassStyle()

        /** The chat's top bar: the demo's button glass. Its legibility comes from the brightness
         * adjustment (see rememberGlassInk), not from smearing the backdrop with blur. */
        val Bar = LiquidGlassStyle()

        /** Frosted glass: a strong, clean blur of the backdrop with paler colours and less contrast,
         * under a thin veil. The base of the panels and menus. */
        val Frosted = LiquidGlassStyle(
            blur = 18.dp,
            // A veil of the theme's surface colour: what is behind loses opacity (and so its edges).
            surfaceAlpha = 0.34f,
            chromaticAberration = false,
            vibrancy = false,
            saturation = 0.68f,
            contrast = 0.9f,
        )

        /** Panels that hold content (a text field, a banner): frosted in the middle, so what lies
         * behind cannot compete with the text on them. */
        val Panel = LiquidGlassStyle(
            chromaticAberration = false,
            frost = Frosted.copy(blur = 14.dp, surfaceAlpha = 0.28f),
            frostRim = 11.dp,
        )

        /** Menus: frosted in the middle (a clean blur with the colours pale and the contrast lowered,
         * under a veil, so lines of text behind do not show through) with a clear rim that refracts. */
        val Menu = LiquidGlassStyle(chromaticAberration = false, frost = Frosted)

        /** Rows of a settings screen turned to glass: a light blur, the lens, a thin veil for the text. */
        val Row = LiquidGlassStyle(
            blur = 10.dp,
            surfaceAlpha = 0.2f,
            lensHeight = 10.dp,
            lensAmount = 18.dp,
            chromaticAberration = false,
            vibrancy = false,
        )

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
