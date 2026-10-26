package eu.siacs.conversations.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.effect
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 * Fluid glass: all the glass elements of one area (a bar, a row of buttons) drawn as ONE glass body.
 *
 * Every element is a rounded rectangle (it may be turned); the glass is the smooth union of them,
 * with a bridge between two elements that are close, and the backdrop is refracted along the one
 * outline. So elements side by side are on the same level (nothing is glass seen through glass), and
 * they flow into each other as they get close.
 *
 * Clear and frosted elements live in the same layer: the sharp glass is drawn for the whole union,
 * and where an element is frosted (not at its rim) a second, blurred and veiled copy of the same
 * shape is drawn over it, so a frosted element and a clear one next to it can still be bridged.
 *
 * The same layer draws the lab's field (GlassFluid.kt) and the real bars (FluidGlassHost).
 */

/** One shape of a fluid glass layer, in the layer's own pixels. */
@Stable
class FluidShape(val id: Int) {
    var cx = 0f
    var cy = 0f
    var hw = 0f
    var hh = 0f
    var radius = 0f

    /** Radians, clockwise on screen. */
    var angle = 0f

    /** 0 clear glass .. 1 frosted (blurred and veiled) in the middle. */
    var frost = 0f

    /** How far in from the edge the frost has faded out: the rim stays clear glass. */
    var frostRim = 0f

    /** Added to the glass colour here (the glass brightens over a dark backdrop and darkens over a bright one). */
    var brightness = 0f

    /** 0..1: how hard the element is pressed; [touchX] and [touchY] is where the finger is (layer pixels). */
    var press = 0f
    var touchX = 0f
    var touchY = 0f

    /** A colour laid over the glass inside this shape: red, green, blue and how strongly (0: none). */
    var tintR = 0f
    var tintG = 0f
    var tintB = 0f
    var tintA = 0f

    /** Radius of a circle that holds the shape. */
    val bound get() = hypot(hw, hh) + 2f
}

/** What a fluid glass layer draws, and how it moves. */
@Stable
interface FluidScene {
    /** Changes whenever the scene did; read while drawing so the layer follows. */
    val tick: Int

    /** True while some element is frosted (the second pass is only drawn then). */
    val frostActive: Boolean

    /** The shapes as they are right now (reads whatever states drive them, so reading it while drawing follows them). */
    fun shapes(): List<FluidShape>

    /** The blend radius of the bridge between two shapes (0: separate). */
    fun bridgeBlend(a: FluidShape, b: FluidShape): Float

    /**
     * The area the shapes fill (the layer is laid out to just cover it, plus a margin, so a menu can grow far beyond the
     * bar it came from). Null: the layer fills the area it is given.
     */
    fun bounds(): androidx.compose.ui.geometry.Rect? = null

    /**
     * Called while the sharp glass draws: a chance to record what is behind each element (for the glass and the
     * text on it to adapt to a bright or a dark backdrop). [draw] draws the backdrop of the whole layer.
     */
    fun sampleInks(scope: androidx.compose.ui.graphics.drawscope.DrawScope, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {}
}

/** Signed distance from a point to the shape's body: negative inside. */
internal fun shapeSd(s: FluidShape, x: Float, y: Float): Float {
    val dx = x - s.cx
    val dy = y - s.cy
    val c = kotlin.math.cos(s.angle)
    val sn = kotlin.math.sin(s.angle)
    val lx = c * dx + sn * dy
    val ly = -sn * dx + c * dy
    val qx = abs(lx) - (s.hw - s.radius)
    val qy = abs(ly) - (s.hh - s.radius)
    return hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - s.radius
}

/**
 * The bridges between shapes: for every pair that is close, how deeply the two have pulled towards
 * each other. Driven by the gap (zero at the reach, full when touching), eased by a critically damped
 * spring so it pours and never overshoots, and faded in and out so it never snaps. Pairs are known
 * by the ids of their shapes, so shapes can come and go.
 */
class FluidBridges {
    private class State(var depth: Float = 0f, var velocity: Float = 0f, var gap: Float = 1e6f)

    private val pairs = HashMap<Long, State>()

    /** Pieces start reaching for each other from this gap (px). */
    var reach = 28f

    /** How deep a full merge is: the roundness of the join (px). */
    var softness = 16f

    /** Seconds for the blend to settle. */
    var response = 0.65f

    private fun key(a: Int, b: Int): Long = (min(a, b).toLong() shl 32) or (max(a, b).toLong() and 0xffffffffL)

    private fun smooth(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private val near = FloatArray(2)

    /** The point of the shape's outline nearest to (x, y), in the layer's frame. */
    private fun closest(s: FluidShape, x: Float, y: Float) {
        val dx = x - s.cx
        val dy = y - s.cy
        val c = kotlin.math.cos(s.angle)
        val sn = kotlin.math.sin(s.angle)
        val lx = c * dx + sn * dy
        val ly = -sn * dx + c * dy
        val ix = max(s.hw - s.radius, 0f)
        val iy = max(s.hh - s.radius, 0f)
        val qx = lx.coerceIn(-ix, ix)
        val qy = ly.coerceIn(-iy, iy)
        val ex = lx - qx
        val ey = ly - qy
        val len = hypot(ex, ey)
        val px = if (len > 1e-4f) qx + ex / len * s.radius else qx
        val py = if (len > 1e-4f) qy + ey / len * s.radius else qy
        near[0] = s.cx + c * px - sn * py
        near[1] = s.cy + sn * px + c * py
    }

    /**
     * The gap between two shapes: the distance between their nearest points (negative when they overlap).
     * Found by bouncing between them a few times, so it is right for a wide pill beside a round button too
     * (measuring from the centres made every wide shape look far away until it was on top of its neighbour).
     */
    private fun gapOf(a: FluidShape, b: FluidShape): Float {
        var ax = a.cx
        var ay = a.cy
        var bx = b.cx
        var by = b.cy
        repeat(3) {
            closest(b, ax, ay)
            bx = near[0]; by = near[1]
            closest(a, bx, by)
            ax = near[0]; ay = near[1]
        }
        val intoB = shapeSd(b, ax, ay)
        val intoA = shapeSd(a, bx, by)
        return if (intoB < 0f || intoA < 0f) min(intoB, intoA) else hypot(ax - bx, ay - by)
    }

    fun forget(id: Int) {
        pairs.keys.removeAll { (it shr 32).toInt() == id || (it and 0xffffffffL).toInt() == id }
    }

    fun clear() = pairs.clear()

    /** One step; true while any bridge is still moving. [off]: no bridges at all. */
    fun step(dt: Float, shapes: List<FluidShape>, off: Boolean): Boolean {
        var moving = false
        val omega = 5.8f / response.coerceAtLeast(0.2f)
        val stiffness = omega * omega
        val damping = 2f * omega
        val live = HashSet<Long>()
        for (i in shapes.indices) for (j in i + 1 until shapes.size) {
            val a = shapes[i]
            val b = shapes[j]
            val key = key(a.id, b.id)
            var st = pairs[key]
            if (st == null) {
                // Far apart and no bridge: nothing to track.
                val dist = hypot(a.cx - b.cx, a.cy - b.cy)
                if (off || dist > a.bound + b.bound + reach + 8f) continue
                st = State()
                pairs[key] = st
            }
            live += key
            val g = gapOf(a, b)
            st.gap = g
            val target = if (off) 0f else 3f * softness * smooth(1f - g / reach.coerceAtLeast(1f))
            var left = dt
            while (left > 0f) {
                val h = min(left, 1f / 120f)
                st.velocity += (stiffness * (target - st.depth) - damping * st.velocity) * h
                st.depth = max(st.depth + st.velocity * h, 0f)
                left -= h
            }
            if (abs(st.depth - target) > 0.05f || abs(st.velocity) > 0.5f) moving = true
        }
        // Pairs that are gone (a shape left) or fully settled at nothing need no state.
        pairs.entries.removeAll { (k, s) -> k !in live || (s.depth < 0.01f && abs(s.velocity) < 0.01f && s.gap > reach) }
        return moving
    }

    /** The smoothing radius of the bridge between two shapes in the shader (0: none). */
    fun blend(a: FluidShape, b: FluidShape): Float {
        val st = pairs[key(a.id, b.id)] ?: return 0f
        val c = st.depth
        // Fades in and out with the depth instead of switching at some depth: a bridge that is
        // nearly gone is nearly not there, so it never snaps off.
        val fade = smooth((c - 0.5f) / 7.5f)
        if (fade <= 0f) return 0f
        // A radius of three times the gap is where the surfaces just touch (cubic blend: the middle
        // sinks by a sixth of the radius); the depth is on top of that. Never longer than what tears
        // at the reach: however far they were pulled in one go, a bridge cannot outlive that.
        val cap = max(3f * reach * 0.98f, c)
        return min(3f * max(st.gap, 0f) + c, cap) * fade
    }
}

/** How a fluid glass layer looks (everything here is the same for all its shapes). */
class FluidLook(
    val refractionHeight: Dp = 12.dp,
    val refractionAmount: Dp = 24.dp,
    val fringes: Boolean = false,
    val blur: Dp = 0.dp,
    /** 1 keeps the backdrop's colours; more is more vivid. */
    val saturation: Float = 1f,
    val rim: Dp = 2.5.dp,
    val shadow: Boolean = false,
    val frostBlur: Dp = 18.dp,
    /** The colour and strength of the veil over a frosted shape. */
    val veil: Color = Color.Transparent,
    val frostSaturation: Float = 0.68f,
    val frostContrast: Float = 0.9f,
)

internal const val FLUID_MAX = 16
internal const val FLUID_MAX_PAIRS = 24

private const val FIELD_SRC = """
uniform float2 pad;
uniform float count;
uniform float pairCount;
uniform float cullMargin;
uniform float exactDist;  // 1: every distance exact (the shadow needs them), 0: far shapes only bounded
uniform float4 box[16];   // centre xy, half size zw
uniform float4 info[16];  // corner radius, bounding radius, cos, sin of the turn
uniform float4 pA[24];    // the two shapes of each bridge, repeated here so no lookup by index is needed
uniform float4 pB[24];
uniform float4 pAi[24];
uniform float4 pBi[24];
uniform float pk[24];     // blend radius of the bridge

float sdBoxI(float2 p, float4 b, float4 inf) {
    float2 d = p - b.xy;
    // Into the shape's own, turned, frame.
    float2 l = float2(inf.z * d.x + inf.w * d.y, -inf.w * d.x + inf.z * d.y);
    float2 q = abs(l) - (b.zw - float2(inf.x));
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - inf.x;
}

float smin(float a, float b, float kk) {
    float h = max(kk - abs(a - b), 0.0) / kk;
    return min(a, b) - h * h * h * kk / 6.0;
}

// The field: every shape's distance, and only for the pairs that really are bridged the smooth union.
// A shape far from this pixel is not measured exactly (its distance is at least the distance to its
// bounding circle, which is all that matters when it is not the nearest), and a bridge only counts
// near its two shapes: so a shape with no neighbour costs the same as if the others were not there.
float field(float2 p) {
    float r = 1.0e6;
    for (int i = 0; i < 16; i++) {
        if (float(i) >= count) break;
        float dc = length(p - box[i].xy);
        float di = (exactDist < 0.5 && dc > info[i].y + cullMargin) ? (dc - info[i].y) : sdBoxI(p, box[i], info[i]);
        r = min(r, di);
    }
    for (int n = 0; n < 24; n++) {
        if (float(n) >= pairCount) break;
        float kk = pk[n];
        float da = length(p - pA[n].xy);
        float db = length(p - pB[n].xy);
        if (da < pAi[n].y + kk && db < pBi[n].y + kk) {
            r = min(r, smin(sdBoxI(p, pA[n], pAi[n]), sdBoxI(p, pB[n], pBi[n]), kk));
        }
    }
    return r;
}
"""

private const val GLASS_SRC = """
uniform shader content;
uniform float4 look[16];  // frost, frost rim, brightness, press
uniform float2 touch[16];
uniform float4 tint[16];  // colour laid over the glass inside a shape: rgb and strength
uniform float frostPass;  // 0: the sharp glass, 1: the frosted copy (its input is already blurred)
uniform float refractionHeight;
uniform float refractionAmount;
uniform float rimWidth;
uniform float chroma;
uniform float sat;
uniform float4 veil;
uniform float frostSat;
uniform float frostContrast;
""" + FIELD_SRC + """
half4 main(float2 coord) {
    float2 p = coord - pad;
    float sd = field(p);
    if (sd > 1.0) return half4(0.0);
    float cover = clamp(0.5 - sd, 0.0, 1.0);

    // What each shape near here asks of the glass: how frosted, how bright, how pressed.
    float frostW = 0.0;
    float bright = 0.0;
    float wsum = 0.0;
    float lightUp = 0.0;
    float tintA = 0.0;
    float3 tintRGB = float3(0.0);
    for (int i = 0; i < 16; i++) {
        if (float(i) >= count) break;
        float dc = length(p - box[i].xy);
        if (dc > info[i].y + 40.0) continue;
        float si = sdBoxI(p, box[i], info[i]);
        float w = exp(-max(si, 0.0) / 6.0);
        bright += w * look[i].z;
        wsum += w;
        frostW = max(frostW, look[i].x * smoothstep(0.0, max(look[i].y, 1.0), -si));
        if (tint[i].w > 0.0) {
            float a = smoothstep(0.5, -0.5, si) * tint[i].w;
            if (a > tintA) { tintA = a; tintRGB = tint[i].rgb; }
        }
        if (look[i].w > 0.0) {
            float inside = smoothstep(0.5, -0.5, si);
            float reachR = min(box[i].z, box[i].w) * 3.0;
            float closeBy = smoothstep(reachR, reachR * 0.5, distance(p, touch[i]));
            lightUp += inside * look[i].w * (0.08 + 0.15 * closeBy);
        }
    }
    bright = wsum > 0.0 ? bright / wsum : 0.0;
    if (frostPass > 0.5 && frostW < 0.002) return half4(0.0);

    // The direction the surface faces, from a wide difference and NOT normalised: along the middle
    // ridge of a thin body the direction flips, and a unit vector there tears the picture (the grid
    // lines hooked and broke); this way it fades to nothing at the ridge instead.
    float e = 7.0;
    float2 n = float2(
        field(p + float2(e, 0.0)) - field(p - float2(e, 0.0)),
        field(p + float2(0.0, e)) - field(p - float2(0.0, e))) / (2.0 * e);
    float nl = length(n);
    if (nl > 1.0) n = n / nl;
    float inner = min(sd, 0.0);
    float2 shift = float2(0.0);
    if (-inner < refractionHeight) {
        float t = 1.0 - (-inner) / refractionHeight;
        shift = n * ((1.0 - sqrt(1.0 - t * t)) * refractionAmount);
    }
    half4 c;
    if (chroma > 0.5 && dot(shift, shift) > 0.0) {
        half4 cr = content.eval(coord + shift * 1.07);
        half4 cg = content.eval(coord + shift);
        half4 cb = content.eval(coord + shift * 0.93);
        c = half4(cr.r, cg.g, cb.b, cg.a);
    } else {
        c = content.eval(coord + shift);
    }
    float luma = dot(c.rgb, half3(0.2126, 0.7152, 0.0722));
    if (frostPass > 0.5) {
        c.rgb = mix(half3(luma), c.rgb, half(frostSat));
        c.rgb = (c.rgb - half3(0.5)) * half(frostContrast) + half3(0.5);
        c.rgb = mix(c.rgb, veil.rgb, half(veil.a));
    } else {
        c.rgb = mix(half3(luma), c.rgb, half(sat));
    }
    c.rgb += half3(bright) * c.a;
    if (tintA > 0.0) {
        // The colour over the glass, as 'over': it also shows where the backdrop is empty.
        c.rgb = c.rgb * half(1.0 - tintA) + half3(tintRGB) * half(tintA);
        c.a = c.a * half(1.0 - tintA) + half(tintA);
    }

    float rim = smoothstep(rimWidth, 0.0, -inner);
    float light = dot(n, normalize(float2(-0.55, -0.8)));
    float spec = rim * (max(light, 0.0) * 0.8 + max(-light, 0.0) * 0.3);
    // The edge light and the press light do not depend on what is behind: over an empty backdrop the
    // glass has no body to show, but its edge must still be seen.
    float add = spec * 0.55 + lightUp;
    c.rgb += half3(add);
    // White at its own alpha: lighter over a dark backdrop, unchanged over a white one (it used to darken a white page).
    c.a = max(c.a, half(clamp(add, 0.0, 1.0)));
    float k = frostPass > 0.5 ? cover * frostW : cover;
    return c * k;
}
"""

private const val SHADOW_SRC = """
uniform float shadowSigma;
uniform float2 shadowOffset;
uniform float shadowAlpha;
""" + FIELD_SRC + """
half4 main(float2 coord) {
    float sd = field(coord - pad - shadowOffset);
    // A Gaussian fall-off (a smoothstep that ends at some distance left a visible ring around every bubble).
    float dd = max(sd, 0.0);
    float s2 = shadowSigma * 0.5;
    float a = shadowAlpha * exp(-(dd * dd) / (2.0 * s2 * s2));
    // Only outside the glass: where the backdrop is empty the glass is see-through, and a shadow under it would show as a grey patch.
    a *= smoothstep(-0.5, 1.5, field(coord - pad));
    return half4(0.0, 0.0, 0.0, a);
}
"""

/** The numbers of one frame for the shaders, built once and used by every pass. */
internal class FluidSnapshot(shapes: List<FluidShape>, scene: FluidScene) {
    val n = min(shapes.size, FLUID_MAX)
    val boxes = FloatArray(FLUID_MAX * 4)
    val infos = FloatArray(FLUID_MAX * 4)
    val looks = FloatArray(FLUID_MAX * 4)
    val touches = FloatArray(FLUID_MAX * 2)
    val tints = FloatArray(FLUID_MAX * 4)
    val pa = FloatArray(FLUID_MAX_PAIRS * 4)
    val pb = FloatArray(FLUID_MAX_PAIRS * 4)
    val pai = FloatArray(FLUID_MAX_PAIRS * 4)
    val pbi = FloatArray(FLUID_MAX_PAIRS * 4)
    val pk = FloatArray(FLUID_MAX_PAIRS)
    var pairs = 0
    var cullMargin = 4f

    init {
        for (i in 0 until n) {
            val s = shapes[i]
            boxes[i * 4] = s.cx
            boxes[i * 4 + 1] = s.cy
            boxes[i * 4 + 2] = s.hw
            boxes[i * 4 + 3] = s.hh
            infos[i * 4] = s.radius
            infos[i * 4 + 1] = s.bound
            infos[i * 4 + 2] = kotlin.math.cos(s.angle)
            infos[i * 4 + 3] = kotlin.math.sin(s.angle)
            looks[i * 4] = s.frost
            looks[i * 4 + 1] = s.frostRim
            looks[i * 4 + 2] = s.brightness
            looks[i * 4 + 3] = s.press
            touches[i * 2] = s.touchX
            touches[i * 2 + 1] = s.touchY
            tints[i * 4] = s.tintR
            tints[i * 4 + 1] = s.tintG
            tints[i * 4 + 2] = s.tintB
            tints[i * 4 + 3] = s.tintA
        }
        // Only the bridges that exist now, strongest first.
        val live = ArrayList<Triple<Int, Int, Float>>()
        for (i in 0 until n) for (j in i + 1 until n) {
            val bl = scene.bridgeBlend(shapes[i], shapes[j])
            if (bl > 0.001f) live.add(Triple(i, j, bl))
        }
        live.sortByDescending { it.third }
        var widest = 0f
        for ((i, j, bl) in live) {
            if (pairs >= FLUID_MAX_PAIRS) break
            System.arraycopy(boxes, i * 4, pa, pairs * 4, 4)
            System.arraycopy(boxes, j * 4, pb, pairs * 4, 4)
            System.arraycopy(infos, i * 4, pai, pairs * 4, 4)
            System.arraycopy(infos, j * 4, pbi, pairs * 4, 4)
            pk[pairs] = bl
            if (bl > widest) widest = bl
            pairs++
        }
        cullMargin = widest + 4f
    }

    fun applyField(shader: RuntimeShader, padX: Float, padY: Float, exact: Boolean = false) {
        shader.setFloatUniform("pad", padX, padY)
        shader.setFloatUniform("exactDist", if (exact) 1f else 0f)
        shader.setFloatUniform("count", n.toFloat())
        shader.setFloatUniform("pairCount", pairs.toFloat())
        shader.setFloatUniform("cullMargin", cullMargin)
        shader.setFloatUniform("box", boxes)
        shader.setFloatUniform("info", infos)
        shader.setFloatUniform("pA", pa)
        shader.setFloatUniform("pB", pb)
        shader.setFloatUniform("pAi", pai)
        shader.setFloatUniform("pBi", pbi)
        shader.setFloatUniform("pk", pk)
    }
}

/** Builds the frame's numbers once per tick however many passes ask for them. */
internal class FluidSnapshotCache {
    private var tick = Int.MIN_VALUE
    private var snap: FluidSnapshot? = null

    fun get(scene: FluidScene): FluidSnapshot {
        val t = scene.tick
        val cached = snap
        if (cached != null && t == tick) return cached
        val fresh = FluidSnapshot(scene.shapes(), scene)
        snap = fresh
        tick = t
        return fresh
    }
}

/**
 * Draws a [scene]: the glass (and, while some shape is frosted, its frosted copy) over [backdrop].
 * Fill the area the glass can appear in; nothing is drawn outside the shapes (but for a soft shadow).
 */
@Composable
fun FluidGlassLayer(
    backdrop: Backdrop,
    scene: FluidScene,
    look: FluidLook,
    modifier: Modifier = Modifier,
    /** How far the glass may reach beyond the area it fills (a bubble pulled out of its bar, a shadow). */
    margin: Dp = 72.dp,
) {
    val cache = remember(scene) { FluidSnapshotCache() }
    val marginPx = with(androidx.compose.ui.platform.LocalDensity.current) { margin.roundToPx() }
    Box(modifier) {
        if (look.shadow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val shadowShader = remember { RuntimeShader(SHADOW_SRC) }
            Canvas(Modifier.matchParentSize().layerBounds(scene, marginPx)) {
                scene.tick
                try {
                    val snap = cache.get(scene)
                    val o = layerOrigin(scene, marginPx)
                    snap.applyField(shadowShader, -o.x.toFloat(), -o.y.toFloat(), exact = true)
                    shadowShader.setFloatUniform("shadowSigma", 9.dp.toPx())
                    shadowShader.setFloatUniform("shadowOffset", 0f, 3.dp.toPx())
                    shadowShader.setFloatUniform("shadowAlpha", 0.16f)
                    drawRect(ShaderBrush(shadowShader))
                } catch (e: Throwable) {
                    android.util.Log.e("FluidGlass", "shadow failed", e)
                }
            }
        }
        // The sharp glass for the whole union.
        Box(
            Modifier
                .matchParentSize()
                .layerBounds(scene, marginPx)
                .glassSnapshot()
                // Reading the scene here as well makes every step redraw the glass, whether or not
                // the backdrop noticed the read inside its own effect block.
                .drawBehind { scene.tick }
                .drawPlainBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = {
                        scene.tick
                        fluidPass(cache, scene, look, marginPx, frost = false)
                    },
                    onDrawBackdrop = { drawBackdrop ->
                        drawBackdrop()
                        scene.sampleInks(this, drawBackdrop)
                    },
                ),
        )
        // The frosted copy, over the first one, only where something is frosted.
        if (scene.frostActive) {
            Box(
                Modifier
                    .matchParentSize()
                    .layerBounds(scene, marginPx)
                    .glassSnapshot()
                    .drawBehind { scene.tick }
                    .drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = {
                            scene.tick
                            fluidPass(cache, scene, look, marginPx, frost = true)
                        },
                    ),
            )
        }
    }
}

private fun com.kyant.backdrop.BackdropEffectScope.fluidPass(
    cache: FluidSnapshotCache,
    scene: FluidScene,
    look: FluidLook,
    marginPx: Int,
    frost: Boolean,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    try {
        val snap = cache.get(scene)
        // The blur comes first: the shader then refracts the blurred picture (frosted copy) or the
        // plain one (clear glass).
        if (frost) glassBlur(look.frostBlur.toPx(), useLabScale = false) else glassBlur(look.blur.toPx())
        val shader: RuntimeShader = obtainRuntimeShader(if (frost) "FluidGlassFrost" else "FluidGlass", GLASS_SRC)
        val o = layerOrigin(scene, marginPx)
        snap.applyField(shader, padding - o.x, padding - o.y)
        shader.setFloatUniform("look", snap.looks)
        shader.setFloatUniform("touch", snap.touches)
        shader.setFloatUniform("tint", snap.tints)
        shader.setFloatUniform("frostPass", if (frost) 1f else 0f)
        shader.setFloatUniform("refractionHeight", look.refractionHeight.toPx())
        shader.setFloatUniform("refractionAmount", -look.refractionAmount.toPx() * GlassTuning.refraction.coerceAtLeast(0f))
        shader.setFloatUniform("rimWidth", look.rim.toPx())
        shader.setFloatUniform("chroma", if (look.fringes && GlassTuning.fringes) 1f else 0f)
        shader.setFloatUniform("sat", look.saturation)
        val v = look.veil
        shader.setFloatUniform("veil", v.red, v.green, v.blue, v.alpha)
        shader.setFloatUniform("frostSat", look.frostSaturation)
        shader.setFloatUniform("frostContrast", look.frostContrast)
        effect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
    } catch (e: Throwable) {
        // A shader the device refuses must not take the app down: the glass is just not drawn.
        android.util.Log.e("FluidGlass", "shader failed", e)
    }
}

/** Where the layer's top-left is, in the area's own coordinates: just covering the shapes (plus the margin), or the area grown by the margin. */
internal fun layerOrigin(scene: FluidScene, marginPx: Int): androidx.compose.ui.unit.IntOffset {
    val b = scene.bounds() ?: return androidx.compose.ui.unit.IntOffset(-marginPx, -marginPx)
    return androidx.compose.ui.unit.IntOffset(kotlin.math.floor(b.left - marginPx).toInt(), kotlin.math.floor(b.top - marginPx).toInt())
}

/** Lays the node out to cover the scene's shapes (see [FluidScene.bounds]), without changing the size its parent sees. */
private fun Modifier.layerBounds(scene: FluidScene, marginPx: Int): Modifier = this.layout { measurable, constraints ->
    val w = constraints.maxWidth
    val h = constraints.maxHeight
    val b = scene.bounds()
    val o = layerOrigin(scene, marginPx)
    val lw = if (b == null) w + 2 * marginPx else (kotlin.math.ceil(b.right + marginPx).toInt() - o.x).coerceAtLeast(1)
    val lh = if (b == null) h + 2 * marginPx else (kotlin.math.ceil(b.bottom + marginPx).toInt() - o.y).coerceAtLeast(1)
    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(lw, lh))
    layout(w, h) { placeable.place(o.x, o.y) }
}
