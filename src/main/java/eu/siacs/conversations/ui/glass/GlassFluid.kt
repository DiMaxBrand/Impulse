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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.kyant.backdrop.backdrops.layerBackdrop
import org.jbox2d.callbacks.ContactImpulse
import org.jbox2d.callbacks.ContactListener
import org.jbox2d.collision.Manifold
import org.jbox2d.collision.WorldManifold
import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.Filter
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import org.jbox2d.dynamics.contacts.Contact
import org.jbox2d.dynamics.joints.MouseJoint
import org.jbox2d.dynamics.joints.MouseJointDef
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

private const val SHAPE_MAX = 16


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
    /** Turn of the body, radians (clockwise on screen); only the rigid-body engine turns pieces. */
    var angle by mutableFloatStateOf(0f)
    var sqX by mutableFloatStateOf(0f)
    var sqY by mutableFloatStateOf(0f)
    var sqVX = 0f
    var sqVY = 0f
    var contactX = 0
    var contactY = 0

    /** The body in the rigid-body world, while that engine is on. */
    var body: Body? = null

    val width get() = baseW * (1f - sqX) * (1f + 0.3f * sqY)
    val height get() = baseH * (1f - sqY) * (1f + 0.3f * sqX)
    val radius get() = min(baseR, min(width, height) / 2f)
    val left get() = pos.x
    val top get() = pos.y
    val centerX get() = pos.x + width / 2f
    val centerY get() = pos.y + height / 2f
}

/** Signed distance from the point to the piece's body: negative inside. The body is turned by [FluidPiece.angle]. */
internal fun pieceSd(p: FluidPiece, x: Float, y: Float): Float {
    val dx = x - p.centerX
    val dy = y - p.centerY
    val c = kotlin.math.cos(p.angle)
    val s = kotlin.math.sin(p.angle)
    val lx = c * dx + s * dy
    val ly = -s * dx + c * dy
    val qx = abs(lx) - (p.width / 2f - p.radius)
    val qy = abs(ly) - (p.height / 2f - p.radius)
    return hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - p.radius
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
class GlassFluidState(private val density: Float) : FluidScene {
    val pieces = mutableStateListOf<FluidPiece>()

    /** The bridges between the pieces (a piece's id is its place in the list). */
    private val bridges = FluidBridges()
    override var tick by mutableIntStateOf(0)
        private set
    override val frostActive: Boolean get() = false

    private val shapeList = ArrayList<FluidShape>()

    /** True while something else draws the pieces (the playground's tab morph): they and their content are not drawn. */
    var hidden by mutableStateOf(false)

    /** The pieces as shapes for the glass layer, right now. */
    override fun shapes(): List<FluidShape> {
        if (hidden) return emptyList()
        val n = pieces.size
        while (shapeList.size < n) shapeList.add(FluidShape(shapeList.size))
        while (shapeList.size > n) shapeList.removeAt(shapeList.lastIndex)
        for (i in 0 until n) {
            val p = pieces[i]
            val sh = shapeList[i]
            sh.cx = p.centerX
            sh.cy = p.centerY
            sh.hw = p.width / 2f
            sh.hh = p.height / 2f
            sh.radius = p.radius
            sh.angle = p.angle
        }
        return shapeList
    }

    override fun bridgeBlend(a: FluidShape, b: FluidShape): Float = bridges.blend(a, b)

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
    var bounce = 0.7f

    /** Pieces are solid: they cannot pass through each other, they pile up. */
    var solidOn = false
    /** No bridges: pieces never join into one body. */
    var mergeOff = false

    var gravityOn = false
    /** dp/s squared. */
    var gravity = 1200f
    /** Degrees: 90 is down. */
    var gravityAngle = 90f
    /** The phone's own tilt (a fraction of g, screen axes), when it sets the direction. */
    var tilt: Offset? = null

    private fun gravityVec(): Offset {
        if (!gravityOn) return Offset.Zero
        val t = tilt
        if (t != null) return t * (gravity * density)
        val a = Math.toRadians(gravityAngle.toDouble())
        return Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat()) * (gravity * density)
    }

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
        letGo()
        for (p in pieces) p.body?.let { world?.destroyBody(it) }
        pieces.clear()
        nextTemplate = 0
        clearPairs()
        repeat(3) { add() }
    }

    private fun clearPairs() {
        bridges.clear()
    }

    private fun clearPairsOf(index: Int) {
        bridges.forget(index)
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
        // Beyond the first few anchors: a grid over the field.
        val grid = ArrayList<Pair<Float, Float>>(anchors)
        run {
            var gy = 0.1f
            while (gy <= 0.9f) {
                var gx = 0.1f
                while (gx <= 0.9f) { grid.add(gx to gy); gx += 0.16f }
                gy += 0.16f
            }
        }
        var spot: Offset? = null
        for (strict in listOf(true, false)) {
            for ((ax, ay) in grid) {
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
        val last = pieces[pieces.lastIndex]
        if (grabbed === last) letGo()
        last.body?.let { world?.destroyBody(it) }
        pieces.removeAt(pieces.lastIndex)
        wake()
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
        } else if (inertiaOn || gravityOn) {
            val g = gravityVec()
            var v = p.vel + g * dt
            val res = slide(p, p.pos + v * dt, walls)
            if (res.pos != p.pos) p.pos = res.pos
            // With no friction at all nothing is lost, not even in a bounce: the piece never stops.
            val rest = if (friction <= 0f) 1f else bounce
            val still = REST_SPEED * density
            if (res.hx != 0) {
                if (g.x * res.hx > 0f && abs(v.x) < still) v = Offset(0f, v.y) // lying against it under gravity
                else {
                    p.sqVX += abs(v.x) / density * IMPACT; p.contactX = res.hx
                    v = Offset(-v.x * rest, v.y)
                }
            }
            if (res.hy != 0) {
                if (g.y * res.hy > 0f && abs(v.y) < still) v = Offset(v.x, 0f)
                else {
                    p.sqVY += abs(v.y) / density * IMPACT; p.contactY = res.hy
                    v = Offset(v.x, -v.y * rest)
                }
            }
            v = v * exp(-friction * dt)
            val top = MAX_SPEED * density
            val sp = v.getDistance()
            v = if (sp > top) v * (top / sp) else v
            // Slower than this without gravity pushing it: it has stopped (a piece lying on the floor too).
            val lying = (res.hx != 0 && g.x * res.hx > 0f) || (res.hy != 0 && g.y * res.hy > 0f)
            val slow = if (friction > 0f) MIN_SPEED * density else 0.01f
            if (v.getDistance() <= slow && (g == Offset.Zero || lying)) v = Offset.Zero
            p.vel = v
            moving = v != Offset.Zero
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
    private fun stepSoft(dt: Float): Boolean {
        var moving = false
        val walls = walls()
        fun rect(q: FluidPiece) = Rect(q.pos.x, q.pos.y, q.pos.x + q.width, q.pos.y + q.height)
        if (solidOn) {
            // Solid pieces that start (or end up) inside one another: the later one steps out.
            for (i in pieces.indices) if (!pieces[i].dragging) escape(pieces[i], walls + (0 until i).map { rect(pieces[it]) })
        } else if (walls.isNotEmpty()) {
            for (p in pieces) if (!p.dragging) escape(p, walls)
        }
        for (p in pieces) {
            val obstacles = if (solidOn) walls + pieces.filter { it !== p }.map { rect(it) } else walls
            if (advance(p, dt, obstacles)) moving = true
        }
        return moving
    }

    fun step(dt: Float): Boolean {
        var moving: Boolean
        if (rigidOn) {
            if (!rigidWasOn) enterRigid()
            moving = stepRigid(dt)
        } else {
            if (rigidWasOn) leaveRigid()
            moving = stepSoft(dt)
        }
        if (laserOn && laserSpin != 0f) {
            laserPhase += laserSpin * dt
            moving = true
        }
        bridges.reach = reach
        bridges.softness = softness
        bridges.response = response
        if (bridges.step(dt, shapes(), mergeOff)) moving = true
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

    private fun sdPiece(p: FluidPiece, x: Float, y: Float): Float = pieceSd(p, x, y)

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
        val fluidShapes = shapes()
        for (i in 0 until n) for (j in i + 1 until n) k[i * SHAPE_MAX + j] = bridges.blend(fluidShapes[i], fluidShapes[j])
        // The field as the shader draws it, from numbers worked out once for the whole beam: the turn of
        // each piece, and only the bridges that exist now.
        val pcx = FloatArray(n) { pieces[it].centerX }
        val pcy = FloatArray(n) { pieces[it].centerY }
        val phw = FloatArray(n) { pieces[it].width / 2f }
        val phh = FloatArray(n) { pieces[it].height / 2f }
        val prr = FloatArray(n) { pieces[it].radius }
        val pcs = FloatArray(n) { kotlin.math.cos(pieces[it].angle) }
        val psn = FloatArray(n) { kotlin.math.sin(pieces[it].angle) }
        val bridgeI = ArrayList<Int>()
        val bridgeJ = ArrayList<Int>()
        val bridgeK = ArrayList<Float>()
        for (i in 0 until n) for (j in i + 1 until n) {
            val kk = k[i * SHAPE_MAX + j]
            if (kk > 0.001f) { bridgeI.add(i); bridgeJ.add(j); bridgeK.add(kk) }
        }
        val dd = FloatArray(n)
        fun sdAll(x: Float, y: Float): Float {
            var r = 1e9f
            for (i in 0 until n) {
                val ddx = x - pcx[i]
                val ddy = y - pcy[i]
                val lx = pcs[i] * ddx + psn[i] * ddy
                val ly = -psn[i] * ddx + pcs[i] * ddy
                val qx = abs(lx) - (phw[i] - prr[i])
                val qy = abs(ly) - (phh[i] - prr[i])
                dd[i] = hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - prr[i]
                r = min(r, dd[i])
            }
            for (b in bridgeI.indices) {
                val kk = bridgeK[b]
                val a = dd[bridgeI[b]]
                val c = dd[bridgeJ[b]]
                val h = max(kk - abs(a - c), 0f) / kk
                r = min(r, min(a, c) - h * h * h * kk / 6f)
            }
            return r
        }
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
        var inside = sdAll(x, y) < 0f
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
                val ad = abs(sdAll(x + dx * t, y + dy * t))
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
                var nx = sdAll(x + e, y) - sdAll(x - e, y)
                var ny = sdAll(x, y + e) - sdAll(x, y - e)
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

    // ---- the rigid-body engine ---------------------------------------------------------------
    //
    // Real 2D physics (JBox2D): each piece is a rigid body shaped like what is drawn (a circle, or a
    // rounded rectangle made of two boxes and four circles), so pieces knock, stack, topple, slide off
    // each other where their curves say they should, and turn. The finger holds a piece with a spring
    // (a mouse joint), at the touch point when the piece may turn, so a pull off-centre spins it.
    // Squashing is only for show here: a hard hit flattens the picture a little, then it rings out.

    /** Use the rigid-body engine instead of the simple one. */
    var rigidOn = false
    /** 0: pieces never turn; 1: stiff (round and square ones turn only when knocked); 2: free. */
    var rotationLevel = 1

    private var rigidWasOn = false
    private var world: World? = null
    private var ground: Body? = null
    private val statics = ArrayList<Body>()
    private var staticsKey = ""
    private var settingsKey = ""
    private var lastGravity = Offset.Zero
    private var restDamping = 20f
    private var accum = 0f
    private var mouse: MouseJoint? = null
    private var grabbed: FluidPiece? = null
    private var anchorStart = Offset.Zero
    private var fingerStart = Offset.Zero

    private val scale get() = 100f * density

    private val contactListener = object : ContactListener {
        override fun beginContact(contact: Contact) {}
        override fun endContact(contact: Contact) {}
        override fun preSolve(contact: Contact, oldManifold: Manifold) {}
        override fun postSolve(contact: Contact, impulse: ContactImpulse) {
            var total = 0f
            for (i in 0 until impulse.count) total += impulse.normalImpulses[i]
            if (total <= 0f) return
            val wm = WorldManifold()
            contact.getWorldManifold(wm)
            val n = wm.normal
            for (body in arrayOf(contact.fixtureA.body, contact.fixtureB.body)) {
                val piece = body.userData as? FluidPiece ?: continue
                val mass = body.mass
                if (mass <= 0f) continue
                val hit = total / mass // m/s: how hard it was stopped
                if (hit < HIT_MIN) continue
                // Flatten along the body's own axis that the hit came along.
                val local = body.getLocalVector(Vec2(n.x, n.y))
                val kick = (hit * 100f * IMPACT).coerceAtMost(MAX_KICK)
                if (abs(local.x) > abs(local.y)) piece.sqVX += kick else piece.sqVY += kick
            }
        }
    }

    private fun worldOrNull(): World? {
        world?.let { return it }
        return try {
            val w = World(Vec2(0f, 0f))
            w.setContactListener(contactListener)
            ground = w.createBody(BodyDef())
            world = w
            w
        } catch (t: Throwable) {
            android.util.Log.e("GlassFluid", "no physics world", t)
            null
        }
    }

    private fun enterRigid() {
        rigidWasOn = true
        staticsKey = ""
        settingsKey = ""
        accum = 0f
        for (p in pieces) { p.body = null; p.angle = 0f; p.vel = Offset.Zero }
    }

    private fun leaveRigid() {
        rigidWasOn = false
        letGo()
        val w = world
        if (w != null) {
            for (p in pieces) p.body?.let { w.destroyBody(it) }
            for (b in statics) w.destroyBody(b)
        }
        statics.clear()
        staticsKey = ""
        for (p in pieces) { p.body = null; p.angle = 0f }
    }

    private fun frictionFor(): Float = if (inertiaOn) 0.05f else 0.6f

    private fun pieceFilter(f: Filter) {
        f.categoryBits = 2
        // Pieces always meet the field; each other only when they are solid.
        f.maskBits = 1 or (if (solidOn) 2 else 0)
    }

    private fun addShapes(body: Body, p: FluidPiece) {
        val hw = p.baseW / 2f / scale
        val hh = p.baseH / 2f / scale
        val r = min(p.baseR / scale, min(hw, hh))
        fun fix(shape: org.jbox2d.collision.shapes.Shape) {
            val fd = FixtureDef()
            fd.shape = shape
            fd.density = 1f
            fd.friction = frictionFor()
            fd.restitution = bounce.coerceIn(0f, 1.3f)
            pieceFilter(fd.filter)
            body.createFixture(fd)
        }
        fun circle(cx: Float, cy: Float, radius: Float) {
            val c = CircleShape()
            c.m_radius = radius
            c.m_p.set(cx, cy)
            fix(c)
        }
        if (abs(hw - hh) < 0.002f && r >= min(hw, hh) - 0.002f) {
            circle(0f, 0f, hw)
            return
        }
        if (hh - r > 0.003f) { val b = PolygonShape(); b.setAsBox(hw, hh - r); fix(b) }
        if (hw - r > 0.003f) { val b = PolygonShape(); b.setAsBox(hw - r, hh); fix(b) }
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) circle(sx * (hw - r), sy * (hh - r), r)
    }

    private fun createBody(w: World, p: FluidPiece): Body {
        val def = BodyDef()
        def.type = BodyType.DYNAMIC
        def.position.set(p.centerX / scale, p.centerY / scale)
        def.angle = p.angle
        def.userData = p
        def.fixedRotation = rotationLevel == 0
        val body = w.createBody(def)
        addShapes(body, p)
        return body
    }

    private fun rebuildStatics(w: World) {
        for (b in statics) w.destroyBody(b)
        statics.clear()
        val fw = fieldW / scale
        val fh = fieldH / scale
        fun box(cx: Float, cy: Float, hx: Float, hy: Float) {
            val def = BodyDef()
            def.position.set(cx, cy)
            val b = w.createBody(def)
            val shape = PolygonShape()
            shape.setAsBox(hx, hy)
            val fd = FixtureDef()
            fd.shape = shape
            fd.friction = frictionFor()
            fd.restitution = bounce.coerceIn(0f, 1.3f)
            fd.filter.categoryBits = 1
            b.createFixture(fd)
            statics.add(b)
        }
        // The edges of the field: thick boxes just outside it.
        box(-0.5f, fh / 2f, 0.5f, fh / 2f + 1f)
        box(fw + 0.5f, fh / 2f, 0.5f, fh / 2f + 1f)
        box(fw / 2f, -0.5f, fw / 2f + 1f, 0.5f)
        box(fw / 2f, fh + 0.5f, fw / 2f + 1f, 0.5f)
        if (wallsOn) {
            for (r in wallRects(fieldW, fieldH)) {
                box(r.center.x / scale, r.center.y / scale, r.width / 2f / scale, r.height / 2f / scale)
            }
        }
    }

    private fun wakeAll() {
        for (p in pieces) p.body?.isAwake = true
    }

    private fun syncRigid(w: World) {
        val key = "$fieldW,$fieldH,$wallsOn"
        if (key != staticsKey) {
            rebuildStatics(w)
            staticsKey = key
            // A field that got smaller: pieces outside it are put back in.
            for (p in pieces) p.body?.let { b ->
                val pos = b.position
                val nx = pos.x.coerceIn(p.baseW / 2f / scale, max(fieldW - p.baseW / 2f, p.baseW / 2f) / scale)
                val ny = pos.y.coerceIn(p.baseH / 2f / scale, max(fieldH - p.baseH / 2f, p.baseH / 2f) / scale)
                if (nx != pos.x || ny != pos.y) b.setTransform(Vec2(nx, ny), b.angle)
            }
            wakeAll()
        }
        for (p in pieces) if (p.body == null) p.body = createBody(w, p)
        val set = "$solidOn|$bounce|$inertiaOn|$rotationLevel|$friction|$gravityOn"
        if (set != settingsKey) {
            settingsKey = set
            val damp = if (inertiaOn || gravityOn) friction.coerceAtLeast(0f) else 20f
            restDamping = damp
            val angDamp = when (rotationLevel) { 1 -> 6f; else -> 0.8f }
            for (p in pieces) p.body?.let { b ->
                // Held by the finger a piece is not slowed: only once it is let go.
                b.linearDamping = if (p === grabbed) min(damp, 3f) else damp
                b.angularDamping = angDamp
                if (b.isFixedRotation != (rotationLevel == 0)) {
                    b.isFixedRotation = rotationLevel == 0
                    if (rotationLevel == 0) {
                        b.setTransform(b.position, 0f)
                        b.angularVelocity = 0f
                        p.angle = 0f
                    }
                }
                var f = b.fixtureList
                while (f != null) {
                    f.friction = frictionFor()
                    f.restitution = bounce.coerceIn(0f, 1.3f)
                    val fl = Filter()
                    fl.set(f.filterData)
                    pieceFilter(fl)
                    f.filterData = fl
                    f = f.next
                }
            }
            // The field's own edges and walls follow friction and bounce too.
            staticsKey = ""
            wakeAll()
        }
        val g = gravityVec()
        if (g != lastGravity) {
            lastGravity = g
            w.gravity = Vec2(g.x / scale, g.y / scale)
            wakeAll()
        }
    }

    private fun springSquash(p: FluidPiece, dt: Float): Boolean {
        var left = dt
        while (left > 0f) {
            val h = min(left, 1f / 240f)
            p.sqVX += (SQ_STIFFNESS * (0f - p.sqX) - SQ_DAMPING * p.sqVX) * h
            p.sqVY += (SQ_STIFFNESS * (0f - p.sqY) - SQ_DAMPING * p.sqVY) * h
            p.sqX = (p.sqX + p.sqVX * h).coerceIn(-0.1f, 0.7f)
            p.sqY = (p.sqY + p.sqVY * h).coerceIn(-0.1f, 0.7f)
            left -= h
        }
        val ringing = abs(p.sqX) > 0.004f || abs(p.sqY) > 0.004f || abs(p.sqVX) > 0.05f || abs(p.sqVY) > 0.05f
        if (!ringing) { p.sqX = 0f; p.sqY = 0f; p.sqVX = 0f; p.sqVY = 0f }
        return ringing
    }

    private fun stepRigid(dt: Float): Boolean {
        if (fieldW <= 0f || fieldH <= 0f) return false
        val w = worldOrNull() ?: return false
        syncRigid(w)
        accum += dt
        var n = 0
        while (accum >= FIXED_STEP && n < 6) {
            w.step(FIXED_STEP, 10, 6)
            accum -= FIXED_STEP
            n++
        }
        if (accum > FIXED_STEP) accum = 0f
        var busy = grabbed != null
        for (p in pieces) {
            val b = p.body ?: continue
            val pos = b.position
            if (springSquash(p, dt)) busy = true
            p.pos = Offset(pos.x * scale - p.width / 2f, pos.y * scale - p.height / 2f)
            if (rotationLevel != 0 && p.angle != b.angle) p.angle = b.angle
            if (b.isAwake) busy = true
        }
        return busy
    }

    /** The piece under a point (the top-most), or null. */
    fun pieceAt(p: Offset): FluidPiece? {
        for (i in pieces.indices.reversed()) if (pieceSd(pieces[i], p.x, p.y) < 0f) return pieces[i]
        return null
    }

    /** The finger goes down on [piece] at [at] (field pixels). */
    fun grab(piece: FluidPiece, at: Offset) {
        val w = world ?: return
        val body = piece.body ?: return
        letGo()
        val longSide = max(piece.baseW, piece.baseH)
        val shortSide = min(piece.baseW, piece.baseH)
        val elongated = longSide / shortSide > 1.15f
        // Held off-centre a piece turns; round and square ones are held by the middle when stiff.
        val atTouch = rotationLevel == 2 || (rotationLevel == 1 && elongated)
        val anchor = if (atTouch) at else Offset(piece.centerX, piece.centerY)
        anchorStart = anchor
        fingerStart = at
        val def = MouseJointDef()
        def.bodyA = ground
        def.bodyB = body
        def.target.set(anchor.x / scale, anchor.y / scale)
        def.maxForce = body.mass * 160f
        def.frequencyHz = 7f
        def.dampingRatio = 0.85f
        mouse = w.createJoint(def) as? MouseJoint
        body.isBullet = true
        body.isAwake = true
        body.linearDamping = min(restDamping, 3f)
        grabbed = piece
        piece.dragging = true
        wake()
    }

    fun dragTo(at: Offset) {
        val m = mouse ?: return
        val t = anchorStart + (at - fingerStart)
        m.setTarget(Vec2(t.x / scale, t.y / scale))
        grabbed?.body?.isAwake = true
        wake()
    }

    fun letGo() {
        mouse?.let { world?.destroyJoint(it) }
        mouse = null
        grabbed?.let {
            it.dragging = false
            it.body?.isBullet = false
            it.body?.linearDamping = restDamping
        }
        grabbed = null
        wake()
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
        if (hidden) return 0f
        if (i >= pieces.size || solidOn || mergeOff) return 1f
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
        const val FIXED_STEP = 1f / 120f
        const val HIT_MIN = 0.7f       // m/s: a hit softer than this (a piece resting) does not flatten anything
        const val MAX_KICK = 14f
        const val REST_SPEED = 40f     // dp/s: a piece falling slower than this onto the floor lies there
        const val MIN_SPEED = 12f      // dp/s: slower than this a sliding piece stops
        const val MAX_SPEED = 5000f    // dp/s
        const val IMPACT = 0.0075f     // squash speed per dp/s of impact: about half the body flattens at 1500 dp/s
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
    rigidOn: Boolean,
    rotationLevel: Int,
    solidOn: Boolean,
    mergeOff: Boolean,
    gravityOn: Boolean,
    gravity: Float,
    gravityAngle: Float,
    tiltOn: Boolean,
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
    state.rigidOn = rigidOn
    state.rotationLevel = rotationLevel
    state.solidOn = solidOn
    state.mergeOff = mergeOff
    state.gravityOn = gravityOn
    state.gravity = gravity
    state.gravityAngle = gravityAngle
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
    LaunchedEffect(rigidOn, rotationLevel, reach, responseMs, softness, wallsOn, inertiaOn, friction, bounce, rigidOn, rotationLevel, solidOn, mergeOff, gravityOn, gravity, gravityAngle, tiltOn, laserOn, laserAngle, laserSpin, glassIndex, heightDp, widthFraction) { state.wake() }

    // The phone's tilt can be the direction of gravity.
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(state, gravityOn, tiltOn) {
        val manager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val sensor = manager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        if (!(gravityOn && tiltOn) || manager == null || sensor == null) {
            state.tilt = null
            onDispose { }
        } else {
            val listener = object : android.hardware.SensorEventListener {
                override fun onSensorChanged(e: android.hardware.SensorEvent) {
                    // Gravity in screen axes (x right, y down) from the accelerometer's reading, which is its opposite.
                    val next = Offset(-e.values[0] / 9.81f, e.values[1] / 9.81f)
                    val old = state.tilt
                    if (old == null || (next - old).getDistance() > 0.02f) {
                        state.tilt = next
                        state.wake()
                    }
                }
                override fun onAccuracyChanged(s: android.hardware.Sensor?, accuracy: Int) {}
            }
            manager.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_GAME)
            onDispose {
                manager.unregisterListener(listener)
                state.tilt = null
            }
        }
    }
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
                // All the pieces are one glass body: see FluidGlass.kt.
                FluidGlassLayer(
                    backdrop = behind,
                    scene = state,
                    look = remember { FluidLook(refractionHeight = 14.dp, refractionAmount = 20.dp) },
                    modifier = Modifier.fillMaxSize(),
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
                                .then(if (rigidOn) Modifier else Modifier.pointerInput(piece, state) {
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
                                })
                                .graphicsLayer {
                                    alpha = state.contentAlpha(index)
                                    rotationZ = Math.toDegrees(piece.angle.toDouble()).toFloat()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            content(index, tint)
                        }
                    }
                }
                // Rigid bodies: one gesture over the whole field (the pieces turn, so a gesture
                // inside a piece would turn with it). A touch that misses every piece is left alone.
                if (rigidOn) {
                    Box(
                        Modifier.fillMaxSize().pointerInput(state) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val hit = state.pieceAt(down.position) ?: return@awaitEachGesture
                                down.consume()
                                state.grab(hit, down.position)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    if (!change.pressed) break
                                    state.dragTo(change.position)
                                }
                                state.letGo()
                            }
                        },
                    )
                }
            }
        }
    }
}
