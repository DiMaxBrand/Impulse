package eu.siacs.conversations.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.util.lerp
import eu.siacs.conversations.entities.Message
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.hypot

/** Slow-fast-slow, never linear — linear motion on something this small reads as robotic. */
private val StandardEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** The four message statuses that form one continuous story — waiting dots morphing into a
 * checkmark, growing a second checkmark, then turning green — as opposed to upload/error/p2p
 * icons, which are unrelated glyphs a morph wouldn't make sense for. */
enum class CheckmarkPhase { WAITING, SENT, DELIVERED, READ }

fun checkmarkPhaseForStatus(status: Int): CheckmarkPhase? = when (status) {
    Message.STATUS_WAITING -> CheckmarkPhase.WAITING
    Message.STATUS_SEND -> CheckmarkPhase.SENT
    Message.STATUS_SEND_RECEIVED -> CheckmarkPhase.DELIVERED
    Message.STATUS_SEND_DISPLAYED -> CheckmarkPhase.READ
    else -> null
}

// All geometry below is measured directly off the real Material Symbols paths this icon set
// already ships (ic_more_horiz_24dp, ic_done_24dp, ic_done_all_24dp — 960x960 viewport, rescaled
// here to a 24-unit space to match this file's drawing convention), not eyeballed:
//   - dots: 3 circles, centers (240,480)/(480,480)/(720,480), radius 80  -> 24-space r=2.0
//   - checkmark: a round-capped/round-joined stroke, centerline points recovered from the two
//     edges flanking each rounded cap in ic_done_24dp's outline; average cap radius ~39.8,
//     i.e. stroke width ~1.99 (24-space) — notably thinner than a dot's own diameter (4.0).
//   - ic_done_all_24dp's second (fully visible) checkmark is exactly ic_done_24dp's shape offset
//     by (+112.8, +0.8) in the 960 viewport -> (+2.82, +0.02) in 24-space; the first checkmark
//     sits at ic_done_24dp's own unshifted position, with the second one's shape cut out of it
//     wherever they overlap — that's the "trace" look, not two full opaque copies.
private val DOT1 = Offset(6f, 12f)
private val DOT2 = Offset(12f, 12f)
private val DOT3 = Offset(18f, 12f)
private const val DOT_RADIUS = 2f

private val CHECK_P0 = Offset(5.275f, 12.2875f) // start
private val CHECK_P1 = Offset(9.55f, 16.6333f) // vertex
private val CHECK_P2 = Offset(18.725f, 7.3875f) // end
private const val CHECK_STROKE_WIDTH = 1.99f
private val DOUBLE_CHECK_OFFSET = Offset(2.82f, 0.02f)

/**
 * Draws the waiting/sent/delivered/read sequence as one continuously-morphing glyph instead of
 * swapping between four unrelated icons:
 *
 * - Waiting → sent: the three dots reposition onto the checkmark's three key points (start,
 *   vertex, end) first, then two strokes grow between them — left dot into the short down-stroke,
 *   the vertex/end dot pair into the long up-stroke. A round stroke cap makes a zero-length
 *   stroke render as a filled circle, so "dot becomes line" falls out of one drawLine() call —
 *   the stroke also thins from the dot's own diameter down to the checkmark's real (thinner)
 *   width over the same motion, since the two aren't actually the same thickness.
 * - Sent → delivered: a second checkmark starts exactly on top of the first — at that position
 *   it's fully hidden, matching a single checkmark — and slides right into the real double-check
 *   layout, where it is the fully-visible (front) stroke and the original checkmark becomes the
 *   partially-hidden (back) one behind it, clipped wherever the new one covers it — matching the
 *   real icon's own technique instead of two flatly-overlapping copies.
 * - Delivered → read: both strokes bounce (quick zoom in, bouncy spring back to rest) while
 *   tinting from gray to green, with the stroke going a bit heavier at the same time so the two
 *   states stay easy to tell apart even for someone not distinguishing the color.
 */
@Composable
fun MessageStatusIcon(
    status: Int,
    grayColor: Color,
    successColor: Color,
    modifier: Modifier = Modifier,
) {
    val phase = checkmarkPhaseForStatus(status) ?: return
    var currentPhase by remember { mutableStateOf(phase) }

    val reposition = remember { Animatable(if (phase == CheckmarkPhase.WAITING) 0f else 1f) }
    val expand1 = remember { Animatable(if (phase == CheckmarkPhase.WAITING) 0f else 1f) }
    val expand2 = remember { Animatable(if (phase == CheckmarkPhase.WAITING) 0f else 1f) }
    val doubleSlide = remember {
        Animatable(if (phase == CheckmarkPhase.DELIVERED || phase == CheckmarkPhase.READ) 1f else 0f)
    }
    val bounceScale = remember { Animatable(1f) }
    val colorProgress = remember { Animatable(if (phase == CheckmarkPhase.READ) 1f else 0f) }

    LaunchedEffect(phase) {
        val from = currentPhase
        val to = phase
        if (from == to) return@LaunchedEffect
        when {
            from == CheckmarkPhase.WAITING && to == CheckmarkPhase.SENT -> {
                reposition.animateTo(1f, tween(180, easing = StandardEasing))
                coroutineScope {
                    launch { expand1.animateTo(1f, tween(240, easing = StandardEasing)) }
                    launch { expand2.animateTo(1f, tween(240, delayMillis = 90, easing = StandardEasing)) }
                }
            }
            from == CheckmarkPhase.SENT && to == CheckmarkPhase.DELIVERED ->
                doubleSlide.animateTo(1f, tween(260, easing = StandardEasing))
            from == CheckmarkPhase.DELIVERED && to == CheckmarkPhase.READ -> {
                coroutineScope {
                    launch {
                        bounceScale.animateTo(1.32f, tween(160, easing = StandardEasing))
                        bounceScale.animateTo(
                            1f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        )
                    }
                    launch { colorProgress.animateTo(1f, tween(600, easing = StandardEasing)) }
                }
            }
            else -> {
                // Any other jump (skipped a step, went backwards, or this composable just
                // mounted mid-story) — snap straight to the target rather than replaying a
                // multi-second morph the user never saw the start of.
                reposition.snapTo(if (to >= CheckmarkPhase.SENT) 1f else 0f)
                expand1.snapTo(if (to >= CheckmarkPhase.SENT) 1f else 0f)
                expand2.snapTo(if (to >= CheckmarkPhase.SENT) 1f else 0f)
                doubleSlide.snapTo(if (to >= CheckmarkPhase.DELIVERED) 1f else 0f)
                colorProgress.snapTo(if (to == CheckmarkPhase.READ) 1f else 0f)
            }
        }
        currentPhase = to
    }

    Canvas(modifier = modifier) {
        val s = size.minDimension / 24f
        val strokeW = lerp(DOT_RADIUS * 2f, CHECK_STROKE_WIDTH, expand1.value)
        val readBoldStrokeW = lerp(strokeW, strokeW * 1.2f, colorProgress.value)

        val pos0 = androidx.compose.ui.geometry.lerp(DOT1, CHECK_P0, reposition.value)
        val pos1 = androidx.compose.ui.geometry.lerp(DOT2, CHECK_P1, reposition.value)
        val pos2 = androidx.compose.ui.geometry.lerp(DOT3, CHECK_P2, reposition.value)

        val color = androidx.compose.ui.graphics.lerp(grayColor, successColor, colorProgress.value)
        val shift = DOUBLE_CHECK_OFFSET * doubleSlide.value

        scale(bounceScale.value) {
            fun drawStrokeCheckmark(o: Offset) {
                val a = pos0 + o
                val b = pos1 + o
                val c = pos2 + o
                drawCircle(color = color, radius = readBoldStrokeW / 2f * s, center = c * s)
                drawLine(
                    color = color,
                    start = a * s,
                    end = androidx.compose.ui.geometry.lerp(a, b, expand1.value) * s,
                    strokeWidth = readBoldStrokeW * s,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = b * s,
                    end = androidx.compose.ui.geometry.lerp(b, c, expand2.value) * s,
                    strokeWidth = readBoldStrokeW * s,
                    cap = StrokeCap.Round,
                )
            }

            if (doubleSlide.value > 0f) {
                // The moving (front) checkmark, at its slid position — always drawn whole.
                drawStrokeCheckmark(shift)
                // The original (back) checkmark, with whatever the front one currently
                // overlaps cut out of it — a real trace/gap, not a flat double-draw.
                val frontCoverage = checkmarkCoverage(pos0 + shift, pos1 + shift, pos2 + shift, readBoldStrokeW / 2f, s)
                clipPath(frontCoverage, ClipOp.Difference) {
                    drawStrokeCheckmark(Offset.Zero)
                }
            } else {
                drawStrokeCheckmark(Offset.Zero)
            }
        }
    }
}

/** Union of two round-capped capsule shapes (P0→P1 and P1→P2) approximating the filled outline
 * a stroked checkmark would have — used only as a clip mask, never drawn directly. */
private fun checkmarkCoverage(a: Offset, b: Offset, c: Offset, halfWidth: Float, scale: Float): Path {
    val seg1 = capsulePath(a * scale, b * scale, halfWidth * scale)
    val seg2 = capsulePath(b * scale, c * scale, halfWidth * scale)
    val union = Path()
    union.op(seg1, seg2, PathOperation.Union)
    return union
}

private fun angleDeg(x: Float, y: Float): Float = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()

private fun capsulePath(start: Offset, end: Offset, halfWidth: Float): Path {
    val path = Path()
    val dx = end.x - start.x
    val dy = end.y - start.y
    val len = hypot(dx, dy)
    if (len < 0.01f) {
        path.addOval(Rect(start.x - halfWidth, start.y - halfWidth, start.x + halfWidth, start.y + halfWidth))
        return path
    }
    val ux = dx / len
    val uy = dy / len
    val nx = -uy * halfWidth
    val ny = ux * halfWidth
    val startAngle = angleDeg(nx, ny)
    val endAngle = angleDeg(-nx, -ny)
    path.moveTo(start.x + nx, start.y + ny)
    path.lineTo(end.x + nx, end.y + ny)
    path.arcTo(
        rect = Rect(end.x - halfWidth, end.y - halfWidth, end.x + halfWidth, end.y + halfWidth),
        startAngleDegrees = startAngle,
        sweepAngleDegrees = 180f,
        forceMoveTo = false,
    )
    path.lineTo(start.x - nx, start.y - ny)
    path.arcTo(
        rect = Rect(start.x - halfWidth, start.y - halfWidth, start.x + halfWidth, start.y + halfWidth),
        startAngleDegrees = endAngle,
        sweepAngleDegrees = 180f,
        forceMoveTo = false,
    )
    path.close()
    return path
}
