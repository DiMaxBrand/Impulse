package eu.siacs.conversations.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** A rasterized glass surface that keeps RuntimeShader work off intervening display frames. */
@Stable
private class GlassSurfaceSample(val layer: GraphicsLayer, val intervalNanos: Long) {
    private var lastCaptureAtNanos: Long? = null
    private var captureInFlight = false
    private val captureRecorded = Channel<Unit>(Channel.CONFLATED)
    var image by mutableStateOf<ImageBitmap?>(null)
        private set

    fun shouldCapture(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0 || captureInFlight) return false
        val now = android.os.SystemClock.elapsedRealtimeNanos()
        val last = lastCaptureAtNanos
        if (image != null && last != null && now - last < intervalNanos) return false
        lastCaptureAtNanos = now
        captureInFlight = true
        return true
    }

    fun onCaptureRecorded() {
        captureRecorded.trySend(Unit)
    }

    suspend fun freezeCapturedFrames() {
        captureRecorded.receiveAsFlow().collect {
            try {
                // A GraphicsLayer alone retains RenderNodes and their live RuntimeShader effects.
                // Rasterizing each accepted draw is what prevents refraction from running again
                // just because the window presents another UI frame.
                image = layer.toImageBitmap()
            } catch (error: Exception) {
                android.util.Log.w("GlassSnapshot", "Could not freeze rendered glass surface", error)
            } finally {
                captureInFlight = false
            }
        }
    }
}

@Composable
fun Modifier.glassSnapshot(geometryKey: Any? = null): Modifier {
    val layer = rememberGraphicsLayer()
    val refreshRate = LocalView.current.display?.refreshRate ?: 60f
    val interval = glassSurfaceIntervalNanos(refreshRate, GlassTuning.samplingRate)
    val sample = remember(geometryKey, layer, interval) { GlassSurfaceSample(layer, interval) }
    LaunchedEffect(sample) { sample.freezeCapturedFrames() }
    return drawWithContent {
        if (sample.shouldCapture(size.width.toInt(), size.height.toInt())) {
            sample.layer.record(IntSize(size.width.toInt(), size.height.toInt())) {
                this@drawWithContent.drawContent()
            }
            sample.onCaptureRecorded()
        }
        val frozen = sample.image
        if (frozen != null) drawImage(frozen) else drawLayer(sample.layer)
    }
}
