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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.util.lerp
import eu.siacs.conversations.entities.Message
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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

/**
 * Draws the waiting/sent/delivered/read sequence as one continuously-morphing glyph instead of
 * swapping between four unrelated icons:
 *
 * - Waiting → sent: the three dots reposition onto the checkmark's three key points (start,
 *   vertex, end) first, then two strokes grow between them — left dot into the short down-stroke,
 *   the vertex/end dot pair into the long up-stroke. A round stroke cap of the same diameter as
 *   the dots means a zero-length stroke *is* a dot, so "dot becomes line" falls out for free
 *   rather than needing separate dot/line drawing paths.
 * - Sent → delivered: a second, identical checkmark starts exactly on top of the first and
 *   slides right into the familiar double-check layout.
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
        val dotR = 2f
        val strokeBase = dotR * 2f
        val strokeW = lerp(strokeBase, strokeBase * 1.35f, colorProgress.value)

        val waitDot1 = Offset(5f, 12f)
        val waitDot2 = Offset(12f, 12f)
        val waitDot3 = Offset(19f, 12f)
        val p0 = Offset(4f, 12.5f)
        val p1 = Offset(9.5f, 18f)
        val p2 = Offset(20f, 5.5f)

        val pos0 = androidx.compose.ui.geometry.lerp(waitDot1, p0, reposition.value)
        val pos1 = androidx.compose.ui.geometry.lerp(waitDot2, p1, reposition.value)
        val pos2 = androidx.compose.ui.geometry.lerp(waitDot3, p2, reposition.value)

        val color = androidx.compose.ui.graphics.lerp(grayColor, successColor, colorProgress.value)
        val offset2 = Offset(6f, 0f) * doubleSlide.value

        scale(bounceScale.value) {
            fun drawCheckmark(shift: Offset) {
                val a = pos0 + shift
                val b = pos1 + shift
                val c = pos2 + shift
                drawCircle(color = color, radius = dotR * s, center = c * s)
                drawLine(
                    color = color,
                    start = a * s,
                    end = androidx.compose.ui.geometry.lerp(a, b, expand1.value) * s,
                    strokeWidth = strokeW * s,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = b * s,
                    end = androidx.compose.ui.geometry.lerp(b, c, expand2.value) * s,
                    strokeWidth = strokeW * s,
                    cap = StrokeCap.Round,
                )
            }
            if (doubleSlide.value > 0f) drawCheckmark(offset2)
            drawCheckmark(Offset.Zero)
        }
    }
}
