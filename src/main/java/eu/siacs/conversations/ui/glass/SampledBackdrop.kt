package eu.siacs.conversations.ui.glass

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop
import kotlin.math.ceil

/**
 * A screen-sized backdrop that stays on the GPU and is refreshed less often than the display.
 *
 * This intentionally caches only the source pixels. The glass nodes themselves still draw their
 * live shape, shadow, labels and controls every frame. In particular, never replace a glass node
 * with a bitmap: a moving node and a frozen bitmap are what produced the trails in beta.31.
 */
@Stable
class SampledBackdrop internal constructor(
    internal val layer: GraphicsLayer,
    private val intervalMs: Long,
) : Backdrop {
    private var coordinates: LayoutCoordinates? = null
    private var nextCaptureAt = 0L

    override val isCoordinatesDependent: Boolean = true

    internal fun updateCoordinates(value: LayoutCoordinates) {
        coordinates = value
    }

    internal fun shouldCapture(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        if (intervalMs == 0L || layer.size.width != width || layer.size.height != height) {
            nextCaptureAt = SystemClock.uptimeMillis() + intervalMs
            return true
        }
        val now = SystemClock.uptimeMillis()
        if (now < nextCaptureAt) return false
        nextCaptureAt = now + intervalMs
        return true
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val source = this@SampledBackdrop.coordinates ?: return
        val target = coordinates ?: return
        // The source is screen-sized. Its contents retain ordinary translation alignment while
        // the glass node itself applies its live transforms. The library's inverse-transform
        // helper is internal, so we deliberately do not reflect into a private API here.
        val offset = try {
            source.localPositionOf(target)
        } catch (_: Exception) {
            target.positionInWindow() - source.positionInWindow()
        }
        translate(-offset.x, -offset.y) { drawLayer(layer) }
    }
}

/**
 * 60–120 Hz displays refresh the sampled source at half rate; faster panels use two thirds.
 * Slower displays retain their full rate. Rounding up avoids accidentally exceeding the cap.
 */
private fun sampledBackdropInterval(refreshRate: Float): Long {
    val rate = refreshRate.takeIf { it.isFinite() && it > 0f } ?: return 0L
    val target = when {
        rate < 60f -> return 0L
        rate <= 120f -> rate / 2f
        else -> rate * (2f / 3f)
    }
    return ceil(1000.0 / target).toLong().coerceAtLeast(1L)
}

@Composable
fun rememberSampledBackdrop(): SampledBackdrop {
    val refreshRate = LocalView.current.display?.refreshRate ?: 60f
    val layer = rememberGraphicsLayer()
    val interval = sampledBackdropInterval(refreshRate)
    return remember(layer, interval) { SampledBackdrop(layer, interval) }
}

/** Records only the backing source on the selected cadence; its onscreen content is always live. */
fun Modifier.sampledBackdrop(backdrop: SampledBackdrop): Modifier =
    this
        .onGloballyPositioned { backdrop.updateCoordinates(it) }
        .drawWithContent {
            drawContent()
            if (backdrop.shouldCapture(size.width.toInt(), size.height.toInt())) {
                backdrop.layer.record(IntSize(size.width.toInt(), size.height.toInt())) {
                    this@drawWithContent.drawContent()
                }
            }
        }
