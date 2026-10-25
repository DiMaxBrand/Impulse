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
 * One member of a fluid field. [pos] is the top-left of its resting body (px); while it is pressed
 * against something the body is squashed ([sqX], [sqY]) with the side that touches staying put.
 */
@Stable
class FluidPiece(val baseW: Float, val baseH: Float, val baseR: Float, x: Float, y: Float) {
    var pos by mutableStateOf(Offset(x, y))
    var vel = Offset.Zero
    var dragging = false

    /** Where the finger wants the body to be (it can be beyond a wall). */
    var target = Offset(x, y)
    var sqX by mutableFloatStateOf(0f)
    var sqY by mutableFloatStateOf(0f)
    var sqVX = 0f
    var sqVY = 0f
    var contactX = 0
    var contactY = 0

    val width get() = baseW * (1f - sqX) * (1f + 0.3f * sqY)
    val height get() = baseH * (1f - sqY) * (1f + 0.3f * sqX)
    val radius get() = min(baseR, min(width, height) / 2f)
    val left get() = when {
        contactX > 0 -> pos.x + baseW - width
        contactX < 0 -> pos.x
        else -> pos.x + (baseW - width) / 2f
    }
    val top get() = when {
        contactY > 0 -> pos.y + baseH - height
        contactY < 0 -> pos.y
        else -> pos.y + (baseH - height) / 2f
    }
    val centerX get() = left + width / 2f
    val centerY get() = top + height / 2f
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

    private class Slide(val pos: Offset, val hx: Int, val hy: Int)

    /** Moves a piece towards [to] without going through the field's edges or a wall; says which side stopped it. */
    private fun slide(p: FluidPiece, to: Offset, walls: List<Rect>): Slide {
        val bw = p.baseW
        val bh = p.baseH
        val x = p.pos.x
        val y = p.pos.y
        var hx = 0
        var hy = 0
        var nx = to.x
        if (fieldW > 0f) {
            if (nx < 0f) { nx = 0f; hx = -1 }
            if (nx + bw > fieldW) { nx = max(fieldW - bw, 0f); hx = 1 }
        }
        for (w in walls) {
            if (y < w.bottom && y + bh > w.top) {
                if (x + bw <= w.left + 0.5f && nx + bw > w.left) { nx = w.left - bw; hx = 1 }
                else if (x >= w.right - 0.5f && nx < w.right) { nx = w.right; hx = -1 }
            }
        }
        var ny = to.y
        if (fieldH > 0f) {
            if (ny < 0f) { ny = 0f; hy = -1 }
            if (ny + bh > fieldH) { ny = max(fieldH - bh, 0f); hy = 1 }
        }
        for (w in walls) {
            if (nx < w.right && nx + bw > w.left) {
                if (y + bh <= w.top + 0.5f && ny + bh > w.top) { ny = w.top - bh; hy = 1 }
                else if (y >= w.bottom - 0.5f && ny < w.bottom) { ny = w.bottom; hy = -1 }
            }
        }
        return Slide(Offset(nx, ny), hx, hy)
    }

    /** A piece that ended up inside a wall (the walls were just switched on, or the field changed) steps out the shortest way. */
    private fun escape(p: FluidPiece, walls: List<Rect>) {
        for (w in walls) {
            val r = Rect(p.pos.x, p.pos.y, p.pos.x + p.baseW, p.pos.y + p.baseH)
            if (!w.overlaps(r)) continue
            val left = r.right - w.left
            val right = w.right - r.left
            val up = r.bottom - w.top
            val down = w.bottom - r.top
            val m = min(min(left, right), min(up, down))
            p.pos = when (m) {
                left -> Offset(w.left - p.baseW, p.pos.y)
                right -> Offset(w.right, p.pos.y)
                up -> Offset(p.pos.x, w.top - p.baseH)
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
            val res = slide(p, p.target, walls)
            if (res.pos != p.pos) p.pos = res.pos
            // The finger can go on past a wall: the difference is how hard the piece is pressed.
            if (res.hx != 0) penX = p.target.x - res.pos.x
            if (res.hy != 0) penY = p.target.y - res.pos.y
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
        val tx = if (wallsOn) (abs(penX) / p.baseW).coerceAtMost(MAX_SQUASH) else 0f
        val ty = if (wallsOn) (abs(penY) / p.baseH).coerceAtMost(MAX_SQUASH) else 0f
        if (penX != 0f) p.contactX = sign(penX).toInt()
        if (penY != 0f) p.contactY = sign(penY).toInt()
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
        if (ringing) moving = true
        else {
            p.sqX = 0f; p.sqY = 0f; p.sqVX = 0f; p.sqVY = 0f
            if (!p.dragging) { p.contactX = 0; p.contactY = 0 }
        }
        return moving
    }

    /** One physics step; true while anything is still moving. */
    fun step(dt: Float): Boolean {
        var moving = false
        val walls = walls()
        if (walls.isNotEmpty()) for (p in pieces) if (!p.dragging) escape(p, walls)
        for (p in pieces) if (advance(p, dt, walls)) moving = true
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
    LaunchedEffect(reach, responseMs, softness, wallsOn, inertiaOn, friction, bounce, heightDp, widthFraction) { state.wake() }

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
                                            piece.target = piece.pos
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
