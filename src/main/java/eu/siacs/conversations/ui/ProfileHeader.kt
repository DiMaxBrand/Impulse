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
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.graphics.ShaderBrush
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Path
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
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
    /** The camera hole: its centre and radius (px); [hasCamera] is false when the screen has no cut-out. */
    val camX: Float,
    val camY: Float,
    val camR: Float,
    val hasCamera: Boolean,
    val bigCorner: Float,
)

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * The camera hole and the picture's silhouette as two distance fields joined with a polynomial smooth minimum (the
 * join rounds off by [soft] pixels), plus a tapering strand from the hole towards the picture. Black, hard-edged.
 */
private const val CAMERA_BLOB_SRC = """
uniform float2 cam;
uniform float camR;
uniform float2 av;
uniform float2 avR;
uniform float soft;
uniform float strand;

float sdEllipse(float2 p, float2 r) {
    float2 q = p / max(r, float2(0.5));
    return (length(q) - 1.0) * min(r.x, r.y);
}

float sdStrand(float2 p, float2 a, float2 b, float ra, float rb) {
    float2 pa = p - a;
    float2 ba = b - a;
    float t = clamp(dot(pa, ba) / max(dot(ba, ba), 1.0), 0.0, 1.0);
    return length(pa - ba * t) - mix(ra, rb, t);
}

float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / max(k, 0.001), 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

half4 main(float2 p) {
    float dCam = length(p - cam) - camR;
    float dAv = sdEllipse(p - av, avR);
    float d = smin(dCam, dAv, soft);
    if (strand > 0.5) {
        // From the hole to the top of the picture, thicker at the hole.
        float2 top = av - float2(0.0, avR.y * 0.8);
        d = smin(d, sdStrand(p, cam, top, strand, strand * 0.25), soft);
    }
    float a = clamp(0.5 - d, 0.0, 1.0);
    return half4(0.0, 0.0, 0.0, a);
}
"""
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
    scroll: Float,
    pull: Float,
    metrics: HeaderMetrics,
    glass: Boolean,
    tilt: State<Offset>,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val p1 = ease(((scroll) / metrics.toCircle).coerceIn(0f, 1f))
    val p2raw = ease(((scroll - metrics.toCircle) / (metrics.toCamera - metrics.toCircle)).coerceIn(0f, 1f))
    // The way into the camera trails the finger a little and overshoots when thrown: a drop, not a slider.
    val p2 by animateFloatAsState(p2raw, spring(dampingRatio = 0.72f, stiffness = 330f), label = "intoCamera")

    // Big -> circle: the box is drawn together.
    val circleL = (metrics.width - metrics.circle) / 2f
    val l1 = lerpF(0f, circleL, p1)
    val t1 = lerpF(0f, metrics.circleTop, p1)
    val r1 = lerpF(metrics.width, circleL + metrics.circle, p1)
    val b1 = lerpF(metrics.bigHeight + pull, metrics.circleTop + metrics.circle, p1)
    val w1 = r1 - l1
    val h1 = b1 - t1
    val cx1 = (l1 + r1) / 2f
    val cy1 = (t1 + b1) / 2f

    // Circle -> camera: it climbs to just under the hole and shrinks to its size; on the way it is stretched upward.
    val endR = max(metrics.camR * 1.15f, with(density) { 7.dp.toPx() })
    val endX = if (metrics.hasCamera) metrics.camX else metrics.width / 2f
    val endY = if (metrics.hasCamera) metrics.camY + metrics.camR * 0.9f + endR else metrics.circleTop * 0.5f
    val stretch = 1f + 0.22f * sin(p2 * Math.PI.toFloat())
    val cx = lerpF(cx1, endX, p2)
    val cy = lerpF(cy1, endY, p2)
    val w = lerpF(w1, endR * 2f, p2) / sqrt(stretch)
    val h = lerpF(h1, endR * 2f, p2) * stretch
    val left = cx - w / 2f
    val top = cy - h / 2f
    val round = min(w, h) / 2f
    val topCorner = lerpF(0f, round, p1).coerceAtMost(round)
    val bottomCorner = lerpF(metrics.bigCorner, round, p1).coerceAtMost(round)
    // The picture dissolves into the black of the camera over the last of the way.
    val photoAlpha = 1f - ((p2 - 0.7f) / 0.3f).coerceIn(0f, 1f)
    val wDp = with(density) { w.toDp() }
    val hDp = with(density) { h.toDp() }

    // Black, never the picture's colour (the camera cannot change colour): the hole reaches down to the picture with a
    // neck, and the picture draws itself into it.
    if (metrics.hasCamera && p2 > 0.01f) {
        val silhouette = (1f - ((p2 - 0.7f) / 0.3f).coerceIn(0f, 1f) * 0.9f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The same kind of merging as the playground's glass: the two shapes are distance fields, joined with a smooth
            // minimum, so the join is exact at any size (and costs one small shader), with a strand that runs from the hole
            // to the picture while they are apart.
            val shader = remember { RuntimeShader(CAMERA_BLOB_SRC) }
            val soft = with(density) { 11.dp.toPx() }
            Canvas(Modifier.fillMaxSize()) {
                shader.setFloatUniform("cam", metrics.camX, metrics.camY)
                shader.setFloatUniform("camR", metrics.camR)
                shader.setFloatUniform("av", cx, cy)
                shader.setFloatUniform("avR", w / 2f * silhouette, h / 2f * silhouette)
                shader.setFloatUniform("soft", soft * (0.35f + 0.65f * p2))
                // The strand is there while the picture is on its way up, and gone when it has arrived.
                shader.setFloatUniform("strand", (sin(p2 * Math.PI.toFloat()).coerceIn(0f, 1f)) * 0.55f * metrics.camR)
                drawRect(
                    ShaderBrush(shader),
                    topLeft = Offset.Zero,
                    size = androidx.compose.ui.geometry.Size(size.width, min(size.height, metrics.circleTop + metrics.circle + soft * 4f)),
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Older phones: the same thing by blurring and cutting hard.
            val blurPx = with(density) { 7.dp.toPx() }
            val effect = remember(blurPx) {
                val threshold = AndroidColorMatrix(
                    floatArrayOf(
                        0f, 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 30f, -30f * 0.5f * 255f,
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
                val camera = Offset(metrics.camX, metrics.camY)
                drawCircle(Color.Black, radius = metrics.camR, center = camera)
                val rx = w / 2f * silhouette
                val ry = h / 2f * silhouette
                drawOval(Color.Black, topLeft = Offset(cx - rx, cy - ry), size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f))
            }
        }
    }

    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(wDp, hDp)
            .graphicsLayer { alpha = photoAlpha }
            .clip(RoundedCornerShape(topStart = with(density) { topCorner.toDp() }, topEnd = with(density) { topCorner.toDp() },
                bottomEnd = with(density) { bottomCorner.toDp() }, bottomStart = with(density) { bottomCorner.toDp() }))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Canvas(Modifier.fillMaxSize()) { drawRect(tint) }
        }
        // The glassy surface belongs to the small picture: the big one stays plain and crisp.
        if (glass && p1 > 0.02f) {
            GlassSurface(tilt, topCorner, bottomCorner, strength = p1)
        }
    }
}

/**
 * The glassy surface of the picture: a highlight that slides over it as the phone tilts, the dark counterpart of that
 * highlight on the far side, and a rim that is bright where the light would catch. Nothing here bends the picture.
 */
@Composable
private fun GlassSurface(tilt: State<Offset>, topCorner: Float, bottomCorner: Float, strength: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val t = tilt.value
        val w = size.width
        val h = size.height
        val reach = max(w, h)
        // The shine: leans away from the side the phone dips to. Kept small and soft: the picture must stay clear.
        val shine = Offset(w * (0.5f - t.x * 0.4f), h * (0.25f - t.y * 0.28f))
        drawCircle(
            Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.26f * strength), Color.Transparent),
                center = shine,
                radius = reach * 0.42f,
            ),
            radius = reach * 0.42f,
            center = shine,
        )
        // And the opposite: the same glass, darkened where the shine is not.
        val shade = Offset(w * (0.5f + t.x * 0.45f), h * (0.92f + t.y * 0.2f))
        drawCircle(
            Brush.radialGradient(
                colors = listOf(Color.Black.copy(alpha = 0.16f * strength), Color.Transparent),
                center = shade,
                radius = reach * 0.5f,
            ),
            radius = reach * 0.5f,
            center = shade,
        )
        // The rim, bright on the side the light catches.
        drawRoundRect(
            Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.65f * strength), Color.White.copy(alpha = 0.04f * strength), Color.White.copy(alpha = 0.35f * strength)),
                start = Offset(w * (0f - t.x * 0.3f), 0f),
                end = Offset(w * (1f - t.x * 0.3f), h),
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(min(topCorner, bottomCorner).coerceAtLeast(1f)),
            style = Stroke(width = 1.4.dp.toPx()),
        )
    }
}
