package eu.siacs.conversations.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.effect
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign
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
 * Besides the bridges: pieces can be added, there can be walls a piece squashes against while the
 * finger keeps pulling, and a slippery floor that lets a piece keep sliding (and bounce) after it is
 * let go. The size of the field is adjustable.
 *
 * The bridge follows the gesture: how deeply two pieces have pulled towards each other is a smooth
 * function of the gap between them (zero at the reach, full when touching), eased by a critically
 * damped spring, so it pours rather than snaps and a bridge never gets thicker while it is pulled apart.
 */

private const val SHAPE_MAX = 8

private const val FLUID_SHADER = """
uniform shader content;
uniform float2 pad;
uniform float count;
uniform float4 box[8];
uniform float rad[8];
uniform float k[64];
uniform float refractionHeight;
uniform float refractionAmount;
uniform float rimWidth;

float sdBox(float2 p, float4 b, float r) {
    float2 q = abs(p - b.xy) - (b.zw - float2(r));
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float smin(float a, float b, float kk) {
    float h = max(kk - abs(a - b), 0.0) / kk;
    return min(a, b) - h * h * h * kk / 6.0;
}

float field(float2 p) {
    float d[8];
    float r = 1.0e6;
    for (int i = 0; i < 8; i++) {
        if (float(i) >= count) break;
        d[i] = sdBox(p, box[i], rad[i]);
        r = min(r, d[i]);
    }
    for (int i = 0; i < 8; i++) {
        if (float(i) >= count) break;
        for (int j = 0; j < 8; j++) {
            if (j > i && float(j) < count) {
                float kk = k[i * 8 + j];
                if (kk > 0.001) r = min(r, smin(d[i], d[j], kk));
            }
        }
    }
    return r;
}

half4 main(float2 coord) {
    float2 p = coord - pad;
    float sd = field(p);
    if (sd > 1.0) return half4(0.0);
    float cover = clamp(0.5 - sd, 0.0, 1.0);
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

/**
 * One member of a fluid field. [pos] is the top-left of its body AS IT IS NOW: pressed against
 * something, the body is squashed ([sqX], [sqY]), and the physics (the walls, the gaps between them)
 * always works with that current size, so a piece pressed flat can slip through a slot it would not
 * fit in at rest. The side that touches stays put while it squashes.
 */
@Stable
class FluidPiece(val baseW: Float, val baseH: Float, val baseR: Float, x: Float, y: Float) {
    var pos by mutableStateOf(Offset(x, y))
    var vel = Offset.Zero
    var dragging = false

    /** Where the finger wants the CENTRE of the body to be (it can be beyond a wall). */
    var target = Offset(x + baseW / 2f, y + baseH / 2f)
    var sqX by mutableFloatStateOf(0f)
    var sqY by mutableFloatStateOf(0f)
    var sqVX = 0f
    var sqVY = 0f
    var contactX = 0
    var contactY = 0

    val width get() = baseW * (1f - sqX) * (1f + 0.3f * sqY)
    val height get() = baseH * (1f - sqY) * (1f + 0.3f * sqX)
    val radius get() = min(baseR, min(width, height) / 2f)
    val left get() = pos.x
    val top get() = pos.y
    val centerX get() = pos.x + width / 2f
    val centerY get() = pos.y + height / 2f
}

/**
 * The pieces, the springs of the bridges between them, and the little physics of the field.
 *
 * Gesture-driven: how much two pieces reach for each other follows how far apart they are, as a
 * smooth curve (nothing is switched on or off), and the blend itself is critically damped (never
 * overshoots, so a bridge never swells while it is being pulled apart), slow enough to feel poured
 * rather than snapped.
 */
@Stable
class GlassFluidState(private val density: Float) {
    val pieces = mutableStateListOf<FluidPiece>()

    /** Depth of the bridge of each pair (px), indexed low * 8 + high. */
    private val depth = FloatArray(SHAPE_MAX * SHAPE_MAX)
    private val depthVelocity = FloatArray(SHAPE_MAX * SHAPE_MAX)
    private val gaps = FloatArray(SHAPE_MAX * SHAPE_MAX) { 1e6f }
    var tick by mutableIntStateOf(0)
        private set

    /** Something moved (a piece, a setting): the springs have to run. */
    val wakeups = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    fun wake() {
        wakeups.trySend(Unit)
    }

    /** Pieces start reaching for each other from this gap (px). */
    var reach = 28f
    /** How deep a full merge is: the roundness of the join (px). */
    var softness = 16f
    /** Seconds for the blend to settle: how slowly it pours. */
    var response = 0.65f

    var wallsOn by mutableStateOf(false)
    var inertiaOn = false
    /** Slowing of a sliding piece, per second. */
    var friction = 1.4f
    /** How much speed is kept when a sliding piece hits an edge or a wall (1: all of it, above 1: more). */
    var bounce = 0.45f

    var fieldW by mutableFloatStateOf(0f)
    var fieldH by mutableFloatStateOf(0f)

    private val templates = listOf(
        floatArrayOf(64f, 64f, 32f),
        floatArrayOf(116f, 64f, 32f),
        floatArrayOf(64f, 64f, 20f),
        floatArrayOf(52f, 52f, 26f),
        floatArrayOf(150f, 56f, 28f),
        floatArrayOf(56f, 104f, 28f),
    )
    private var nextTemplate = 0

    /** The cross of walls in the middle of a field of this size (always the same shape, drawn when on). */
    fun wallRects(w: Float, h: Float): List<Rect> {
        if (w <= 0f || h <= 0f) return emptyList()
        val t = 10f * density
        return listOf(
            Rect(w / 2f - t / 2f, h * 0.12f, w / 2f + t / 2f, h * 0.88f),
            Rect(w * 0.12f, h / 2f - t / 2f, w * 0.88f, h / 2f + t / 2f),
        )
    }

    private fun walls(): List<Rect> = if (wallsOn) wallRects(fieldW, fieldH) else emptyList()

    fun reset() {
        pieces.clear()
        nextTemplate = 0
        clearPairs()
        repeat(3) { add() }
    }

    private fun clearPairs() {
        depth.fill(0f)
        depthVelocity.fill(0f)
        gaps.fill(1e6f)
    }

    private fun clearPairsOf(index: Int) {
        for (o in 0 until SHAPE_MAX) {
            for (idx in intArrayOf(pairIndex(index, o), pairIndex(o, index))) {
                depth[idx] = 0f
                depthVelocity[idx] = 0f
                gaps[idx] = 1e6f
            }
        }
    }

    /** Adds a piece of the next shape in a free place; false when the field is full. */
    fun add(): Boolean {
        if (pieces.size >= SHAPE_MAX) return false
        val t = templates[nextTemplate % templates.size]
        nextTemplate++
        val w = t[0] * density
        val h = t[1] * density
        val fw = if (fieldW > 0f) fieldW else 340f * density
        val fh = if (fieldH > 0f) fieldH else 300f * density
        val anchors = listOf(
            0.25f to 0.28f, 0.75f to 0.28f, 0.25f to 0.78f, 0.75f to 0.78f,
            0.5f to 0.06f, 0.08f to 0.5f, 0.92f to 0.5f, 0.5f to 0.94f,
        )
        val walls = wallRects(fw, fh)
        val margin = 10f * density
        fun free(x: Float, y: Float, vsPieces: Boolean): Boolean {
            val r = Rect(x - margin, y - margin, x + w + margin, y + h + margin)
            if (walls.any { it.overlaps(r) }) return false
            if (vsPieces && pieces.any { Rect(it.pos.x, it.pos.y, it.pos.x + it.baseW, it.pos.y + it.baseH).overlaps(r) }) return false
            return true
        }
        var spot: Offset? = null
        for (strict in listOf(true, false)) {
            for ((ax, ay) in anchors) {
                val x = (fw * ax - w / 2f).coerceIn(0f, max(fw - w, 0f))
                val y = (fh * ay - h / 2f).coerceIn(0f, max(fh - h, 0f))
                if (free(x, y, strict)) { spot = Offset(x, y); break }
            }
            if (spot != null) break
        }
        val at = spot ?: Offset(0f, 0f)
        clearPairsOf(pieces.size)
        pieces.add(FluidPiece(w, h, t[2] * density, at.x, at.y))
        wake()
        return true
    }

    fun removeLast() {
        if (pieces.size <= 1) return
        clearPairsOf(pieces.size - 1)
        pieces.removeAt(pieces.lastIndex)
        wake()
    }

    /** The smoothing radius of the pair in the shader (0: none, the pieces are separate). */
    fun blendFor(i: Int, j: Int): Float {
        val pair = pairIndex(i, j)
        val c = depth[pair]
        if (c < 4f) return 0f
        // A radius of three times the gap is where the surfaces just touch (cubic blend: the
        // middle sinks by a sixth of the radius); the depth is on top of that.
        // Never longer than what tears at the reach: however far the pieces were pulled in one go,
        // a bridge cannot outlive that (it used to hang on as a long thread while the depth settled).
        val cap = max(3f * reach * 0.98f, c)
        return min(3f * max(gaps[pair], 0f) + c, cap)
    }

    private fun pairIndex(i: Int, j: Int): Int = if (i < j) i * SHAPE_MAX + j else j * SHAPE_MAX + i

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

    private fun smooth(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private class Slide(val pos: Offset, val hx: Int, val hy: Int, val bx: Float, val by: Float)

    /**
     * Moves a piece's CURRENT body (its squashed size) towards the top-left [to] without going through
     * the field's edges or a wall; says which side stopped it and where that surface is.
     */
    private fun slide(p: FluidPiece, to: Offset, walls: List<Rect>): Slide {
        val w = p.width
        val h = p.height
        val x = p.pos.x
        val y = p.pos.y
        var hx = 0
        var hy = 0
        var bx = 0f
        var by = 0f
        var nx = to.x
        if (fieldW > 0f) {
            if (nx < 0f) { nx = 0f; hx = -1; bx = 0f }
            if (nx + w > fieldW) { nx = max(fieldW - w, 0f); hx = 1; bx = fieldW }
        }
        for (wl in walls) {
            if (y < wl.bottom && y + h > wl.top) {
                if (x + w <= wl.left + 0.5f && nx + w > wl.left) { nx = wl.left - w; hx = 1; bx = wl.left }
                else if (x >= wl.right - 0.5f && nx < wl.right) { nx = wl.right; hx = -1; bx = wl.right }
            }
        }
        var ny = to.y
        if (fieldH > 0f) {
            if (ny < 0f) { ny = 0f; hy = -1; by = 0f }
            if (ny + h > fieldH) { ny = max(fieldH - h, 0f); hy = 1; by = fieldH }
        }
        for (wl in walls) {
            if (nx < wl.right && nx + w > wl.left) {
                if (y + h <= wl.top + 0.5f && ny + h > wl.top) { ny = wl.top - h; hy = 1; by = wl.top }
                else if (y >= wl.bottom - 0.5f && ny < wl.bottom) { ny = wl.bottom; hy = -1; by = wl.bottom }
            }
        }
        return Slide(Offset(nx, ny), hx, hy, bx, by)
    }

    /** A piece that ended up inside a wall (the walls were just switched on, or it grew back) steps out the shortest way. */
    private fun escape(p: FluidPiece, walls: List<Rect>) {
        for (w in walls) {
            val r = Rect(p.pos.x, p.pos.y, p.pos.x + p.width, p.pos.y + p.height)
            if (!w.overlaps(r)) continue
            val left = r.right - w.left
            val right = w.right - r.left
            val up = r.bottom - w.top
            val down = w.bottom - r.top
            val m = min(min(left, right), min(up, down))
            p.pos = when (m) {
                left -> Offset(w.left - p.width, p.pos.y)
                right -> Offset(w.right, p.pos.y)
                up -> Offset(p.pos.x, w.top - p.height)
                else -> Offset(p.pos.x, w.bottom)
            }
            p.vel = Offset.Zero
        }
    }

    /** Moves one piece for [dt]; true while it is still in motion. */
    private fun advance(p: FluidPiece, dt: Float, walls: List<Rect>): Boolean {
        var moving = false
        var penX = 0f
        var penY = 0f
        if (p.dragging) {
            val res = slide(p, Offset(p.target.x - p.width / 2f, p.target.y - p.height / 2f), walls)
            if (res.pos != p.pos) p.pos = res.pos
            // The finger can go on past a wall: how far its centre is beyond where an unsquashed
            // body would rest against that surface is how hard the piece is pressed.
            if (res.hx > 0) penX = p.target.x - (res.bx - p.baseW / 2f)
            if (res.hx < 0) penX = (res.bx + p.baseW / 2f) - p.target.x
            if (res.hy > 0) penY = p.target.y - (res.by - p.baseH / 2f)
            if (res.hy < 0) penY = (res.by + p.baseH / 2f) - p.target.y
            if (res.hx != 0) p.contactX = res.hx
            if (res.hy != 0) p.contactY = res.hy
            p.vel = Offset.Zero
            moving = true
        } else if (inertiaOn && p.vel.getDistance() > (if (friction > 0f) MIN_SPEED * density else 0.01f)) {
            val res = slide(p, p.pos + p.vel * dt, walls)
            if (res.pos != p.pos) p.pos = res.pos
            var v = p.vel
            // With no friction at all nothing is lost, not even in a bounce: the piece never stops.
            val rest = if (friction <= 0f) 1f else bounce
            if (res.hx != 0) {
                if (wallsOn) { p.sqVX += abs(v.x) / density * IMPACT; p.contactX = res.hx }
                v = Offset(-v.x * rest, v.y)
            }
            if (res.hy != 0) {
                if (wallsOn) { p.sqVY += abs(v.y) / density * IMPACT; p.contactY = res.hy }
                v = Offset(v.x, -v.y * rest)
            }
            v = v * exp(-friction * dt)
            val top = MAX_SPEED * density
            val sp = v.getDistance()
            p.vel = if (sp > top) v * (top / sp) else v
            moving = true
        } else {
            p.vel = Offset.Zero
        }
        // The squash is a bouncy spring: pressed, it follows the pressure; free, it rings out.
        val tx = if (wallsOn) (max(penX, 0f) / p.baseW).coerceAtMost(MAX_SQUASH) else 0f
        val ty = if (wallsOn) (max(penY, 0f) / p.baseH).coerceAtMost(MAX_SQUASH) else 0f
        val oldW = p.width
        val oldH = p.height
        var left = dt
        while (left > 0f) {
            val h = min(left, 1f / 240f)
            p.sqVX += (SQ_STIFFNESS * (tx - p.sqX) - SQ_DAMPING * p.sqVX) * h
            p.sqVY += (SQ_STIFFNESS * (ty - p.sqY) - SQ_DAMPING * p.sqVY) * h
            p.sqX = (p.sqX + p.sqVX * h).coerceIn(-0.1f, 0.7f)
            p.sqY = (p.sqY + p.sqVY * h).coerceIn(-0.1f, 0.7f)
            left -= h
        }
        val ringing = abs(p.sqX) > 0.004f || abs(p.sqY) > 0.004f || abs(p.sqVX) > 0.05f || abs(p.sqVY) > 0.05f
        if (!ringing) {
            p.sqX = 0f; p.sqY = 0f; p.sqVX = 0f; p.sqVY = 0f
        } else {
            moving = true
        }
        // The side that touches stays where it is while the body changes size.
        val dw = oldW - p.width
        val dh = oldH - p.height
        if (dw != 0f || dh != 0f) {
            var nx = p.pos.x + when { p.contactX > 0 -> dw; p.contactX < 0 -> 0f; else -> dw / 2f }
            var ny = p.pos.y + when { p.contactY > 0 -> dh; p.contactY < 0 -> 0f; else -> dh / 2f }
            if (fieldW > 0f) nx = nx.coerceIn(0f, max(fieldW - p.width, 0f))
            if (fieldH > 0f) ny = ny.coerceIn(0f, max(fieldH - p.height, 0f))
            p.pos = Offset(nx, ny)
        }
        if (!ringing && !p.dragging) { p.contactX = 0; p.contactY = 0 }
        return moving
    }

    /** One physics step; true while anything is still moving. */
    fun step(dt: Float): Boolean {
        var moving = false
        val walls = walls()
        if (walls.isNotEmpty()) for (p in pieces) if (!p.dragging) escape(p, walls)
        for (p in pieces) if (advance(p, dt, walls)) moving = true
        if (laserOn && laserSpin != 0f) {
            laserPhase += laserSpin * dt
            moving = true
        }
        val slowOmega = 5.8f / response.coerceAtLeast(0.2f)
        for (i in pieces.indices) for (j in i + 1 until pieces.size) {
            val pair = pairIndex(i, j)
            val g = gap(pieces[i], pieces[j])
            gaps[pair] = g
            // The closer, the deeper: 0 at the reach, full when touching.
            val target = 3f * softness * smooth(1f - g / reach.coerceAtLeast(1f))
            // Critically damped, the same going in and coming out: no overshoot, and it pours.
            val omega = slowOmega
            val stiffness = omega * omega
            val damping = 2f * omega
            var left = dt
            while (left > 0f) {
                val h = min(left, 1f / 120f)
                depthVelocity[pair] += (stiffness * (target - depth[pair]) - damping * depthVelocity[pair]) * h
                depth[pair] = max(depth[pair] + depthVelocity[pair] * h, 0f)
                left -= h
            }
            if (abs(depth[pair] - target) > 0.05f || abs(depthVelocity[pair]) > 0.5f) moving = true
        }
        tick++
        return moving
    }

    // ---- the laser ---------------------------------------------------------------------------

    var laserOn = false
    /** Direction of the beam in degrees (0: right, 90: down), plus [laserSpin] degrees per second. */
    var laserAngle = 35f
    var laserSpin = 0f
    private var laserPhase = 0f
    /** Refractive index of the glass: the beam bends towards the normal on the way in. */
    var glassIndex = 1.5f

    class LaserSeg(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val alpha: Float)

    private fun sdPiece(p: FluidPiece, x: Float, y: Float): Float {
        val qx = abs(x - p.centerX) - (p.width / 2f - p.radius)
        val qy = abs(y - p.centerY) - (p.height / 2f - p.radius)
        return hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - p.radius
    }

    private val dBuf = FloatArray(SHAPE_MAX)

    /** The same field as the shader draws: the pieces joined by their bridges. */
    private fun fieldSd(x: Float, y: Float, n: Int, k: FloatArray): Float {
        var r = 1e9f
        for (i in 0 until n) {
            dBuf[i] = sdPiece(pieces[i], x, y)
            r = min(r, dBuf[i])
        }
        for (i in 0 until n) for (j in i + 1 until n) {
            val kk = k[i * SHAPE_MAX + j]
            if (kk > 0.001f) {
                val h = max(kk - abs(dBuf[i] - dBuf[j]), 0f) / kk
                r = min(r, min(dBuf[i], dBuf[j]) - h * h * h * kk / 6f)
            }
        }
        return r
    }

    /**
     * The beam: it starts at the middle of the field, leaves the wall it sits in, reflects off the
     * walls and the edges, and bends into and out of the glass (the drops and their bridges are
     * traced as one shape, like the shader draws them), with total reflection where the angle is too
     * flat to get out.
     */
    fun laserPath(): List<LaserSeg> {
        if (!laserOn || fieldW <= 0f || fieldH <= 0f) return emptyList()
        val n = min(pieces.size, SHAPE_MAX)
        val k = FloatArray(SHAPE_MAX * SHAPE_MAX)
        for (i in 0 until n) for (j in i + 1 until n) k[i * SHAPE_MAX + j] = blendFor(i, j)
        val walls = walls()
        val a = Math.toRadians((laserAngle + laserPhase).toDouble())
        var dx = kotlin.math.cos(a).toFloat()
        var dy = kotlin.math.sin(a).toFloat()
        var x = fieldW / 2f
        var y = fieldH / 2f
        var guard = 0
        while (guard++ < 4000 && walls.any { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }) {
            x += dx * 2f
            y += dy * 2f
        }
        var inside = fieldSd(x, y, n, k) < 0f
        val segs = ArrayList<LaserSeg>()
        var alpha = 1f
        var total = 0f
        val limit = 8f * (fieldW + fieldH)
        val index = glassIndex.coerceAtLeast(0.2f)
        for (hit in 0 until MAX_LASER_HITS) {
            // What flat things are in the way: the edges of the field first, then the walls.
            val tx = if (dx > 0f) (fieldW - x) / dx else if (dx < 0f) -x / dx else Float.MAX_VALUE
            val ty = if (dy > 0f) (fieldH - y) / dy else if (dy < 0f) -y / dy else Float.MAX_VALUE
            var tA = min(tx, ty)
            var nax = if (tx <= ty) -sign(dx) else 0f
            var nay = if (tx <= ty) 0f else -sign(dy)
            for (w in walls) {
                val inf = Float.MAX_VALUE
                val x1 = if (dx != 0f) (w.left - x) / dx else if (x in w.left..w.right) -inf else inf
                val x2 = if (dx != 0f) (w.right - x) / dx else if (x in w.left..w.right) inf else -inf
                val y1 = if (dy != 0f) (w.top - y) / dy else if (y in w.top..w.bottom) -inf else inf
                val y2 = if (dy != 0f) (w.bottom - y) / dy else if (y in w.top..w.bottom) inf else -inf
                val enterX = min(x1, x2)
                val enterY = min(y1, y2)
                val enter = max(enterX, enterY)
                val exit = min(max(x1, x2), max(y1, y2))
                if (enter <= exit && enter > 0.001f && enter < tA) {
                    tA = enter
                    if (enterX > enterY) { nax = -sign(dx); nay = 0f } else { nax = 0f; nay = -sign(dy) }
                }
            }
            // The glass: sphere tracing along the beam, up to the flat obstacle.
            var t = 0f
            var hitGlass = false
            var steps = 0
            while (t < tA && steps++ < 160) {
                val ad = abs(fieldSd(x + dx * t, y + dy * t, n, k))
                if (ad < 0.6f && t > 1.5f) { hitGlass = true; break }
                // Still on the surface it just came through: step off it instead of hitting it again.
                t += if (ad < 0.6f) 1.5f else ad * 0.85f
            }
            val te = if (hitGlass) t else tA
            segs.add(LaserSeg(x, y, x + dx * te, y + dy * te, alpha))
            x += dx * te
            y += dy * te
            total += te
            if (total > limit) break
            if (hitGlass) {
                val e = 1.5f
                var nx = fieldSd(x + e, y, n, k) - fieldSd(x - e, y, n, k)
                var ny = fieldSd(x, y + e, n, k) - fieldSd(x, y - e, n, k)
                val len = hypot(nx, ny)
                if (len < 1e-4f) break
                nx /= len; ny /= len
                // The normal that faces the incoming beam.
                val facing = if (dx * nx + dy * ny < 0f) 1f else -1f
                val fx = nx * facing
                val fy = ny * facing
                val cosI = -(dx * fx + dy * fy)
                val eta = if (!inside) 1f / index else index
                val kk = 1f - eta * eta * (1f - cosI * cosI)
                if (kk < 0f) {
                    val d2 = dx * fx + dy * fy
                    dx -= 2f * d2 * fx
                    dy -= 2f * d2 * fy
                } else {
                    val c = eta * cosI - sqrt(kk)
                    dx = eta * dx + c * fx
                    dy = eta * dy + c * fy
                    inside = !inside
                }
                alpha *= 0.97f
                val l = hypot(dx, dy)
                dx /= l; dy /= l
                x += dx * 0.8f
                y += dy * 0.8f
            } else {
                val d2 = dx * nax + dy * nay
                dx -= 2f * d2 * nax
                dy -= 2f * d2 * nay
                alpha *= 0.92f
                x += nax * 0.6f
                y += nay * 0.6f
            }
            if (alpha < 0.05f) break
        }
        return segs
    }

    /** The finger let go: it carries on sliding if the floor is slippery. */
    fun release(p: FluidPiece, velocity: Offset) {
        p.dragging = false
        val max = MAX_SPEED * density
        val speed = velocity.getDistance()
        p.vel = if (!inertiaOn) Offset.Zero else if (speed > max) velocity * (max / speed) else velocity
        wake()
    }

    /** How visible piece [i]'s own content is: it gives way while another piece's content is near. */
    fun contentAlpha(i: Int): Float {
        if (i >= pieces.size) return 1f
        var alpha = 1f
        for (j in 0 until min(i, pieces.size)) {
            val dist = hypot(pieces[i].centerX - pieces[j].centerX, pieces[i].centerY - pieces[j].centerY)
            val r = (min(pieces[i].width, pieces[i].height) + min(pieces[j].width, pieces[j].height)) * 0.5f * 0.8f
            alpha = min(alpha, ((dist - r) / (r * 0.6f)).coerceIn(0f, 1f))
        }
        return alpha
    }

    // Last of the properties: it uses all of them (it used to run before `templates` existed, and crashed).
    init {
        reset()
    }

    private companion object {
        const val MAX_LASER_HITS = 28
        const val MIN_SPEED = 12f      // dp/s: slower than this a sliding piece stops
        const val MAX_SPEED = 5000f    // dp/s
        const val IMPACT = 0.0011f     // squash speed per dp/s of impact
        const val MAX_SQUASH = 0.55f
        const val SQ_STIFFNESS = 520f
        const val SQ_DAMPING = 20f     // ratio ~0.44: bouncy
    }
}

@Composable
fun rememberGlassFluidState(): GlassFluidState {
    val d = LocalDensity.current.density
    return remember { GlassFluidState(d) }
}

/**
 * The prototype field: draggable pieces over a pattern. [reach] (dp) is the gap at which pieces
 * start reaching for each other, [responseMs] how slowly the blend settles, [softness] (dp) how
 * round the join is. [heightDp] and [widthFraction] (of the screen's width; more than 1 scrolls
 * sideways) set the size of the field.
 */
@Composable
fun GlassFluidField(
    state: GlassFluidState,
    reach: Float,
    responseMs: Float,
    softness: Float,
    wallsOn: Boolean,
    inertiaOn: Boolean,
    friction: Float,
    bounce: Float,
    laserOn: Boolean,
    laserAngle: Float,
    laserSpin: Float,
    glassIndex: Float,
    heightDp: Float,
    widthFraction: Float,
    modifier: Modifier = Modifier,
    content: @Composable (index: Int, tint: androidx.compose.ui.graphics.Color) -> Unit,
) {
    val density = LocalDensity.current
    state.reach = with(density) { reach.dp.toPx() }
    state.response = responseMs / 1000f
    state.softness = with(density) { softness.dp.toPx() }
    state.wallsOn = wallsOn
    state.inertiaOn = inertiaOn
    state.friction = friction
    state.bounce = bounce
    state.laserOn = laserOn
    state.laserAngle = laserAngle
    state.laserSpin = laserSpin
    state.glassIndex = glassIndex

    // The springs run while anything moves. One loop that is woken by a move (not restarted by it),
    // and that keeps going a few frames past the point where everything has settled, so the last
    // state is always drawn and nothing waits for some other redraw to show it.
    LaunchedEffect(state) {
        for (signal in state.wakeups) {
            var last = 0L
            var calm = 0
            while (calm < 4) {
                val frame = withFrameNanos { it }
                val dt = if (last == 0L) 1f / 60f else ((frame - last) / 1_000_000_000f).coerceAtMost(1f / 30f)
                last = frame
                calm = if (state.step(dt)) 0 else calm + 1
            }
        }
    }
    LaunchedEffect(reach, responseMs, softness, wallsOn, inertiaOn, friction, bounce, laserOn, laserAngle, laserSpin, glassIndex, heightDp, widthFraction) { state.wake() }

    val tint = MaterialTheme.colorScheme.onSurface
    // What the glass looks through: the pattern below, recorded here (the page's own backdrop does not
    // contain it, so the glass used to show nothing but the plain page).
    val behind = rememberLayerBackdrop()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fieldWidth = maxWidth * (widthFraction / 100f).coerceAtLeast(0.05f)
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Box(
                Modifier
                    .width(fieldWidth)
                    .height(heightDp.coerceAtLeast(20f).dp)
                    .onSizeChanged {
                        state.fieldW = it.width.toFloat()
                        state.fieldH = it.height.toFloat()
                        state.wake()
                    },
            ) {
                // Something to refract: bold bands, a grid and the walls (the page behind is almost plain).
                val bandA = MaterialTheme.colorScheme.primaryContainer
                val bandB = MaterialTheme.colorScheme.tertiaryContainer
                val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                val wallColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize().layerBackdrop(behind)) {
                    val step = 28.dp.toPx()
                    var x = -size.height
                    var n = 0
                    while (x < size.width) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(x, 0f); lineTo(x + step, 0f); lineTo(x + step + size.height, size.height); lineTo(x + size.height, size.height); close()
                        }
                        drawPath(path, if (n % 2 == 0) bandA else bandB)
                        x += step
                        n++
                    }
                    var gx = 0f
                    while (gx < size.width) { drawLine(line, Offset(gx, 0f), Offset(gx, size.height), 1.5.dp.toPx()); gx += 40.dp.toPx() }
                    var gy = 0f
                    while (gy < size.height) { drawLine(line, Offset(0f, gy), Offset(size.width, gy), 1.5.dp.toPx()); gy += 40.dp.toPx() }
                    if (state.wallsOn) {
                        for (w in state.wallRects(size.width, size.height)) {
                            drawRoundRect(
                                wallColor,
                                topLeft = Offset(w.left, w.top),
                                size = androidx.compose.ui.geometry.Size(w.width, w.height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        // Reading the springs here as well makes every step redraw the glass, whether or not
                        // the backdrop noticed the read inside its own effect block.
                        .drawBehind { state.tick }
                        .drawPlainBackdrop(
                            backdrop = behind,
                            shape = { RectangleShape },
                            effects = {
                                state.tick // follow the springs
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) try {
                                    val shader: RuntimeShader = obtainRuntimeShader("GlassFluid", FLUID_SHADER)
                                    val boxes = FloatArray(SHAPE_MAX * 4)
                                    val radii = FloatArray(SHAPE_MAX)
                                    val blends = FloatArray(SHAPE_MAX * SHAPE_MAX)
                                    val n = min(state.pieces.size, SHAPE_MAX)
                                    for (i in 0 until n) {
                                        val p = state.pieces[i]
                                        boxes[i * 4] = p.centerX
                                        boxes[i * 4 + 1] = p.centerY
                                        boxes[i * 4 + 2] = p.width / 2f
                                        boxes[i * 4 + 3] = p.height / 2f
                                        radii[i] = p.radius
                                        for (j in i + 1 until n) blends[i * SHAPE_MAX + j] = state.blendFor(i, j)
                                    }
                                    shader.setFloatUniform("pad", padding, padding)
                                    shader.setFloatUniform("count", n.toFloat())
                                    shader.setFloatUniform("box", boxes)
                                    shader.setFloatUniform("rad", radii)
                                    shader.setFloatUniform("k", blends)
                                    shader.setFloatUniform("refractionHeight", 14.dp.toPx())
                                    shader.setFloatUniform("refractionAmount", -20.dp.toPx())
                                    shader.setFloatUniform("rimWidth", 2.5.dp.toPx())
                                    effect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
                                } catch (e: Throwable) {
                                    // A shader the device refuses must not take the app down: the field is just not drawn.
                                    android.util.Log.e("GlassFluid", "shader failed", e)
                                }
                            },
                        ),
                )
                // The laser, over the glass and under the pieces' own content.
                if (laserOn) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        state.tick
                        val beam = androidx.compose.ui.graphics.Color(0xFFFF3B30)
                        val segs = state.laserPath()
                        for (s in segs) {
                            val a = Offset(s.x0, s.y0)
                            val b = Offset(s.x1, s.y1)
                            drawLine(beam.copy(alpha = 0.16f * s.alpha), a, b, 11.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            drawLine(beam.copy(alpha = 0.4f * s.alpha), a, b, 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            drawLine(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f * s.alpha), a, b, 1.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        }
                        segs.firstOrNull()?.let { drawCircle(beam, 5.dp.toPx(), Offset(it.x0, it.y0)) }
                    }
                }
                state.pieces.forEachIndexed { index, piece ->
                    androidx.compose.runtime.key(piece) {
                        Box(
                            Modifier
                                .offset { IntOffset(piece.left.roundToInt(), piece.top.roundToInt()) }
                                .size(
                                    with(density) { piece.width.toDp() },
                                    with(density) { piece.height.toDp() },
                                )
                                .pointerInput(piece, state) {
                                    val tracker = VelocityTracker()
                                    detectDragGestures(
                                        onDragStart = {
                                            tracker.resetTracking()
                                            piece.dragging = true
                                            piece.vel = Offset.Zero
                                            piece.target = Offset(piece.centerX, piece.centerY)
                                            state.wake()
                                        },
                                        onDragEnd = {
                                            val v = tracker.calculateVelocity()
                                            state.release(piece, Offset(v.x, v.y))
                                        },
                                        onDragCancel = { state.release(piece, Offset.Zero) },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            piece.target = piece.target + drag
                                            tracker.addPosition(change.uptimeMillis, piece.target)
                                            state.wake()
                                        },
                                    )
                                }
                                .graphicsLayer { alpha = state.contentAlpha(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            content(index, tint)
                        }
                    }
                }
            }
        }
    }
}
