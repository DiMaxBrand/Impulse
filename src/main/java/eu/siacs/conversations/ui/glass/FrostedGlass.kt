package eu.siacs.conversations.ui.glass

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import kotlin.math.max
import kotlin.math.min

/*
 * Frosted glass that still refracts. A strong blur and a lens do not go together when the lens comes
 * after the blur: it bends a backdrop that has already lost everything sharp, so nothing is seen to
 * bend. So a frosted piece is two layers: the clear glass (light blur, the lens at the rim) over the
 * whole piece, and over it, inset by [LiquidGlassStyle.frostRim], the frosted layer (strong blur,
 * paler colours, lower contrast, a veil) for the middle. The rim band that is left is clear, and
 * bends what is behind it.
 */

/** The shape of the frosted layer: the piece's own corner, less the rim, so it follows the outline. */
private class InnerShape(private val outerCorner: (Size, Density) -> Float, private val rim: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rimPx = with(density) { rim.toPx() }
        val outer = outerCorner(Size(size.width + 2 * rimPx, size.height + 2 * rimPx), density)
        val corner = min(max(outer - rimPx, 0f), size.minDimension / 2f)
        return RoundedCornerShape(CornerSize(corner)).createOutline(size, layoutDirection, density)
    }
}

@Composable
internal fun BoxScope.FrostedInterior(
    backdrop: Backdrop,
    frost: LiquidGlassStyle,
    rim: Dp,
    outerCorner: (Size, Density) -> Float,
    // How much of the frost shows: 0..1, for a menu that is still a small bubble.
    visible: () -> Float = { 1f },
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
) {
    val surface = MaterialTheme.colorScheme.surface
    val shape = androidx.compose.runtime.remember(rim) { InnerShape(outerCorner, rim) }
    Box(
        Modifier
            .matchParentSize()
            .padding(rim)
            .graphicsLayer { alpha = visible() }
            .glassSnapshot()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    colorControls(0f, frost.contrast, frost.saturation)
                    glassBlur(frost.blur.toPx())
                },
                highlight = null,
                shadow = null,
                innerShadow = null,
                layerBlock = layerBlock,
                onDrawSurface = { drawRect(surface.copy(alpha = frost.surfaceAlpha)) },
            ),
    )
}
