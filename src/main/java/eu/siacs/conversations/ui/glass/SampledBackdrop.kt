package eu.siacs.conversations.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay
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
    val intervalMs: Long,
) : Backdrop {
    private var coordinates: LayoutCoordinates? = null
    private var captureTick by mutableIntStateOf(0)
    private var capturePending = true
    private var hasCaptured = false

    override val isCoordinatesDependent: Boolean = true

    internal fun updateCoordinates(value: LayoutCoordinates) {
        coordinates = value
    }

    internal fun requestCaptureFrame() {
        capturePending = true
        captureTick++
    }

    internal fun observeCaptureCadence() {
        // Reading snapshot state in the draw phase invalidates just this source draw when the
        // cadence ticker advances. Without it, the cap is only consulted when unrelated UI work
        // happens to redraw the source, so motion can still appear to sample at display rate.
        captureTick
    }

    internal fun shouldCapture(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        // Auto on a sub-60 Hz panel uses every draw. Otherwise, once the initial source exists,
        // only a cadence tick may refresh it. Ordinary scroll/layout invalidations must not pull
        // the sample forward and silently turn a manual 1 fps cap into a higher rate.
        if (intervalMs == 0L) return true
        if (!hasCaptured || capturePending) {
            capturePending = false
            hasCaptured = true
            return true
        }
        return false
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
private fun sampledBackdropInterval(refreshRate: Float, requestedRate: Float): Long {
    val rate = refreshRate.takeIf { it.isFinite() && it > 0f } ?: return 0L
    val target = when {
        // Manual is a cap, never a request to sample more often than the panel presents.
        requestedRate > 0f -> requestedRate.coerceAtMost(rate)
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
    val interval = sampledBackdropInterval(refreshRate, GlassTuning.samplingRate)
    val backdrop = remember(layer, interval) { SampledBackdrop(layer, interval) }
    LaunchedEffect(backdrop, interval) {
        if (interval > 0L) {
            while (true) {
                delay(interval)
                backdrop.requestCaptureFrame()
            }
        }
    }
    return backdrop
}

/** Records only the backing source on the selected cadence; its onscreen content is always live. */
fun Modifier.sampledBackdrop(backdrop: SampledBackdrop): Modifier =
    this
        .onGloballyPositioned { backdrop.updateCoordinates(it) }
        .drawWithContent {
            backdrop.observeCaptureCadence()
            drawContent()
            if (backdrop.shouldCapture(size.width.toInt(), size.height.toInt())) {
                backdrop.layer.record(IntSize(size.width.toInt(), size.height.toInt())) {
                    this@drawWithContent.drawContent()
                }
            }
        }
