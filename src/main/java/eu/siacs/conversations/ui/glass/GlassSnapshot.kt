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
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay

/** GPU-only cache for a rendered glass surface, refreshed by the selected cadence. */
@Stable
private class GlassSurfaceSample(val layer: GraphicsLayer, intervalMs: Long) {
    private var tick by mutableIntStateOf(0)
    private val captureGate = SampledCaptureGate(intervalMs)

    fun request() {
        captureGate.request()
        tick++
    }

    fun observe() {
        tick
    }

    fun shouldCapture(): Boolean = captureGate.consumeIfRequested(android.os.SystemClock.elapsedRealtime())
}

@Composable
fun Modifier.glassSnapshot(geometryKey: Any? = null): Modifier {
    val layer = rememberGraphicsLayer()
    val refreshRate = LocalView.current.display?.refreshRate ?: 60f
    val interval = sampledBackdropInterval(refreshRate, GlassTuning.samplingRate)
    val sample = remember(geometryKey, layer, interval) { GlassSurfaceSample(layer, interval) }
    LaunchedEffect(sample, interval) {
        while (true) {
            delay(interval)
            sample.request()
        }
    }
    return drawWithContent {
        sample.observe()
        if (sample.shouldCapture()) {
            sample.layer.record(IntSize(size.width.toInt(), size.height.toInt())) {
                this@drawWithContent.drawContent()
            }
        }
        drawLayer(sample.layer)
    }
}
