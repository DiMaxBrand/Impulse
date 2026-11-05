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
import androidx.compose.ui.graphics.drawscope.withTransform
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import eu.siacs.conversations.ui.glass.FluidBridges
import eu.siacs.conversations.ui.glass.FluidShape
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

/** The same cubic smooth union as FluidGlass.kt; no separately drawn connecting strand. */
private const val CAMERA_BLOB_SRC = """
uniform float2 cam;
uniform float camR;
uniform float2 av;
uniform float2 avR;
uniform float soft;

float sdBody(float2 p, float2 halfSize) {
    float r = min(halfSize.x, halfSize.y);
    float2 q = abs(p) - (halfSize - float2(r));
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
float smin(float a, float b, float k) {
    if (k <= 0.001) return min(a, b);
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * h * k / 6.0;
}
half4 main(float2 p) {
    float dCam = length(p - cam) - camR;
    float dAv = sdBody(p - av, avR);
    float d = smin(dCam, dAv, soft);
    return half4(0.0, 0.0, 0.0, clamp(0.5 - d, 0.0, 1.0));
}
"""

internal data class CameraMergePose(val x: Float, val y: Float, val width: Float, val height: Float)

internal fun cameraMergePose(
    metrics: HeaderMetrics, progress: Float, x: Float, y: Float, width: Float, height: Float,
): CameraMergePose {
    val p = progress.coerceIn(0f, 1f)
    val radius = if (metrics.hasCamera) metrics.camR.coerceAtLeast(0.5f) else 0.5f
    val stretch = 1f + 0.22f * sin(p * Math.PI.toFloat())
    return CameraMergePose(
        lerpF(x, if (metrics.hasCamera) metrics.camX else metrics.width / 2f, p),
        lerpF(y, if (metrics.hasCamera) metrics.camY else 0f, p),
        lerpF(width, radius * 2f, p) / sqrt(stretch),
        lerpF(height, radius * 2f, p) * stretch,
    )
}

private fun ease(t: Float): Float = t * t * (3f - 2f * t)

internal data class CameraAbsorption(val blur: Float, val topBlack: Float, val bottomBlack: Float)

/** A reversible swell, with zero size/opacity velocity at either resting state. */
internal fun cameraHaloProgress(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    val swell = 4f * p * (1f - p)
    return swell * swell
}

/** Darken into opaque black before the photo fits inside the cutout; never fade into the page. */
internal fun cameraAbsorption(progress: Float, gap: Float, reach: Float): CameraAbsorption {
    val p = progress.coerceIn(0f, 1f)
    val proximity = ease((1f - gap.coerceAtLeast(0f) / reach.coerceAtLeast(1f)).coerceIn(0f, 1f)) *
        ease((p / 0.2f).coerceIn(0f, 1f))
    val black = ease(((p - 0.55f) / 0.35f).coerceIn(0f, 1f))
    return CameraAbsorption(max(proximity, black), max(proximity, black), black)
}

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
    // One scroll-driven geometry for both the photo and its black body. A separate spring
    // here lagged behind the expanding header when reversing direction.
    val p2 = p2raw

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

    // End inside the actual cutout, not as a second dot underneath it.
    val pose = cameraMergePose(metrics, p2, cx1, cy1, w1, h1)
    val cx = pose.x
    val cy = pose.y
    val w = pose.width
    val h = pose.height
    val left = cx - w / 2f
    val top = cy - h / 2f
    val round = min(w, h) / 2f
    val topCorner = lerpF(0f, round, p1).coerceAtMost(round)
    val bottomCorner = lerpF(metrics.bigCorner, round, p1).coerceAtMost(round)
    val absorption = cameraAbsorption(p2, top - (metrics.camY + metrics.camR),
        with(density) { 32.dp.toPx() })
    val photoBlurPx = with(density) { 24.dp.toPx() } * absorption.blur
    val photoBlur = remember(photoBlurPx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && photoBlurPx > 0.1f)
            RenderEffect.createBlurEffect(photoBlurPx, photoBlurPx, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        else null
    }
    val wDp = with(density) { w.toDp() }
    val hDp = with(density) { h.toDp() }

    // Black, never the picture's colour (the camera cannot change colour): the hole reaches down to the picture with a
    // neck, and the picture draws itself into it.
    if (metrics.hasCamera && p2 > 0.01f) {
        val bridges = remember(density.density) {
            FluidBridges().apply {
                reach = with(density) { 32.dp.toPx() }
                softness = with(density) { 12.dp.toPx() }
                response = 0.4f
            }
        }
        val cameraShape = remember { FluidShape(0) }
        val avatarShape = remember { FluidShape(1) }
        val currentPose by rememberUpdatedState(pose)
        var blend by remember { mutableFloatStateOf(0f) }
        if (p2 < 1f) LaunchedEffect(bridges) {
            var previous = 0L
            while (true) {
                val now = withFrameNanos { it }
                val dt = if (previous == 0L) 1f / 60f else
                    ((now - previous) / 1_000_000_000f).coerceIn(0f, 1f / 30f)
                previous = now
                cameraShape.apply {
                    this.cx = metrics.camX; this.cy = metrics.camY
                    hw = metrics.camR; hh = metrics.camR; radius = metrics.camR
                }
                avatarShape.apply {
                    this.cx = currentPose.x; this.cy = currentPose.y
                    hw = currentPose.width / 2f; hh = currentPose.height / 2f
                    radius = min(hw, hh)
                }
                bridges.step(dt, listOf(cameraShape, avatarShape), false)
                blend = bridges.blend(cameraShape, avatarShape)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The same kind of merging as the playground's glass: the two shapes are distance fields, joined with a smooth
            // minimum. Only nearby shapes join; there is no line drawn across an empty gap.
            val shader = remember { RuntimeShader(CAMERA_BLOB_SRC) }
            val soft = with(density) { 11.dp.toPx() }
            Canvas(Modifier.fillMaxSize()) {
                shader.setFloatUniform("cam", metrics.camX, metrics.camY)
                shader.setFloatUniform("camR", metrics.camR)
                shader.setFloatUniform("av", cx, cy)
                shader.setFloatUniform("avR", w / 2f, h / 2f)
                // Identical to the playground: attraction starts only within reach,
                // broadens at both bodies and narrows naturally at the middle.
                shader.setFloatUniform("soft", blend * ease(((1f - p2) / 0.12f).coerceIn(0f, 1f)))
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
                val rx = w / 2f
                val ry = h / 2f
                drawRoundRect(Color.Black, topLeft = Offset(cx - rx, cy - ry),
                    size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(min(rx, ry)))
            }
        }
    }

    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(wDp, hDp)
            .graphicsLayer { alpha = if (p2 < 1f) 1f else 0f }
            .clip(RoundedCornerShape(topStart = with(density) { topCorner.toDp() }, topEnd = with(density) { topCorner.toDp() },
                bottomEnd = with(density) { bottomCorner.toDp() }, bottomStart = with(density) { bottomCorner.toDp() }))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
    ) {
        // Blur only the photo, before its dark gradient. The cutout and silhouette stay sharp.
        Box(Modifier.fillMaxSize().graphicsLayer { renderEffect = photoBlur }) {
            Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black) }
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Canvas(Modifier.fillMaxSize()) { drawRect(tint) }
            }
        }
        // The glassy surface belongs to the small picture: the big one stays plain and crisp.
        if (glass && p1 > 0.02f) {
            GlassSurface(tilt, topCorner, bottomCorner, strength = p1 * (1f - absorption.bottomBlack))
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(
                0f to Color.Black.copy(alpha = absorption.topBlack),
                0.35f to Color.Black.copy(alpha = lerpF(absorption.topBlack, absorption.bottomBlack, 0.18f)),
                0.75f to Color.Black.copy(alpha = lerpF(absorption.topBlack, absorption.bottomBlack, 0.8f)),
                1f to Color.Black.copy(alpha = absorption.bottomBlack),
            ))
        }
    }
    // Only the outer shadow swells and softens. Its radial falloff approximates a broad blur
    // without blurring the real cutout or allocating a screen-sized blur layer.
    val halo = cameraHaloProgress(p2)
    if (glass && metrics.hasCamera && halo > 0.001f) Canvas(Modifier.fillMaxSize()) {
        val camera = Offset(metrics.camX, metrics.camY)
        val radius = metrics.camR + 36.dp.toPx() * halo
        val inner = (metrics.camR / radius).coerceIn(0f, 0.99f)
        val direction = Math.toDegrees(kotlin.math.atan2(
            (cy - metrics.camY).toDouble(), (cx - metrics.camX).toDouble(),
        )).toFloat() - 90f
        withTransform({
            rotate(direction, pivot = camera)
            scale(1f, 1f + 0.45f * halo, pivot = camera)
        }) {
            drawCircle(Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.58f * halo),
                inner to Color.Black.copy(alpha = 0.5f * halo),
                (inner + (1f - inner) * 0.3f) to Color.Black.copy(alpha = 0.28f * halo),
                (inner + (1f - inner) * 0.65f) to Color.Black.copy(alpha = 0.07f * halo),
                1f to Color.Transparent,
                center = camera, radius = radius,
            ), radius, camera)
        }
    }
    // Last, separate and always opaque: neither the photo blur nor its gradient can tint the lens.
    if (metrics.hasCamera) Canvas(Modifier.fillMaxSize()) {
        drawCircle(Color.Black, metrics.camR, Offset(metrics.camX, metrics.camY))
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
