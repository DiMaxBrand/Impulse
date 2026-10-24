package eu.siacs.conversations.ui.glass

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
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
 * bend. So a frosted piece is two layers over the same area: the clear glass (light blur, the lens at
 * the rim) below, and the frosted layer (strong blur, paler colours, lower contrast, a veil) over it.
 * The frosted layer is not a smaller rectangle: it covers the whole piece, and fades out towards the
 * edge over [LiquidGlassStyle.frostRim], so there is no seam. At the very edge only the clear glass is
 * left, and bends what is behind it; the further in, the more frosted it is.
 */

private const val RAMP_STEPS = 12

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
    val shape = androidx.compose.runtime.remember(outerCorner) { OuterShape(outerCorner) }
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                alpha = visible()
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                // Fade the frost out towards the edge: rings, each taking away what it covers.
                val rimPx = rim.toPx()
                val corner = outerCorner(size, this)
                val ring = rimPx / RAMP_STEPS
                for (i in 0 until RAMP_STEPS) {
                    val t = (i + 0.5f) / RAMP_STEPS
                    val remaining = t * t * (3f - 2f * t) // smoothstep: 0 at the edge, 1 inside
                    val inset = ring * i + ring / 2f
                    val w = size.width - 2 * inset
                    val h = size.height - 2 * inset
                    if (w <= 0f || h <= 0f) break
                    val r = max(corner - inset, 0f)
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 1f - remaining),
                        topLeft = Offset(inset, inset),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(r, r),
                        style = Stroke(width = ring + 0.5f),
                        blendMode = BlendMode.DstOut,
                    )
                }
            }
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

/** The piece's own outline, so the frosted layer fills exactly the same shape. */
private class OuterShape(private val corner: (Size, Density) -> Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = min(corner(size, density), size.minDimension / 2f)
        return RoundedCornerShape(CornerSize(c)).createOutline(size, layoutDirection, density)
    }
}
