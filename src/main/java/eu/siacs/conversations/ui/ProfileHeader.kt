package eu.siacs.conversations.ui

import android.graphics.Bitmap
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The contact's picture as the head of the contact screen. Three states, with the scroll running between them:
 *   big     the picture fills the top of the screen, name over its lower edge;
 *   medium  it has drawn itself together into a circle with the name under it;
 *   hidden  the circle shrinks into the phone's camera hole (a drop of the picture runs into the hole and the picture
 *           is gone) and the name moves up into the top bar.
 * Pulling down at the top stretches the big picture like a jelly. In the glass style the picture has a glassy surface
 * (still crisp: nothing refracts it): a highlight that slides as the phone is tilted, and its dark counterpart on
 * the opposite side.
 */

internal class HeaderMetrics(
    val width: Float,
    val bigHeight: Float,
    /** Scroll distance at which the picture is a circle. */
    val toCircle: Float,
    /** Scroll distance at which it has gone into the camera. */
    val toCamera: Float,
    val circle: Float,
    val circleTop: Float,
    val dockX: Float,
    val dockY: Float,
    val dockSize: Float,
    val bigCorner: Float,
)

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun ease(t: Float): Float = t * t * (3f - 2f * t)

/** The phone's tilt as two numbers in -1..1 (sideways, forward/back), smoothed; zero when [enabled] is false. */
@Composable
internal fun rememberTilt(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(enabled) {
        val manager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!enabled || manager == null || sensor == null) {
            tilt.value = Offset.Zero
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    // Gravity in the screen's axes. Held upright it is (0, ~1); the numbers are the difference from that.
                    val x = (-e.values[0] / 9.81f).coerceIn(-1f, 1f)
                    val y = (e.values[1] / 9.81f - 0.75f).coerceIn(-1f, 1f)
                    val old = tilt.value
                    // A slow filter: the shine follows the hand, it does not tremble with it.
                    tilt.value = Offset(old.x + (x - old.x) * 0.12f, old.y + (y - old.y) * 0.12f)
                }

                override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { manager.unregisterListener(listener) }
        }
    }
    return tilt
}

/** The average colour of a picture (for the drop that runs into the camera hole). */
internal fun averageColor(bitmap: Bitmap?): Color {
    if (bitmap == null) return Color(0xFF455A64)
    return try {
        val one = Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
        Color(one).copy(alpha = 1f)
    } catch (_: Exception) {
        Color(0xFF455A64)
    }
}

@Composable
internal fun ProfileHeader(
    bitmap: ImageBitmap?,
    tint: Color,
    name: String,
    presence: String?,
    scroll: Float,
    pull: Float,
    metrics: HeaderMetrics,
    glass: Boolean,
    tilt: State<Offset>,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val p1 = ease(((scroll) / metrics.toCircle).coerceIn(0f, 1f))
    val p2 = ease(((scroll - metrics.toCircle) / (metrics.toCamera - metrics.toCircle)).coerceIn(0f, 1f))

    // Big -> circle: the box is drawn together.
    val circleL = (metrics.width - metrics.circle) / 2f
    val l1 = lerpF(0f, circleL, p1)
    val t1 = lerpF(0f, metrics.circleTop, p1)
    val r1 = lerpF(metrics.width, circleL + metrics.circle, p1)
    val b1 = lerpF(metrics.bigHeight + pull, metrics.circleTop + metrics.circle, p1)
    // Circle -> camera.
    val w1 = r1 - l1
    val h1 = b1 - t1
    val cx1 = (l1 + r1) / 2f
    val cy1 = (t1 + b1) / 2f
    val cx = lerpF(cx1, metrics.dockX, p2)
    val cy = lerpF(cy1, metrics.dockY, p2)
    val w = lerpF(w1, metrics.dockSize, p2)
    val h = lerpF(h1, metrics.dockSize, p2)
    val left = cx - w / 2f
    val top = cy - h / 2f
    val round = min(w, h) / 2f
    val topCorner = lerpF(0f, round, p1).coerceAtMost(round)
    val bottomCorner = lerpF(metrics.bigCorner, round, p1).coerceAtMost(round)
    // The picture goes out into the camera: it is only there until the last part of the way.
    val fade = (1f - ((p2 - 0.82f) / 0.18f).coerceIn(0f, 1f))
    val wDp = with(density) { w.toDp() }
    val hDp = with(density) { h.toDp() }

    // The drop between the circle and the camera hole: both are the picture's colour, blurred together and cut hard.
    if (p2 > 0.02f && p2 < 0.98f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val blurPx = with(density) { 9.dp.toPx() }
        val effect = remember(blurPx) {
            val threshold = AndroidColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 26f, -26f * 0.5f * 255f,
                ),
            )
            RenderEffect.createChainEffect(
                RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(threshold)),
                RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.DECAL),
            ).asComposeRenderEffect()
        }
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                renderEffect = effect
            },
        ) {
            drawCircle(tint, radius = min(w, h) / 2f * 1.05f, center = Offset(cx, cy))
            drawCircle(tint, radius = metrics.dockSize / 2f * (1.1f + 0.6f * (1f - p2)), center = Offset(metrics.dockX, metrics.dockY))
        }
    }

    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(wDp, hDp)
            .graphicsLayer { alpha = fade }
            .clip(RoundedCornerShape(topStart = with(density) { topCorner.toDp() }, topEnd = with(density) { topCorner.toDp() },
                bottomEnd = with(density) { bottomCorner.toDp() }, bottomStart = with(density) { bottomCorner.toDp() }))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Canvas(Modifier.fillMaxSize()) { drawRect(tint) }
        }
        if (glass) {
            GlassSurface(tilt, topCorner, bottomCorner)
        }
        // The name over the lower edge of the big picture, gone once it has become a circle.
        val nameAlpha = (1f - p1 * 2f).coerceIn(0f, 1f)
        if (nameAlpha > 0f) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = nameAlpha }) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        startY = size.height * 0.45f,
                        endY = size.height,
                    ),
                )
            }
            androidx.compose.foundation.layout.Column(
                Modifier.align(Alignment.BottomStart).padding(start = 22.dp, end = 22.dp, bottom = 20.dp)
                    .graphicsLayer { alpha = nameAlpha },
            ) {
                Text(name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (presence != null) Text(presence, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * The glassy surface of the picture: a highlight that slides over it as the phone tilts, the dark counterpart of that
 * highlight on the far side, and a rim that is bright where the light would catch. Nothing here bends the picture.
 */
@Composable
private fun GlassSurface(tilt: State<Offset>, topCorner: Float, bottomCorner: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val t = tilt.value
        val w = size.width
        val h = size.height
        val reach = max(w, h)
        // The shine: leans away from the side the phone dips to.
        val shine = Offset(w * (0.5f - t.x * 0.42f), h * (0.22f - t.y * 0.3f))
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.10f), Color.Transparent),
                center = shine,
                radius = reach * 0.75f,
            ),
        )
        // And the opposite: the same glass, darkened where the shine is not.
        val shade = Offset(w * (0.5f + t.x * 0.5f), h * (0.95f + t.y * 0.25f))
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent),
                center = shade,
                radius = reach * 0.8f,
            ),
        )
        // A soft wide band of light across the surface, at an angle that follows the tilt.
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.14f), Color.Transparent),
                start = Offset(w * (0.1f - t.x * 0.4f), 0f),
                end = Offset(w * (0.9f - t.x * 0.4f), h * (0.9f - t.y * 0.2f)),
            ),
        )
        // The rim.
        drawRoundRect(
            Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.45f)),
                start = Offset(w * (0f - t.x * 0.3f), 0f),
                end = Offset(w * (1f - t.x * 0.3f), h),
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(min(topCorner, bottomCorner).coerceAtLeast(1f)),
            style = Stroke(width = 1.6.dp.toPx()),
        )
    }
}

/** The name under the circle (the middle state), fading out as the circle goes to the camera. */
@Composable
internal fun BoxScopeName(
    name: String,
    presence: String?,
    scroll: Float,
    metrics: HeaderMetrics,
    modifier: Modifier = Modifier,
) {
    val p1 = ease((scroll / metrics.toCircle).coerceIn(0f, 1f))
    val p2 = ease(((scroll - metrics.toCircle) / (metrics.toCamera - metrics.toCircle)).coerceIn(0f, 1f))
    val alpha = ((p1 - 0.55f) / 0.45f).coerceIn(0f, 1f) * (1f - (p2 * 1.6f).coerceIn(0f, 1f))
    if (alpha <= 0f) return
    val density = LocalDensity.current
    val y = lerpF(metrics.circleTop + metrics.circle + with(density) { 14.dp.toPx() }, metrics.dockY, p2 * 0.3f)
    androidx.compose.foundation.layout.Column(
        modifier
            .offset { IntOffset(0, y.roundToInt()) }
            .graphicsLayer { this.alpha = alpha }
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
        if (presence != null) {
            Text(presence, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
