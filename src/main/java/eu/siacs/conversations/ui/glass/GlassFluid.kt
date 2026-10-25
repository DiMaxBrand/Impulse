package eu.siacs.conversations.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.effect
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * PROTOTYPE: glass pieces that merge into one surface.
 *
 * The library's lens works on one rounded rectangle at a time, so two pieces side by side are two
 * separate glass bodies, and one dragged over the other is glass seen through glass. Here all the
 * pieces of a field are ONE shader pass: each is a rounded-rectangle distance, the field is their
 * smooth union (a polynomial smooth minimum, with its own blend radius per pair), and the screen
 * behind is refracted along the gradient of that one distance. So:
 *
 *  - a bridge is not a shape added between two others; it is simply where the union is smoothed;
 *  - the corner where one piece is dragged over another is rounded at any overlap, always;
 *  - nothing refracts anything else: it is one body.
 *
 * The bridge is not scrubbed by the finger. Each pair has a connected/disconnected state with
 * hysteresis (connect closer than [connectAt], let go only past [releaseAt]) and the blend radius
 * is a spring that goes up when connected and down to 0 when not, so it forms and tears by itself,
 * however slowly the finger moves.
 */

private const val SHAPE_MAX = 4

// 6 pairs of 4 pieces: 01 02 03 12 13 23
private fun pairIndex(i: Int, j: Int): Int = when (i * 4 + j) {
    1 -> 0; 2 -> 1; 3 -> 2; 6 -> 3; 7 -> 4; 11 -> 5
    else -> -1
}

private const val FLUID_SHADER = """
uniform shader content;
uniform float2 pad;
uniform float4 box[4];
uniform float rad[4];
uniform float k[6];
uniform float refractionHeight;
uniform float refractionAmount;
uniform float rimWidth;

float sdBox(float2 p, float4 b, float r) {
    float2 q = abs(p - b.xy) - (b.zw - float2(r));
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float smin(float a, float b, float kk) {
    if (kk <= 0.001) return min(a, b);
    float h = max(kk - abs(a - b), 0.0) / kk;
    return min(a, b) - h * h * kk * 0.25;
}

float field(float2 p) {
    float d0 = sdBox(p, box[0], rad[0]);
    float d1 = sdBox(p, box[1], rad[1]);
    float d2 = sdBox(p, box[2], rad[2]);
    float d3 = sdBox(p, box[3], rad[3]);
    float r = min(min(d0, d1), min(d2, d3));
    r = min(r, smin(d0, d1, k[0]));
    r = min(r, smin(d0, d2, k[1]));
    r = min(r, smin(d0, d3, k[2]));
    r = min(r, smin(d1, d2, k[3]));
    r = min(r, smin(d1, d3, k[4]));
    r = min(r, smin(d2, d3, k[5]));
    return r;
}

half4 main(float2 coord) {
    float2 p = coord - pad;
    float sd = field(p);
    if (sd > 1.0) return half4(0.0);
    float cover = clamp(0.5 - sd, 0.0, 1.0);
    float2 g = float2(
        field(p + float2(1.0, 0.0)) - field(p - float2(1.0, 0.0)),
        field(p + float2(0.0, 1.0)) - field(p - float2(0.0, 1.0)));
    float gl = length(g);
    float2 n = gl > 0.0001 ? g / gl : float2(0.0);
    float inner = min(sd, 0.0);
    float2 at = coord;
    if (-inner < refractionHeight) {
        float t = 1.0 - (-inner) / refractionHeight;
        float d = (1.0 - sqrt(1.0 - t * t)) * refractionAmount;
        at = coord + d * n;
    }
    half4 c = content.eval(at);
    float rim = smoothstep(rimWidth, 0.0, -inner);
    float light = dot(n, normalize(float2(-0.55, -0.8)));
    float spec = rim * (max(light, 0.0) * 0.8 + max(-light, 0.0) * 0.3);
    c.rgb += half3(spec * 0.55) * c.a;
    return c * cover;
}
"""

/** One member of a fluid field: its size and corner radius (px), and where it is (px, top-left). */
@Stable
class FluidPiece(val width: Float, val height: Float, val radius: Float, x: Float, y: Float) {
    var pos by androidx.compose.runtime.mutableStateOf(Offset(x, y))
    val centerX get() = pos.x + width / 2f
    val centerY get() = pos.y + height / 2f
}

/** The pieces and the springs of the bridges between them. */
@Stable
class GlassFluidState(val pieces: List<FluidPiece>) {
    private val connected = BooleanArray(6)
    private val blend = FloatArray(6)
    private val blendVelocity = FloatArray(6)
    var tick by mutableIntStateOf(0)
        private set

    var connectAt = 12f
    var releaseAt = 30f
    var softness = 16f

    fun blendFor(pair: Int) = blend[pair]

    fun positionsKey(): List<Offset> = pieces.map { it.pos }

    private fun gap(a: FluidPiece, b: FluidPiece): Float {
        fun sd(p: FluidPiece, x: Float, y: Float): Float {
            val qx = abs(x - p.centerX) - (p.width / 2f - p.radius)
            val qy = abs(y - p.centerY) - (p.height / 2f - p.radius)
            return hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - p.radius
        }
        val inA = min(a.width, a.height) / 2f
        val inB = min(b.width, b.height) / 2f
        return (sd(b, a.centerX, a.centerY) - inA + sd(a, b.centerX, b.centerY) - inB) / 2f
    }

    /** One physics step; true while anything is still moving. */
    fun step(dt: Float): Boolean {
        var moving = false
        for (i in pieces.indices) for (j in i + 1 until pieces.size) {
            val pair = pairIndex(i, j)
            if (pair < 0) continue
            val g = gap(pieces[i], pieces[j])
            if (!connected[pair] && g < connectAt) connected[pair] = true
            if (connected[pair] && g > releaseAt) connected[pair] = false
            val target = if (connected[pair]) max(softness, 2.6f * max(g, 0f) + softness * 0.5f) else 0f
            var left = dt
            while (left > 0f) {
                val h = min(left, 1f / 120f)
                blendVelocity[pair] += (STIFFNESS * (target - blend[pair]) - DAMPING * blendVelocity[pair]) * h
                blend[pair] = max(blend[pair] + blendVelocity[pair] * h, 0f)
                left -= h
            }
            if (abs(blend[pair] - target) > 0.05f || abs(blendVelocity[pair]) > 0.5f) moving = true
        }
        tick++
        return moving
    }

    /** How visible piece [i]'s own content is: it gives way while another piece's content is near. */
    fun contentAlpha(i: Int): Float {
        var alpha = 1f
        for (j in 0 until i) {
            val dist = hypot(pieces[i].centerX - pieces[j].centerX, pieces[i].centerY - pieces[j].centerY)
            val r = (min(pieces[i].width, pieces[i].height) + min(pieces[j].width, pieces[j].height)) * 0.5f * 0.55f
            alpha = min(alpha, ((dist - r) / (r * 0.8f)).coerceIn(0f, 1f))
        }
        return alpha
    }

    private companion object {
        const val STIFFNESS = 260f
        val DAMPING = 2f * 0.5f * sqrt(STIFFNESS)
    }
}

/**
 * The prototype field: draggable pieces over [backdrop]. [connectAt] / [releaseAt] are the gaps (dp)
 * at which a pair joins and lets go, [softness] (dp) how round the join is.
 */
@Composable
fun GlassFluidField(
    backdrop: Backdrop,
    connectAt: Float,
    releaseAt: Float,
    softness: Float,
    modifier: Modifier = Modifier,
    fieldHeight: androidx.compose.ui.unit.Dp = 300.dp,
    content: @Composable (index: Int, tint: androidx.compose.ui.graphics.Color) -> Unit,
) {
    val density = LocalDensity.current
    val state = remember {
        with(density) {
            GlassFluidState(
                listOf(
                    FluidPiece(64.dp.toPx(), 64.dp.toPx(), 32.dp.toPx(), 16.dp.toPx(), 24.dp.toPx()),
                    FluidPiece(116.dp.toPx(), 64.dp.toPx(), 32.dp.toPx(), 130.dp.toPx(), 120.dp.toPx()),
                    FluidPiece(64.dp.toPx(), 64.dp.toPx(), 20.dp.toPx(), 24.dp.toPx(), 210.dp.toPx()),
                ),
            )
        }
    }
    state.connectAt = with(density) { connectAt.dp.toPx() }
    state.releaseAt = with(density) { releaseAt.dp.toPx() }
    state.softness = with(density) { softness.dp.toPx() }
    var fieldSize by remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }

    // The springs run while anything moves (and restart when a piece does, or a setting changes).
    LaunchedEffect(state) {
        snapshotFlow { state.positionsKey() }.collectLatest {
            var last = 0L
            do {
                val frame = withFrameNanos { it }
                val dt = if (last == 0L) 1f / 60f else ((frame - last) / 1_000_000_000f).coerceAtMost(1f / 30f)
                last = frame
            } while (state.step(dt))
        }
    }
    LaunchedEffect(connectAt, releaseAt, softness) { state.step(1f / 60f) }

    val tint = MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .fillMaxWidth()
            .height(fieldHeight)
            .onSizeChanged { fieldSize = it },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawPlainBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = {
                        state.tick // follow the springs
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val shader: RuntimeShader = obtainRuntimeShader("GlassFluid", FLUID_SHADER)
                            val boxes = FloatArray(SHAPE_MAX * 4)
                            val radii = FloatArray(SHAPE_MAX)
                            for (i in 0 until SHAPE_MAX) {
                                val p = state.pieces.getOrNull(i)
                                if (p == null) {
                                    boxes[i * 4] = -100000f
                                    boxes[i * 4 + 1] = -100000f
                                } else {
                                    boxes[i * 4] = p.centerX
                                    boxes[i * 4 + 1] = p.centerY
                                    boxes[i * 4 + 2] = p.width / 2f
                                    boxes[i * 4 + 3] = p.height / 2f
                                    radii[i] = p.radius
                                }
                            }
                            shader.setFloatUniform("pad", padding, padding)
                            shader.setFloatUniform("box", boxes)
                            shader.setFloatUniform("rad", radii)
                            shader.setFloatUniform("k", FloatArray(6) { state.blendFor(it) })
                            shader.setFloatUniform("refractionHeight", 14.dp.toPx())
                            shader.setFloatUniform("refractionAmount", -26.dp.toPx())
                            shader.setFloatUniform("rimWidth", 2.5.dp.toPx())
                            effect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
                        }
                    },
                ),
        )
        state.pieces.forEachIndexed { index, piece ->
            Box(
                Modifier
                    .offset { IntOffset(piece.pos.x.roundToInt(), piece.pos.y.roundToInt()) }
                    .size(
                        with(density) { piece.width.toDp() },
                        with(density) { piece.height.toDp() },
                    )
                    .pointerInput(index, fieldSize) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            piece.pos = Offset(
                                (piece.pos.x + drag.x).coerceIn(0f, max(fieldSize.width - piece.width, 0f)),
                                (piece.pos.y + drag.y).coerceIn(0f, max(fieldSize.height - piece.height, 0f)),
                            )
                        }
                    }
                    .graphicsLayer { alpha = state.contentAlpha(index) },
                contentAlignment = Alignment.Center,
            ) {
                content(index, tint)
            }
        }
    }
}
