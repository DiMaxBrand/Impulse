package eu.siacs.conversations.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
// LISTENING/LISTENED/LISTEN_UNKNOWN appended after CANCELLED for the same reason OFFERED/
// CANCELLED were: the WAITING..READ ordinal ordering is load-bearing for the `>=` comparisons in
// the catch-all snap branch below, and none of these five are part of that chain.
enum class CheckmarkPhase { WAITING, UPLOADING, SENT, DELIVERED, READ, OFFERED, CANCELLED, LISTENING, LISTENED, LISTEN_UNKNOWN }

private fun CheckmarkPhase.isListenPhase() =
    this == CheckmarkPhase.LISTENING || this == CheckmarkPhase.LISTENED || this == CheckmarkPhase.LISTEN_UNKNOWN

/**
 * Overrides [basePhase] with the voice-message listen-status phase once the peer has done
 * anything with a voice message we sent — the headphone glyph takes over this exact slot, it
 * isn't an extra icon bolted on next to the checkmark. PAUSED and NOT_LISTENED (or no listen
 * status at all, i.e. [listenState] null) fall through to [basePhase] unchanged — the ordinary
 * checkmark, same as any non-voice message.
 */
fun voiceCheckmarkPhase(
    basePhase: CheckmarkPhase?,
    listenState: ListenStatusManager.State?,
): CheckmarkPhase? = when (listenState) {
    ListenStatusManager.State.LISTENING -> CheckmarkPhase.LISTENING
    ListenStatusManager.State.LISTENED -> CheckmarkPhase.LISTENED
    ListenStatusManager.State.UNKNOWN -> CheckmarkPhase.LISTEN_UNKNOWN
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

// Headphone glyph, reachable only from the checkmark family (SENT/DELIVERED/READ) once a voice
// message's listen status arrives. Bespoke to this file's drawing convention rather than measured
// off a real Material Symbol — a stroke-only two-segment headband (same left/apex/right vee
// technique the chevron already uses, just opening downward instead of up) plus two ear-cup
// strokes growing straight down from its ends, same CHECK_STROKE_WIDTH as every other glyph here
// so it reads as the same family, not a bolted-on icon in a different visual language.
private val HEADBAND_LEFT = Offset(6f, 10f)
private val HEADBAND_APEX = Offset(12f, 5f)
private val HEADBAND_RIGHT = Offset(18f, 10f)
private val EAR_LEFT_BOTTOM = Offset(6f, 16f)
private val EAR_RIGHT_BOTTOM = Offset(18f, 16f)

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
 * - Read → listening (voice messages only, once the peer starts playing one we sent): the
 *   double-check (if showing) retracts back into a single checkmark — headphone is one glyph, not
 *   two — any green fades back to gray, and that single checkmark's own 3 points slide onto a
 *   headband's, then two ear-cup strokes grow straight down from its ends — a hand-drawn
 *   approximation just for the motion; the instant it settles, this swaps to the real Google
 *   Material Symbol headphone drawable, same as every other phase's own real bundled asset, so
 *   the transition's first frame is the checkmark exactly as it already looked and its last frame
 *   is the true icon exactly as it always renders at rest, pixel for pixel. Listening itself
 *   pulses continuously so it reads as "live." Listening → listened bounces to green exactly like
 *   delivered → read above; listening/listened → an extrapolation losing track bounces bouncier,
 *   to red — same spatial+effect spring language throughout, just aimed at the headphone.
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
    // Only touched by (sent/delivered/read) -> listening: the checkmark's own 3 points sliding
    // onto the headband's 3 points — same lerp-two-point-sets technique as chevronToCheck below,
    // just aimed at the headband instead of the checkmark.
    val checkToHeadband = remember { Animatable(if (phase.isListenPhase()) 1f else 0f) }
    // Same transition: two ear-cup strokes growing straight down from the headband's two ends
    // once it's finished forming — same "grow from an anchor" language as the upload icon's stem.
    val earsProgress = remember { Animatable(if (phase.isListenPhase()) 1f else 0f) }
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

    // Drives the headphone's continuous "this is live" pulse while at rest in LISTENING — the
    // only phase in this whole file that keeps animating once settled rather than stopping dead.
    // Always mounted (Compose composables must be called unconditionally); its value is only ever
    // read down in the render section, guarded to the moments it actually applies.
    val infiniteTransition = rememberInfiniteTransition(label = "listening-pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "listening-pulse-scale",
    )

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
                    to == CheckmarkPhase.LISTENING -> {
                    isAnimating = true
                    // The double-check (if showing) retracts back into a single checkmark first —
                    // headphone is one glyph, not two — while that single checkmark's own 3 points
                    // slide onto the headband's, and any green from an already-landed read marker
                    // fades back to gray at the same time, since arriving at LISTENING always
                    // starts from the plain black/gray look.
                    coroutineScope {
                        launch { doubleSlide.animateTo(0f, tween(200, easing = StandardEasing)) }
                        launch { checkToHeadband.animateTo(1f, tween(260, easing = StandardEasing)) }
                        launch { colorProgress.animateTo(0f, tween(200, easing = StandardEasing)) }
                    }
                    earsProgress.animateTo(1f, tween(220, easing = StandardEasing))
                }
                from == CheckmarkPhase.LISTENING && to == CheckmarkPhase.LISTENED -> {
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
                    // multi-second morph the user never saw the start of. Never worth animating,
                    // so isAnimating stays false — which renders the static drawable for the
                    // ordinary checkmark family, or the headphone glyph at its plain resting look
                    // for the listen family (it has no static-drawable shortcut of its own).
                    reposition.snapTo(if (to >= CheckmarkPhase.SENT) 1f else 0f)
                    expand1.snapTo(if (to >= CheckmarkPhase.SENT) 1f else 0f)
                    expand2.snapTo(if (to >= CheckmarkPhase.SENT) 1f else 0f)
                    doubleSlide.snapTo(if (to >= CheckmarkPhase.DELIVERED) 1f else 0f)
                    bounceScale.snapTo(1f)
                    checkToHeadband.snapTo(if (to.isListenPhase()) 1f else 0f)
                    earsProgress.snapTo(if (to.isListenPhase()) 1f else 0f)
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
        val (staticDrawable, staticColor) = when (currentPhase) {
            CheckmarkPhase.WAITING -> R.drawable.ic_more_horiz_24dp to grayColor
            CheckmarkPhase.UPLOADING -> R.drawable.ic_upload_24dp to grayColor
            CheckmarkPhase.SENT -> R.drawable.ic_done_24dp to grayColor
            CheckmarkPhase.DELIVERED -> R.drawable.ic_done_all_24dp to grayColor
            CheckmarkPhase.READ -> R.drawable.ic_done_all_bold_24dp to successColor
            CheckmarkPhase.OFFERED -> R.drawable.ic_p2p_24dp to grayColor
            CheckmarkPhase.CANCELLED -> R.drawable.ic_cancel_24dp to grayColor
            // The real Google Material Symbol (rounded, filled) — not the hand-drawn stroke
            // approximation the Canvas block below uses while mid-morph. That approximation only
            // has to get the *shape* of the transition right; the settled look, same as every
            // other phase here, is the actual bundled asset, pixel-exact.
            CheckmarkPhase.LISTENING -> R.drawable.ic_headphones_24dp to grayColor
            CheckmarkPhase.LISTENED -> R.drawable.ic_headphones_24dp to listenedColor
            CheckmarkPhase.LISTEN_UNKNOWN -> R.drawable.ic_headphones_24dp to unknownColor
        }
        // LISTENING alone keeps animating even at rest — the continuous "this is live" pulse —
        // so it's the one phase in this whole file where !isAnimating doesn't mean "nothing is
        // moving." pulseScale is itself a continuously-updating animation value, so reading it
        // here keeps this Icon recomposing every frame without needing isAnimating to stay true.
        val iconModifier = if (currentPhase == CheckmarkPhase.LISTENING) {
            modifier.graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
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
        Canvas(modifier = modifier) {
            val s = size.minDimension / 24f
            // Only ever reached once past the dots stage (checkmark family or later), so the
            // stroke is always at its real final width — no thin-dot lerp needed here, and "as
            // thick as the checkmark icons" falls out of reusing the exact same constant.
            val strokeW = CHECK_STROKE_WIDTH

            val basePos0 = androidx.compose.ui.geometry.lerp(DOT1, CHECK_P0, reposition.value)
            val basePos1 = androidx.compose.ui.geometry.lerp(DOT2, CHECK_P1, reposition.value)
            val basePos2 = androidx.compose.ui.geometry.lerp(DOT3, CHECK_P2, reposition.value)

            // The checkmark's own 3 points sliding onto the headband's — same technique
            // chevronToCheck uses above, different target shape.
            val headPos0 = androidx.compose.ui.geometry.lerp(basePos0, HEADBAND_LEFT, checkToHeadband.value)
            val headPos1 = androidx.compose.ui.geometry.lerp(basePos1, HEADBAND_APEX, checkToHeadband.value)
            val headPos2 = androidx.compose.ui.geometry.lerp(basePos2, HEADBAND_RIGHT, checkToHeadband.value)

            val listenTint = when (currentPhase) {
                CheckmarkPhase.LISTENED -> androidx.compose.ui.graphics.lerp(grayColor, listenedColor, colorProgress.value)
                CheckmarkPhase.LISTEN_UNKNOWN -> androidx.compose.ui.graphics.lerp(grayColor, unknownColor, colorProgress.value)
                // LISTENING (or still mid-transition into it) — plain black/gray throughout.
                else -> grayColor
            }
            // This block only ever runs while isAnimating is true — i.e. mid-morph — so the
            // continuous idle pulse (which only applies once fully settled and idle in LISTENING)
            // never actually applies here; it lives in the static-icon branch above instead. Any
            // in-flight bounce (listening -> listened / -> listen_unknown) uses bounceScale, same
            // as the checkmark's own delivered -> read kick; otherwise it's a plain 1f.
            scale(bounceScale.value) {
                // Headband: a real curve through the same 3 interpolated points the chevron/
                // checkmark above uses two straight segments for — headPos1 is a Bézier control
                // point here, not a vertex the line actually touches, so this reads as a rounded
                // arch the whole way through the morph. Drawing it as two straight segments
                // meeting at a hard point (the technique reused elsewhere in this file) reportedly
                // reads as a peaked roof mid-transition instead of a headphone band, which two
                // straight lines meeting at a point will always risk once the shape itself is
                // trying to look organic/curved rather than mechanical.
                val headbandPath = Path().apply {
                    val p0 = headPos0 * s
                    val p1 = headPos1 * s
                    val p2 = headPos2 * s
                    moveTo(p0.x, p0.y)
                    quadraticBezierTo(p1.x, p1.y, p2.x, p2.y)
                }
                drawPath(
                    path = headbandPath,
                    color = listenTint,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW * s, cap = StrokeCap.Round),
                )

                // Ear cups: grow straight down from the headband's two ends once it's formed —
                // same "grow from an anchor" language as the upload icon's stem.
                if (earsProgress.value > 0f) {
                    val earLeft = androidx.compose.ui.geometry.lerp(headPos0, EAR_LEFT_BOTTOM, earsProgress.value)
                    val earRight = androidx.compose.ui.geometry.lerp(headPos2, EAR_RIGHT_BOTTOM, earsProgress.value)
                    drawLine(listenTint, headPos0 * s, earLeft * s, strokeW * s, cap = StrokeCap.Round)
                    drawLine(listenTint, headPos2 * s, earRight * s, strokeW * s, cap = StrokeCap.Round)
                }
            }
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
