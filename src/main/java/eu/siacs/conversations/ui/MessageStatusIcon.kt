package eu.siacs.conversations.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.util.lerp
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.entities.Transferable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.hypot

/** Slow-fast-slow, never linear — linear motion on something this small reads as robotic. */
private val StandardEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** How long the one genuine shape-changing headphone leg (checkmark family -> headphone) takes to
 * morph from the real checkmark outline into the real headphone outline — deliberately slower
 * than every other transition in this file so the shape change itself reads clearly. */
private const val HEADPHONE_MORPH_DURATION_MS = 1000

/** Point count each closed outline (checkmark, double-check, headphone) gets resampled to via
 * PathMeasure before morphing — high enough that the polygon these render as is indistinguishable
 * from the true curves at this icon's on-screen size. */
private const val MORPH_SAMPLE_COUNT = 72

/** Fast start, hard deceleration into the last stretch — used only for the stem retracting back
 * up into the chevron, so the "eraser" visibly slows down right before it reaches the tip instead
 * of just stopping. */
private val StemRetractEasing = CubicBezierEasing(0.1f, 0.7f, 0.05f, 1f)

/** The statuses that form one continuous story worth morphing between — waiting dots that turn
 * into an upload glyph (a file transfer starting), a P2P offer glyph (a direct transfer being
 * proposed), or a checkmark (a text message going out); the checkmark growing a second one, then
 * turning green; an upload turning into a cancel glyph if stopped. Appended OFFERED/CANCELLED
 * after READ on purpose — the WAITING..READ ordinal ordering is load-bearing for the `>=`
 * comparisons in the catch-all snap branch below, and these two aren't part of that chain. */
// LISTENING/LISTENED/LISTEN_UNKNOWN/LISTEN_PAUSED appended after CANCELLED for the same reason
// OFFERED/CANCELLED were: the WAITING..READ ordinal ordering is load-bearing for the `>=`
// comparisons in the catch-all snap branch below, and none of these six are part of that chain.
enum class CheckmarkPhase {
    WAITING, UPLOADING, SENT, DELIVERED, READ, OFFERED, CANCELLED,
    LISTENING, LISTENED, LISTEN_UNKNOWN, LISTEN_PAUSED,
}

private fun CheckmarkPhase.isListenPhase() =
    this == CheckmarkPhase.LISTENING || this == CheckmarkPhase.LISTENED ||
        this == CheckmarkPhase.LISTEN_UNKNOWN || this == CheckmarkPhase.LISTEN_PAUSED

/** The one real bundled drawable + tint for a given phase at rest — used both by the plain
 * "!isAnimating" render branch below and, for the one headphone leg that actually changes shape,
 * to pick the two endpoints of a crossfade between real assets rather than hand-drawing an
 * in-between shape. Single source of truth so the two call sites can't drift apart. */
private fun staticIconFor(
    p: CheckmarkPhase,
    grayColor: Color,
    successColor: Color,
    listenedColor: Color,
    unknownColor: Color,
): Pair<Int, Color> = when (p) {
    CheckmarkPhase.WAITING -> R.drawable.ic_more_horiz_24dp to grayColor
    CheckmarkPhase.UPLOADING -> R.drawable.ic_upload_24dp to grayColor
    CheckmarkPhase.SENT -> R.drawable.ic_done_24dp to grayColor
    CheckmarkPhase.DELIVERED -> R.drawable.ic_done_all_24dp to grayColor
    CheckmarkPhase.READ -> R.drawable.ic_done_all_bold_24dp to successColor
    CheckmarkPhase.OFFERED -> R.drawable.ic_p2p_24dp to grayColor
    CheckmarkPhase.CANCELLED -> R.drawable.ic_cancel_24dp to grayColor
    CheckmarkPhase.LISTENING -> R.drawable.ic_headphones_24dp to grayColor
    CheckmarkPhase.LISTENED -> R.drawable.ic_headphones_24dp to listenedColor
    CheckmarkPhase.LISTEN_UNKNOWN -> R.drawable.ic_headphones_24dp to unknownColor
    // Same gray look as LISTENING — the headphone doesn't disappear on pause, it just stops
    // pulsing (pulseScale sits frozen at 1f), so this shares LISTENING's color entirely and only
    // differs in whether the render section's graphicsLayer scale is actively being driven.
    CheckmarkPhase.LISTEN_PAUSED -> R.drawable.ic_headphones_24dp to grayColor
}

/**
 * Overrides [basePhase] with the voice-message listen-status phase once the peer has done
 * anything with a voice message we sent — the headphone glyph takes over this exact slot, it
 * isn't an extra icon bolted on next to the checkmark. LISTEN_PAUSED keeps the headphone showing,
 * just frozen (see pulseScale below) rather than falling back to the checkmark — pausing doesn't
 * mean "nothing is happening with this voice message anymore." Only NOT_LISTENED and no listen
 * status at all (i.e. [listenState] null) fall through to [basePhase] unchanged — the ordinary
 * checkmark, same as any non-voice message.
 */
fun voiceCheckmarkPhase(
    basePhase: CheckmarkPhase?,
    listenState: ListenStatusManager.State?,
): CheckmarkPhase? = when (listenState) {
    ListenStatusManager.State.LISTENING -> CheckmarkPhase.LISTENING
    ListenStatusManager.State.LISTENED -> CheckmarkPhase.LISTENED
    ListenStatusManager.State.UNKNOWN -> CheckmarkPhase.LISTEN_UNKNOWN
    ListenStatusManager.State.PAUSED -> CheckmarkPhase.LISTEN_PAUSED
    else -> basePhase
}

fun checkmarkPhaseForStatus(
    status: Int,
    transferable: Transferable?,
    errorMessage: String?,
): CheckmarkPhase? = when (status) {
    Message.STATUS_WAITING -> CheckmarkPhase.WAITING
    // STATUS_UNSEND covers two different things: a file genuinely mid-upload (transferable !=
    // null — gets its own dots-into-upload-icon morph) and a text message written to the socket
    // but not yet stream-management-acknowledged, which reads as "still sending" to a user and
    // isn't worth a distinct visual from STATUS_WAITING, so it just continues the dots.
    Message.STATUS_UNSEND -> if (transferable != null) CheckmarkPhase.UPLOADING else CheckmarkPhase.WAITING
    Message.STATUS_OFFERED -> CheckmarkPhase.OFFERED
    Message.STATUS_SEND -> CheckmarkPhase.SENT
    Message.STATUS_SEND_RECEIVED -> CheckmarkPhase.DELIVERED
    Message.STATUS_SEND_DISPLAYED -> CheckmarkPhase.READ
    // Only the user-cancelled case joins the morph story — a generic send/upload/jingle error
    // isn't reachable from a single consistent prior glyph the way a deliberate cancel always is
    // (dots, the upload arrow, or the P2P glyph), so it keeps the plain crossfade fallback.
    Message.STATUS_SEND_FAILED ->
        if (errorMessage == Message.ERROR_MESSAGE_CANCELLED) CheckmarkPhase.CANCELLED else null
    else -> null
}

// All geometry below is measured directly off the real Material Symbols paths this icon set
// already ships (ic_more_horiz_24dp, ic_done_24dp, ic_done_all_24dp, ic_upload_24dp — 960x960
// viewport, rescaled here to a 24-unit space to match this file's drawing convention), not
// eyeballed:
//   - dots: 3 circles, centers (240,480)/(480,480)/(720,480), radius 80  -> 24-space r=2.0
//   - checkmark: a round-capped/round-joined stroke, centerline points recovered from the two
//     edges flanking each rounded cap in ic_done_24dp's outline; average cap radius ~39.8,
//     i.e. stroke width ~1.99 (24-space) — notably thinner than a dot's own diameter (4.0).
//   - ic_done_all_24dp's second (fully visible) checkmark is exactly ic_done_24dp's shape offset
//     by (+112.8, +0.8) in the 960 viewport -> (+2.82, +0.02) in 24-space; the first checkmark
//     sits at ic_done_24dp's own unshifted position, with the second one's shape cut out of it
//     wherever they overlap — that's the "trace" look, not two full opaque copies.
//   - ic_upload_24dp: a chevron (left tip / apex / right tip — same 3-point shape as the
//     checkmark, so it reuses the exact same reposition+expand technique), a stem below the
//     apex, and a 4-corner open-top tray; measured stroke width ~2.0, matching the checkmark's
//     closely enough to reuse CHECK_STROKE_WIDTH rather than add a near-duplicate constant.
private val DOT1 = Offset(6f, 12f)
private val DOT2 = Offset(12f, 12f)
private val DOT3 = Offset(18f, 12f)
private const val DOT_RADIUS = 2f

private val CHECK_P0 = Offset(5.275f, 12.2875f) // start
private val CHECK_P1 = Offset(9.55f, 16.6333f) // vertex
private val CHECK_P2 = Offset(18.725f, 7.3875f) // end
private const val CHECK_STROKE_WIDTH = 1.99f
private val DOUBLE_CHECK_OFFSET = Offset(2.82f, 0.02f)

private val CHEVRON_LEFT = Offset(8.412f, 9.013f)
private val CHEVRON_APEX = Offset(12f, 4.7f)
private val CHEVRON_RIGHT = Offset(15.588f, 9.013f)
private val STEM_BOTTOM = Offset(12f, 15.5f)
private val TRAY_LEFT_TOP = Offset(5f, 16f)
private val TRAY_LEFT_BOTTOM = Offset(5f, 19f)
private val TRAY_RIGHT_BOTTOM = Offset(19f, 19f)
private val TRAY_RIGHT_TOP = Offset(19f, 16f)
// The tray's 4 corners unfold outward from this single point rather than fading in flat — the
// same "grow from a point" trick the dots use, so the reveal reads as one consistent language.
private val TRAY_COLLAPSE_ORIGIN = Offset(12f, 17.5f)

// ic_p2p_24dp: two phone-shaped brackets, diagonally offset (measured: the right phone's bounding
// box sits 80/960 lower than the left's, not side by side) — and, conveniently, the icon already
// has its own 3 small transfer dots built into the artwork, sitting almost exactly where the
// waiting dots already are, so there's a real (not invented) 3-dot anchor to slide onto instead
// of forcing a mapping onto the two phones directly.
private val P2P_DOT1 = Offset(8f, 12f)
private val P2P_DOT2 = Offset(12f, 12f)
private val P2P_DOT3 = Offset(16f, 12f)
private const val P2P_DOT_RADIUS = 1f
private val PHONE_LEFT_TOP_LEFT = Offset(2f, 2f)
private val PHONE_LEFT_BOTTOM_RIGHT = Offset(11f, 20f)
private val PHONE_RIGHT_TOP_LEFT = Offset(13f, 4f)
private val PHONE_RIGHT_BOTTOM_RIGHT = Offset(22f, 22f)
private const val PHONE_CORNER_RADIUS = 2f

// ic_cancel_24dp: an X of two crossing round-capped/round-joined strokes inside a ring, centerline
// recovered the same way as the checkmark (averaging the points flanking each rounded tip); comes
// out to a stroke width matching CHECK_STROKE_WIDTH closely enough to reuse it rather than add a
// near-duplicate constant, same as the upload chevron did.
private val CANCEL_CENTER = Offset(12f, 12f)
private val CANCEL_TOP_LEFT = Offset(8.4f, 8.4f)
private val CANCEL_TOP_RIGHT = Offset(15.6f, 8.4f)
private val CANCEL_BOTTOM_LEFT = Offset(8.4f, 15.6f)
private val CANCEL_BOTTOM_RIGHT = Offset(15.6f, 15.6f)

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
 * - Waiting → uploading (a file transfer starting instead of a text message going out): the same
 *   three dots reposition onto the upload icon's chevron instead — it has exactly three key
 *   points too (left tip, apex, right tip), so it's the identical technique with a different
 *   target shape. Only 3 dots exist but the icon has ~9 key points total, so the remaining ones
 *   (stem, tray) aren't sourced from dots at all: once the chevron finishes, a stem grows
 *   straight down from its apex, then the tray's four corners unfold outward from one point below
 *   the stem with a small bounce — three beats (arrowhead, stem, box), not one impossible mapping.
 * - Sent → delivered: a second checkmark starts exactly on top of the first — at that position
 *   it's fully hidden, matching a single checkmark — and slides right into the real double-check
 *   layout, where it is the fully-visible (front) stroke and the original checkmark becomes the
 *   partially-hidden (back) one behind it, clipped wherever the new one covers it — matching the
 *   real icon's own technique instead of two flatly-overlapping copies.
 * - Delivered → read: both strokes bounce (quick zoom in, bouncy spring back to rest) while
 *   tinting from gray to green, with the stroke going a bit heavier at the same time so the two
 *   states stay easy to tell apart even for someone not distinguishing the color.
 * - Read → listening (voice messages only, once the peer starts playing one we sent): a real
 *   point-to-point morph over a full second, not a crossfade — the checkmark's (or double-check's)
 *   actual outline and the headphone's actual outline are each resampled to the same number of
 *   points via PathMeasure and lerped index-for-index, so every in-between frame is a real blend
 *   of the two real shapes, not an invented approximation of either one (see the "Checkmark <->
 *   headphone morph" section at the bottom of this file). Listening itself pulses continuously so
 *   it reads as "live." Listening → listened bounces to green exactly like delivered → read above,
 *   just aimed at the (already fully-formed, never reshaping again) headphone glyph;
 *   listening/listened → an extrapolation losing track bounces bouncier, to red — same
 *   spatial+effect spring language, no further shape changes either.
 */
@Composable
fun MessageStatusIcon(
    phase: CheckmarkPhase,
    grayColor: Color,
    successColor: Color,
    listenedColor: Color = successColor,
    unknownColor: Color = grayColor,
    modifier: Modifier = Modifier,
) {
    var currentPhase by remember { mutableStateOf(phase) }
    // Only true while an actual morph is in flight. At rest — the vast majority of the time,
    // for every message that isn't mid-transition right now — this renders the real bundled
    // drawable (the actual Google Material Symbol for the headphone, same as every other phase's
    // own real asset) instead of the Canvas, so any tiny residual mismatch between the measured/
    // approximated morph geometry and the true vector asset can't linger, and idle messages stop
    // paying for a per-frame Canvas redraw they don't need. LISTENING is the one exception that
    // keeps animating even at rest (the continuous "this is live" pulse) — see pulseScale below.
    var isAnimating by remember { mutableStateOf(false) }

    val reposition = remember { Animatable(if (phase == CheckmarkPhase.WAITING) 0f else 1f) }
    val expand1 = remember { Animatable(if (phase == CheckmarkPhase.WAITING) 0f else 1f) }
    val expand2 = remember { Animatable(if (phase == CheckmarkPhase.WAITING) 0f else 1f) }
    val doubleSlide = remember {
        Animatable(if (phase == CheckmarkPhase.DELIVERED || phase == CheckmarkPhase.READ) 1f else 0f)
    }
    val bounceScale = remember { Animatable(1f) }
    val colorProgress = remember {
        Animatable(
            if (phase == CheckmarkPhase.READ ||
                phase == CheckmarkPhase.LISTENED ||
                phase == CheckmarkPhase.LISTEN_UNKNOWN
            ) 1f else 0f,
        )
    }
    // Only ever driven by the waiting -> uploading transition below, so a plain 0f start is
    // always correct — unlike reposition/expand1/expand2 there's no later transition that reads
    // these while already at rest in some other phase.
    val stemProgress = remember { Animatable(0f) }
    val trayProgress = remember { Animatable(0f) }
    // Only ever driven by the uploading -> sent transition below — slides the chevron's own
    // points directly onto the checkmark's, never passing back through the dot positions.
    val chevronToCheck = remember { Animatable(0f) }
    // 0 = dots at their canonical waiting-row positions, 1 = at ic_p2p_24dp's own transfer-dot
    // positions (and shrunk to that icon's smaller dot radius). Only touched by waiting <-> p2p.
    val p2pDotShift = remember { Animatable(0f) }
    // 0 = phone brackets invisible, 1 = fully grown in, at their own final size. Only touched by
    // waiting <-> p2p (in) and p2p -> uploading (out).
    val phoneGrow = remember { Animatable(0f) }
    // Only touched by uploading -> cancelled: 0 = chevron's three points sit at their own
    // (possibly already-collapsed) positions, 1 = all three have merged into a single point at
    // the cancel-X's own center.
    val arrowMergeToCenter = remember { Animatable(0f) }
    // Only touched by uploading -> cancelled: the X's four strokes growing outward from that
    // merged center point once it's arrived.
    val cancelCrossGrow = remember { Animatable(0f) }

    // Only touched by (sent/delivered/read) -> listening: 0 = fully the checkmark's (or
    // double-check's) real outline, 1 = fully the headphone's — see the point-morph section at
    // the bottom of this file. Initialized to 1 on a cold mount already in the listen family so a
    // fresh composition doesn't briefly render at the checkmark end of the morph before the
    // catch-all snap corrects it.
    val morphProgress = remember { Animatable(if (phase.isListenPhase()) 1f else 0f) }

    // Drives the headphone's continuous "this is live" pulse while at rest in LISTENING — the
    // only phase in this whole file that keeps animating once settled rather than stopping dead.
    // Deliberately its own Animatable + coroutine loop rather than rememberInfiniteTransition:
    // pausing (LISTEN_PAUSED) needs to let whichever grow/shrink leg is currently in flight finish
    // naturally and freeze exactly at the pulse's own low point (1f) instead of yanking it off
    // mid-swing, and rememberInfiniteTransition has no way to ask for that — it only knows how to
    // cancel immediately or keep going forever. A plain Animatable driven by a loop that always
    // completes one full grow-then-shrink leg pair before re-checking whether to continue gets
    // this for free: however mid-cycle the pause request lands, the loop still finishes both legs
    // (ending back at 1f) before it ever idles, and resuming just means the loop starts driving
    // pulseScale again from wherever it's sitting — which by construction is always 1f, the exact
    // same "small scale" a fresh pulse would start from.
    val pulseScale = remember { Animatable(1f) }
    val shouldPulse = rememberUpdatedState(currentPhase == CheckmarkPhase.LISTENING)
    LaunchedEffect(Unit) {
        while (true) {
            if (!shouldPulse.value) {
                snapshotFlow { shouldPulse.value }.first { it }
            }
            pulseScale.animateTo(1.15f, tween(600, easing = FastOutSlowInEasing))
            pulseScale.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        }
    }

    // Keyed on `phase` this used to cancel and restart mid-animation on every status change —
    // status can flip more than once within a single morph's duration (e.g. an upload finishing
    // sends WAITING -> UPLOADING -> WAITING -> SENT in quick succession), and a cancelled
    // coroutine never reaches the `currentPhase = to` line at the bottom, leaving it pointing at
    // the *previous* phase while the animatables are already partway through a *different*
    // shape's geometry — the next leg then reinterprets that half-finished progress against the
    // wrong target points and visibly jumps. Running one long-lived effect that always finishes
    // its current leg before reading the latest target avoids that: any status changes that land
    // mid-animation just get picked up as the target for the *next* leg once this one settles.
    val latestPhase = rememberUpdatedState(phase)
    LaunchedEffect(Unit) {
        while (true) {
            val to = latestPhase.value
            val from = currentPhase
            if (from == to) {
                snapshotFlow { latestPhase.value }.first { it != currentPhase }
                continue
            }
            when {
                from == CheckmarkPhase.WAITING && to == CheckmarkPhase.SENT -> {
                    isAnimating = true
                    reposition.animateTo(1f, tween(180, easing = StandardEasing))
                    coroutineScope {
                        launch { expand1.animateTo(1f, tween(240, easing = StandardEasing)) }
                        launch { expand2.animateTo(1f, tween(240, delayMillis = 90, easing = StandardEasing)) }
                    }
                }
                from == CheckmarkPhase.WAITING && to == CheckmarkPhase.UPLOADING -> {
                    isAnimating = true
                    // Same dots-into-3-point-shape technique as waiting -> sent, just aimed at
                    // the chevron's points instead of the checkmark's.
                    reposition.animateTo(1f, tween(180, easing = StandardEasing))
                    coroutineScope {
                        launch { expand1.animateTo(1f, tween(240, easing = StandardEasing)) }
                        launch { expand2.animateTo(1f, tween(240, delayMillis = 90, easing = StandardEasing)) }
                    }
                    stemProgress.animateTo(1f, tween(220, easing = StandardEasing))
                    trayProgress.animateTo(1f, tween(500, easing = StandardEasing))
                }
                from == CheckmarkPhase.UPLOADING && to == CheckmarkPhase.WAITING -> {
                    isAnimating = true
                    // The whole forward sequence in reverse: tray folds back into its single
                    // origin point, that point travels up the stem — erasing it as it goes,
                    // decelerating hard right before it reaches the tip — then the two chevron
                    // arms retract into dots at their current (chevron) positions, and only then
                    // do those three dots slide back to their waiting-row positions.
                    trayProgress.animateTo(0f, tween(240, easing = StandardEasing))
                    stemProgress.animateTo(0f, tween(320, easing = StemRetractEasing))
                    coroutineScope {
                        launch { expand2.animateTo(0f, tween(200, easing = StandardEasing)) }
                        launch { expand1.animateTo(0f, tween(200, delayMillis = 90, easing = StandardEasing)) }
                    }
                    reposition.animateTo(0f, tween(180, easing = StandardEasing))
                }
                from == CheckmarkPhase.UPLOADING && to == CheckmarkPhase.SENT -> {
                    isAnimating = true
                    // An upload finishing goes straight from the arrow to a checkmark with no
                    // dots in between, so this doesn't touch reposition/the dot positions at
                    // all: retract the tray and stem first (same motion as the reverse, just
                    // stopping short of dots), then slide the chevron's own two strokes directly
                    // onto the checkmark's via chevronToCheck.
                    trayProgress.animateTo(0f, tween(200, easing = StandardEasing))
                    stemProgress.animateTo(0f, tween(260, easing = StemRetractEasing))
                    chevronToCheck.animateTo(1f, tween(240, easing = StandardEasing))
                }
                from == CheckmarkPhase.WAITING && to == CheckmarkPhase.OFFERED -> {
                    isAnimating = true
                    // The dots don't need to invent a mapping onto two phones — ic_p2p_24dp
                    // already has its own 3 transfer dots sitting almost exactly where these
                    // already are, so they just slide the short remaining distance while the two
                    // phone brackets zoom in on either side of them.
                    p2pDotShift.animateTo(1f, tween(200, easing = StandardEasing))
                    phoneGrow.animateTo(1f, tween(260, easing = StandardEasing))
                }
                from == CheckmarkPhase.OFFERED && to == CheckmarkPhase.UPLOADING -> {
                    isAnimating = true
                    // Zoom the two phones back out, let the dots settle onto their exact
                    // canonical waiting positions, then hand off to the ordinary
                    // waiting -> uploading sequence completely unchanged.
                    phoneGrow.animateTo(0f, tween(220, easing = StandardEasing))
                    p2pDotShift.animateTo(0f, tween(200, easing = StandardEasing))
                    reposition.animateTo(1f, tween(180, easing = StandardEasing))
                    coroutineScope {
                        launch { expand1.animateTo(1f, tween(240, easing = StandardEasing)) }
                        launch { expand2.animateTo(1f, tween(240, delayMillis = 90, easing = StandardEasing)) }
                    }
                    stemProgress.animateTo(1f, tween(220, easing = StandardEasing))
                    trayProgress.animateTo(1f, tween(500, easing = StandardEasing))
                }
                from == CheckmarkPhase.UPLOADING && to == CheckmarkPhase.CANCELLED -> {
                    isAnimating = true
                    // Retract the tray/stem/arms first (same motion as uploading -> waiting, just
                    // not continuing on into dots): that leaves the chevron's three points sitting
                    // at their own three corners. Those three then merge into one point at the
                    // cancel-X's own center, and four strokes grow outward from it — the arrow
                    // quite literally turning into the cross, never passing through dots.
                    trayProgress.animateTo(0f, tween(200, easing = StandardEasing))
                    stemProgress.animateTo(0f, tween(260, easing = StemRetractEasing))
                    coroutineScope {
                        launch { expand2.animateTo(0f, tween(180, easing = StandardEasing)) }
                        launch { expand1.animateTo(0f, tween(180, delayMillis = 80, easing = StandardEasing)) }
                    }
                    arrowMergeToCenter.animateTo(1f, tween(180, easing = StandardEasing))
                    cancelCrossGrow.animateTo(1f, tween(260, easing = StandardEasing))
                }
                from == CheckmarkPhase.SENT && to == CheckmarkPhase.DELIVERED -> {
                    isAnimating = true
                    doubleSlide.animateTo(1f, tween(260, easing = StandardEasing))
                }
                from == CheckmarkPhase.DELIVERED && to == CheckmarkPhase.READ -> {
                    isAnimating = true
                    coroutineScope {
                        launch {
                            bounceScale.animateTo(1.32f, tween(160, easing = StandardEasing))
                            // Spatial spring: this is the part that actually moves/scales, so it's
                            // the one allowed to bounce.
                            bounceScale.animateTo(
                                1f,
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow,
                                ),
                            )
                        }
                        // Effect spring: was a plain tween, the odd one out next to bounceScale's
                        // real physics spring above. Color has no business overshooting (a
                        // "bouncing" tint just reads as flicker), but it should still be a spring —
                        // matching the no-bounce "effects" convention already used elsewhere in the
                        // app (see UpdatesScreen.kt's `effects = spring(stiffness = 1600f,
                        // dampingRatio = 1.0f)`) so the spatial and effect halves of this one
                        // transition are actually the same motion language, not spring + tween.
                        launch {
                            colorProgress.animateTo(1f, spring(stiffness = 1600f, dampingRatio = 1.0f))
                        }
                    }
                }
                (from == CheckmarkPhase.SENT ||
                    from == CheckmarkPhase.DELIVERED ||
                    from == CheckmarkPhase.READ) &&
                    (to == CheckmarkPhase.LISTENING || to == CheckmarkPhase.LISTEN_PAUSED) -> {
                    isAnimating = true
                    // The real point-to-point morph — see the "Checkmark <-> headphone morph"
                    // section at the bottom of this file for how singleCheckMorphPoints/
                    // doubleCheckMorphPoints/headphoneOutlinePoints are built and aligned. Which
                    // point list applies is decided at render time from `from` (SENT -> single,
                    // DELIVERED/READ -> double), not here. Also covers the rare case of arriving
                    // already paused (this composable mounting fresh mid-pause) — same morph
                    // either way, just no pulse afterward if paused.
                    morphProgress.snapTo(0f)
                    morphProgress.animateTo(1f, tween(HEADPHONE_MORPH_DURATION_MS, easing = StandardEasing))
                }
                (from == CheckmarkPhase.LISTENING || from == CheckmarkPhase.LISTEN_PAUSED) &&
                    to == CheckmarkPhase.LISTENED -> {
                    isAnimating = true
                    // Identical spatial+effect spring choreography as delivered -> read above —
                    // same visual language, just aimed at the headphone instead of the checkmark.
                    coroutineScope {
                        launch {
                            bounceScale.animateTo(1.32f, tween(160, easing = StandardEasing))
                            bounceScale.animateTo(
                                1f,
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow,
                                ),
                            )
                        }
                        launch {
                            colorProgress.animateTo(1f, spring(stiffness = 1600f, dampingRatio = 1.0f))
                        }
                    }
                }
                (from == CheckmarkPhase.LISTENING || from == CheckmarkPhase.LISTENED) &&
                    to == CheckmarkPhase.LISTEN_UNKNOWN -> {
                    isAnimating = true
                    // Bouncier than the listened kick — the extrapolation genuinely lost track, so
                    // this should read more like an alert than a confirmation. Same effect-spring
                    // color language, aimed at red instead of green.
                    coroutineScope {
                        launch {
                            bounceScale.snapTo(0f)
                            bounceScale.animateTo(
                                1f,
                                spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
                            )
                        }
                        launch {
                            colorProgress.snapTo(0f)
                            colorProgress.animateTo(1f, spring(stiffness = 1600f, dampingRatio = 1.0f))
                        }
                    }
                }
                (from == CheckmarkPhase.LISTENED || from == CheckmarkPhase.LISTEN_UNKNOWN) &&
                    to == CheckmarkPhase.LISTENING -> {
                    isAnimating = true
                    // A genuine resume — the peer re-opened the message after LISTENED/
                    // LISTEN_UNKNOWN — settles straight back to the resting look; the continuous
                    // pulse takes over from there.
                    bounceScale.snapTo(1f)
                    colorProgress.animateTo(0f, tween(200, easing = StandardEasing))
                }
                else -> {
                    // Any other jump (skipped a step, went backwards, or this composable just
                    // mounted mid-story) — snap straight to the target rather than replaying a
                    // multi-second morph the user never saw the start of. Never worth animating, so
                    // isAnimating stays false, which renders the real bundled drawable for whatever
                    // `to` is directly — every phase has one now, listen family included.
                    reposition.snapTo(if (to >= CheckmarkPhase.SENT) 1f else 0f)
                    expand1.snapTo(if (to >= CheckmarkPhase.SENT) 1f else 0f)
                    expand2.snapTo(if (to >= CheckmarkPhase.SENT) 1f else 0f)
                    doubleSlide.snapTo(if (to >= CheckmarkPhase.DELIVERED) 1f else 0f)
                    bounceScale.snapTo(1f)
                    morphProgress.snapTo(if (to.isListenPhase()) 1f else 0f)
                    colorProgress.snapTo(
                        if (to == CheckmarkPhase.READ ||
                            to == CheckmarkPhase.LISTENED ||
                            to == CheckmarkPhase.LISTEN_UNKNOWN
                        ) 1f else 0f,
                    )
                }
            }
            currentPhase = to
            isAnimating = false
        }
    }

    if (!isAnimating) {
        val (staticDrawable, staticColor) =
            staticIconFor(currentPhase, grayColor, successColor, listenedColor, unknownColor)
        // LISTENING (and, frozen, LISTEN_PAUSED) are the only phases in this whole file where
        // !isAnimating doesn't mean "nothing is moving" — pulseScale is itself a continuously-
        // updating value while active, so reading it here keeps this Icon recomposing every frame
        // without needing isAnimating to stay true. Applying it while paused too is harmless: by
        // construction pulseScale is always exactly 1f at that point (see the pulse loop above),
        // so this is just an identity transform until the loop starts driving it again.
        val iconModifier = if (currentPhase == CheckmarkPhase.LISTENING || currentPhase == CheckmarkPhase.LISTEN_PAUSED) {
            modifier.graphicsLayer(scaleX = pulseScale.value, scaleY = pulseScale.value)
        } else {
            modifier
        }
        Icon(
            painter = painterResource(staticDrawable),
            contentDescription = null,
            tint = staticColor,
            modifier = iconModifier,
        )
        return
    }

    // currentPhase only flips to the new value once its transition finishes, so during a
    // reverse (e.g. uploading -> waiting) animation `phase` is already at the target while
    // `currentPhase` is still at the origin for the whole thing — check both, not just the
    // incoming target. OFFERED joins this same branch since waiting <-> p2p and p2p -> uploading
    // both live here too (the p2p dots settle onto the exact same canonical positions the
    // chevron already starts from, so the two families share one Canvas block).
    val inArrowFamily = phase == CheckmarkPhase.UPLOADING || currentPhase == CheckmarkPhase.UPLOADING ||
        phase == CheckmarkPhase.OFFERED || currentPhase == CheckmarkPhase.OFFERED
    if (inArrowFamily) {
        Canvas(modifier = modifier) {
            val s = size.minDimension / 24f

            if (p2pDotShift.value > 0f || phoneGrow.value > 0f) {
                // P2P family: dots sliding onto (or off of) ic_p2p_24dp's own transfer dots,
                // with the two phone brackets zooming in/out on either side of them. Mutually
                // exclusive with the chevron drawing below in time — these two progresses are
                // always driven to exactly 0 before reposition/expand1/expand2 ever move.
                val dotRadius = lerp(DOT_RADIUS, P2P_DOT_RADIUS, p2pDotShift.value)
                val d0 = androidx.compose.ui.geometry.lerp(DOT1, P2P_DOT1, p2pDotShift.value)
                val d1 = androidx.compose.ui.geometry.lerp(DOT2, P2P_DOT2, p2pDotShift.value)
                val d2 = androidx.compose.ui.geometry.lerp(DOT3, P2P_DOT3, p2pDotShift.value)
                drawCircle(color = grayColor, radius = dotRadius * s, center = d0 * s)
                drawCircle(color = grayColor, radius = dotRadius * s, center = d1 * s)
                drawCircle(color = grayColor, radius = dotRadius * s, center = d2 * s)

                if (phoneGrow.value > 0f) {
                    val phoneStroke = androidx.compose.ui.graphics.drawscope.Stroke(width = CHECK_STROKE_WIDTH * s)
                    val cornerRadius =
                        androidx.compose.ui.geometry.CornerRadius(PHONE_CORNER_RADIUS * s, PHONE_CORNER_RADIUS * s)
                    scale(phoneGrow.value, pivot = P2P_DOT1 * s) {
                        drawRoundRect(
                            color = grayColor,
                            topLeft = PHONE_LEFT_TOP_LEFT * s,
                            size = androidx.compose.ui.geometry.Size(
                                (PHONE_LEFT_BOTTOM_RIGHT.x - PHONE_LEFT_TOP_LEFT.x) * s,
                                (PHONE_LEFT_BOTTOM_RIGHT.y - PHONE_LEFT_TOP_LEFT.y) * s,
                            ),
                            cornerRadius = cornerRadius,
                            style = phoneStroke,
                        )
                    }
                    scale(phoneGrow.value, pivot = P2P_DOT3 * s) {
                        drawRoundRect(
                            color = grayColor,
                            topLeft = PHONE_RIGHT_TOP_LEFT * s,
                            size = androidx.compose.ui.geometry.Size(
                                (PHONE_RIGHT_BOTTOM_RIGHT.x - PHONE_RIGHT_TOP_LEFT.x) * s,
                                (PHONE_RIGHT_BOTTOM_RIGHT.y - PHONE_RIGHT_TOP_LEFT.y) * s,
                            ),
                            cornerRadius = cornerRadius,
                            style = phoneStroke,
                        )
                    }
                }
            } else {

            // Base stroke width thins from the dot's own diameter (expand1 == 0) down to the
            // checkmark/chevron's real width as expand1 grows — same as always. arrowMergeToCenter
            // then thins it the rest of the way to that same final width regardless of expand1,
            // since uploading -> cancelled retracts the chevron arms (parking strokeW back at the
            // fat dot width) before merging into the X, whose strokes must be thin, not fat.
            val baseStrokeW = lerp(DOT_RADIUS * 2f, CHECK_STROKE_WIDTH, expand1.value)
            val strokeW = lerp(baseStrokeW, CHECK_STROKE_WIDTH, arrowMergeToCenter.value)

            val chevronPos0 = androidx.compose.ui.geometry.lerp(DOT1, CHEVRON_LEFT, reposition.value)
            val chevronPos1 = androidx.compose.ui.geometry.lerp(DOT2, CHEVRON_APEX, reposition.value)
            val chevronPos2 = androidx.compose.ui.geometry.lerp(DOT3, CHEVRON_RIGHT, reposition.value)
            // Only ever driven away from 0 by the uploading -> sent transition, which slides the
            // chevron's own two strokes straight into the checkmark's — no dots reappearing in
            // between, since that transition never touches reposition/the dot positions at all.
            val checkPos0 = androidx.compose.ui.geometry.lerp(chevronPos0, CHECK_P0, chevronToCheck.value)
            val checkPos1 = androidx.compose.ui.geometry.lerp(chevronPos1, CHECK_P1, chevronToCheck.value)
            val checkPos2 = androidx.compose.ui.geometry.lerp(chevronPos2, CHECK_P2, chevronToCheck.value)
            // Only ever driven away from 0 by uploading -> cancelled: the chevron's three points
            // (wherever chevronToCheck currently has them, ordinarily their own three corners)
            // converging onto the cancel-X's single center point.
            val pos0 = androidx.compose.ui.geometry.lerp(checkPos0, CANCEL_CENTER, arrowMergeToCenter.value)
            val pos1 = androidx.compose.ui.geometry.lerp(checkPos1, CANCEL_CENTER, arrowMergeToCenter.value)
            val pos2 = androidx.compose.ui.geometry.lerp(checkPos2, CANCEL_CENTER, arrowMergeToCenter.value)

            // Chevron: identical technique to the checkmark's two-segment growth.
            drawCircle(color = grayColor, radius = strokeW / 2f * s, center = pos2 * s)
            drawLine(
                color = grayColor,
                start = pos0 * s,
                end = androidx.compose.ui.geometry.lerp(pos0, pos1, expand1.value) * s,
                strokeWidth = strokeW * s,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = grayColor,
                start = pos1 * s,
                end = androidx.compose.ui.geometry.lerp(pos1, pos2, expand2.value) * s,
                strokeWidth = strokeW * s,
                cap = StrokeCap.Round,
            )

            // Stem: grows straight down from the chevron's apex once it's fully formed. Guarded
            // so a fully-retracted stem draws nothing at all, rather than leaving a stray dot
            // sitting at its old anchor while the chevron above it slides away into a checkmark.
            if (stemProgress.value > 0f) {
                val stemEnd = androidx.compose.ui.geometry.lerp(pos1, STEM_BOTTOM, stemProgress.value)
                drawLine(
                    color = grayColor,
                    start = pos1 * s,
                    end = stemEnd * s,
                    strokeWidth = strokeW * s,
                    cap = StrokeCap.Round,
                )
            }

            // Tray: its four corners unfold outward from one point below the stem instead of
            // fading in flat — the same "grow from a point" language as the dots themselves.
            // Same guard as the stem: fully collapsed draws nothing, no leftover dot.
            if (trayProgress.value > 0f) {
                val trayLeftTop = androidx.compose.ui.geometry.lerp(TRAY_COLLAPSE_ORIGIN, TRAY_LEFT_TOP, trayProgress.value)
                val trayLeftBottom =
                    androidx.compose.ui.geometry.lerp(TRAY_COLLAPSE_ORIGIN, TRAY_LEFT_BOTTOM, trayProgress.value)
                val trayRightBottom =
                    androidx.compose.ui.geometry.lerp(TRAY_COLLAPSE_ORIGIN, TRAY_RIGHT_BOTTOM, trayProgress.value)
                val trayRightTop =
                    androidx.compose.ui.geometry.lerp(TRAY_COLLAPSE_ORIGIN, TRAY_RIGHT_TOP, trayProgress.value)
                drawCircle(color = grayColor, radius = strokeW / 2f * s, center = trayLeftBottom * s)
                drawCircle(color = grayColor, radius = strokeW / 2f * s, center = trayRightBottom * s)
                drawLine(grayColor, trayLeftTop * s, trayLeftBottom * s, strokeW * s, cap = StrokeCap.Round)
                drawLine(grayColor, trayLeftBottom * s, trayRightBottom * s, strokeW * s, cap = StrokeCap.Round)
                drawLine(grayColor, trayRightBottom * s, trayRightTop * s, strokeW * s, cap = StrokeCap.Round)
            }

            // Cancel cross: once the chevron's three points have merged into the X's center
            // (arrowMergeToCenter reaching 1), four strokes grow outward from it to the X's own
            // four tips — the arrow having quite literally turned into the cross.
            if (cancelCrossGrow.value > 0f) {
                val armStrokeW = CHECK_STROKE_WIDTH * s
                val tl = androidx.compose.ui.geometry.lerp(CANCEL_CENTER, CANCEL_TOP_LEFT, cancelCrossGrow.value)
                val tr = androidx.compose.ui.geometry.lerp(CANCEL_CENTER, CANCEL_TOP_RIGHT, cancelCrossGrow.value)
                val bl = androidx.compose.ui.geometry.lerp(CANCEL_CENTER, CANCEL_BOTTOM_LEFT, cancelCrossGrow.value)
                val br = androidx.compose.ui.geometry.lerp(CANCEL_CENTER, CANCEL_BOTTOM_RIGHT, cancelCrossGrow.value)
                drawLine(grayColor, CANCEL_CENTER * s, tl * s, armStrokeW, cap = StrokeCap.Round)
                drawLine(grayColor, CANCEL_CENTER * s, tr * s, armStrokeW, cap = StrokeCap.Round)
                drawLine(grayColor, CANCEL_CENTER * s, bl * s, armStrokeW, cap = StrokeCap.Round)
                drawLine(grayColor, CANCEL_CENTER * s, br * s, armStrokeW, cap = StrokeCap.Round)
            }
            }
        }
        return
    }

    // Same "check both, not just the incoming target" reasoning as inArrowFamily above — during
    // read -> listening `phase` is already LISTENING while `currentPhase` is still READ for the
    // whole transition.
    val inHeadphoneFamily = phase.isListenPhase() || currentPhase.isListenPhase()
    if (inHeadphoneFamily) {
        // Within the headphone family itself (listening <-> listened / listen_unknown / paused,
        // in either direction) the icon never changes shape at all — only scale (a bounce) and
        // tint change, exactly like the delivered -> read kick above — so this renders the one
        // real bundled headphone asset the whole time instead of approximating anything. Only
        // arriving FROM the checkmark family is a genuine shape change, handled below.
        if (currentPhase.isListenPhase() && phase.isListenPhase()) {
            val listenTint = when (currentPhase) {
                CheckmarkPhase.LISTENED -> androidx.compose.ui.graphics.lerp(grayColor, listenedColor, colorProgress.value)
                CheckmarkPhase.LISTEN_UNKNOWN -> androidx.compose.ui.graphics.lerp(grayColor, unknownColor, colorProgress.value)
                else -> grayColor
            }
            Icon(
                painter = painterResource(R.drawable.ic_headphones_24dp),
                contentDescription = null,
                tint = listenTint,
                modifier = modifier.graphicsLayer(scaleX = bounceScale.value, scaleY = bounceScale.value),
            )
            return
        }

        // A genuine shape change — the checkmark family arriving at the headphone (or, in
        // principle, leaving it, though nothing in ListenStatusManager's state machine actually
        // does that once a listen event has fired). A real point-to-point morph between the two
        // real outlines (see the "Checkmark <-> headphone morph" section at the bottom of this
        // file): whichever of singleCheckMorphPoints/doubleCheckMorphPoints matches what was
        // actually showing lerps index-for-index into headphoneOutlinePoints as morphProgress
        // advances — both endpoints are the real geometry the whole time, only the blend between
        // them is invented.
        val fromPoints = if (currentPhase == CheckmarkPhase.SENT) singleCheckMorphPoints else doubleCheckMorphPoints
        val fromColor = if (currentPhase == CheckmarkPhase.READ) successColor else grayColor
        val tintColor = androidx.compose.ui.graphics.lerp(fromColor, grayColor, morphProgress.value)
        Canvas(modifier = modifier) {
            val s = size.minDimension / 24f
            val t = morphProgress.value
            val morphPath = Path()
            for (idx in 0 until MORPH_SAMPLE_COUNT) {
                val p = androidx.compose.ui.geometry.lerp(fromPoints[idx], headphoneOutlinePoints[idx], t) * s
                if (idx == 0) morphPath.moveTo(p.x, p.y) else morphPath.lineTo(p.x, p.y)
            }
            morphPath.close()
            drawPath(morphPath, color = tintColor)
        }
        return
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
 * a stroked checkmark would have — used as a clip mask for the double-check "trace" effect above,
 * and (see the checkmark<->headphone morph section at the bottom of this file) as the real
 * checkmark's own outline for point-sampling, since it's built from this file's own real,
 * already-vetted checkmark geometry (CHECK_P0/P1/P2, CHECK_STROKE_WIDTH) rather than approximated
 * separately for that purpose. */
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

// ─── Checkmark <-> headphone morph ──────────────────────────────────────────
//
// A real point-to-point morph between the two actual outlines, not a crossfade: the checkmark
// side reuses this file's own exact stroke geometry (CHECK_P0/P1/P2, CHECK_STROKE_WIDTH,
// DOUBLE_CHECK_OFFSET — the same numbers every other checkmark drawing in this file already uses,
// via checkmarkCoverage above), and the headphone side is the real Google Material Symbols
// Rounded "headphones" (fill1) path, transcribed command-for-command from the verified SVG
// fetched from fonts.gstatic.com (see ic_headphones_24dp.xml's own header comment for the
// verification) rather than approximated. Both get resampled to the same point count via
// PathMeasure and lerped index-for-index each frame — the only invented part is the blend between
// two real shapes, not either shape itself.

/** The real headphone glyph, transcribed 1:1 from the same verified SVG command sequence as
 * ic_headphones_24dp.xml (h/v -> relativeLineTo, q -> relativeQuadraticBezierTo, the T/t
 * smooth-quadratic shorthand expanded to its reflected control point by hand ahead of time — SVG's
 * T/t has no direct Compose Path equivalent) rather than parsed at runtime, so this is exactly as
 * auditable against the source string as the XML file is. */
private fun buildHeadphonePath(): Path = Path().apply {
    moveTo(7.0000f, 21.0000f)
    lineTo(5.0000f, 21.0000f)
    quadraticBezierTo(4.1750f, 21.0000f, 3.5875f, 20.4125f)
    quadraticBezierTo(3.0000f, 19.8250f, 3.0000f, 19.0000f)
    lineTo(3.0000f, 12.0000f)
    quadraticBezierTo(3.0000f, 10.1250f, 3.7125f, 8.4875f)
    quadraticBezierTo(4.4250f, 6.8500f, 5.6375f, 5.6375f)
    quadraticBezierTo(6.8500f, 4.4250f, 8.4875f, 3.7125f)
    quadraticBezierTo(10.1250f, 3.0000f, 12.0000f, 3.0000f)
    quadraticBezierTo(13.8750f, 3.0000f, 15.5125f, 3.7125f)
    quadraticBezierTo(17.1500f, 4.4250f, 18.3625f, 5.6375f)
    quadraticBezierTo(19.5750f, 6.8500f, 20.2875f, 8.4875f)
    quadraticBezierTo(21.0000f, 10.1250f, 21.0000f, 12.0000f)
    lineTo(21.0000f, 19.0000f)
    quadraticBezierTo(21.0000f, 19.8250f, 20.4125f, 20.4125f)
    quadraticBezierTo(19.8250f, 21.0000f, 19.0000f, 21.0000f)
    lineTo(17.0000f, 21.0000f)
    quadraticBezierTo(16.1750f, 21.0000f, 15.5875f, 20.4125f)
    quadraticBezierTo(15.0000f, 19.8250f, 15.0000f, 19.0000f)
    lineTo(15.0000f, 15.0000f)
    quadraticBezierTo(15.0000f, 14.1750f, 15.5875f, 13.5875f)
    quadraticBezierTo(16.1750f, 13.0000f, 17.0000f, 13.0000f)
    lineTo(19.0000f, 13.0000f)
    lineTo(19.0000f, 12.0000f)
    quadraticBezierTo(19.0000f, 9.0750f, 16.9625f, 7.0375f)
    quadraticBezierTo(14.9250f, 5.0000f, 12.0000f, 5.0000f)
    quadraticBezierTo(9.0750f, 5.0000f, 7.0375f, 7.0375f)
    quadraticBezierTo(5.0000f, 9.0750f, 5.0000f, 12.0000f)
    lineTo(5.0000f, 13.0000f)
    lineTo(7.0000f, 13.0000f)
    quadraticBezierTo(7.8250f, 13.0000f, 8.4125f, 13.5875f)
    quadraticBezierTo(9.0000f, 14.1750f, 9.0000f, 15.0000f)
    lineTo(9.0000f, 19.0000f)
    quadraticBezierTo(9.0000f, 19.8250f, 8.4125f, 20.4125f)
    quadraticBezierTo(7.8250f, 21.0000f, 7.0000f, 21.0000f)
    close()
}

/** Resamples [path]'s outline into [n] evenly-spaced points by arc length via Android's own
 * PathMeasure (framework API, not a new dependency) — the same technique this file's `scale()`/
 * `drawPath()` calls already lean on `asAndroidPath()` for elsewhere (see UpdatesScreen.kt's
 * Matrix transform on a Compose Path for the same underlying reason). */
private fun samplePathPoints(path: Path, n: Int): List<Offset> {
    val measure = android.graphics.PathMeasure(path.asAndroidPath(), false)
    val length = measure.length
    val pos = FloatArray(2)
    return (0 until n).map { i ->
        measure.getPosTan(length * i / n, pos, null)
        Offset(pos[0], pos[1])
    }
}

/** Point-sampled outlines are just a list of N points walked in some direction starting somewhere
 * arbitrary — naively lerping index-for-index between two independently-sampled shapes can make
 * the morph visibly spin, if e.g. one starts at "3 o'clock" and the other at "9 o'clock," or one
 * winds clockwise and the other counterclockwise. Tries every rotation of [candidate] against
 * [reference], in both winding directions, and keeps whichever alignment minimizes total squared
 * point-to-point distance — run once (see the `by lazy` properties below), not per frame, so an
 * O(n²) search here is negligible. */
private fun bestAlignment(reference: List<Offset>, candidate: List<Offset>): List<Offset> {
    val n = reference.size
    var bestScore = Float.MAX_VALUE
    var best = candidate
    for (base in listOf(candidate, candidate.asReversed())) {
        for (rotation in 0 until n) {
            var score = 0f
            for (i in 0 until n) {
                val c = base[(i + rotation) % n]
                val r = reference[i]
                score += (c.x - r.x) * (c.x - r.x) + (c.y - r.y) * (c.y - r.y)
            }
            if (score < bestScore) {
                bestScore = score
                best = List(n) { base[(it + rotation) % n] }
            }
        }
    }
    return best
}

/** Fixed reference outline every checkmark-family shape aligns to — computed once, not per
 * composable instance, since none of these depend on any @Composable state. */
private val headphoneOutlinePoints: List<Offset> by lazy {
    samplePathPoints(buildHeadphonePath(), MORPH_SAMPLE_COUNT)
}

/** The single checkmark's real outline (SENT), aligned so index i already corresponds to
 * headphoneOutlinePoints[i] — lerp the two lists directly, no further alignment needed at draw
 * time. */
private val singleCheckMorphPoints: List<Offset> by lazy {
    val outline = checkmarkCoverage(CHECK_P0, CHECK_P1, CHECK_P2, CHECK_STROKE_WIDTH / 2f, 1f)
    bestAlignment(headphoneOutlinePoints, samplePathPoints(outline, MORPH_SAMPLE_COUNT))
}

/** The double checkmark's real outline (DELIVERED/READ) — the union of both strokes' capsule
 * shapes (front, at DOUBLE_CHECK_OFFSET, plus back, unshifted), same as how the double-check
 * actually looks on screen, not just the front stroke alone. */
private val doubleCheckMorphPoints: List<Offset> by lazy {
    val front = checkmarkCoverage(
        CHECK_P0 + DOUBLE_CHECK_OFFSET,
        CHECK_P1 + DOUBLE_CHECK_OFFSET,
        CHECK_P2 + DOUBLE_CHECK_OFFSET,
        CHECK_STROKE_WIDTH / 2f,
        1f,
    )
    val back = checkmarkCoverage(CHECK_P0, CHECK_P1, CHECK_P2, CHECK_STROKE_WIDTH / 2f, 1f)
    val union = Path().apply { op(front, back, PathOperation.Union) }
    bestAlignment(headphoneOutlinePoints, samplePathPoints(union, MORPH_SAMPLE_COUNT))
}
