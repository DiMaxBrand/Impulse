package eu.siacs.conversations.ui

import androidx.compose.runtime.mutableStateMapOf
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.services.XmppConnectionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tracks what the peer is doing with images/video WE sent (1:1 chats only, Impulse-to-Impulse).
 *
 * Deliberately its own extension/namespace rather than reusing ListenStatusManager's wire tokens
 * — viewing an image isn't "listening," and unlike voice playback (which Impulse fully controls
 * end-to-end) video plays in an external app, so the peer's client can only ever report a
 * best-effort estimate for video, never a certain one. Keeping the two separate means this
 * confidence difference doesn't have to be smuggled into the audio-specific protocol.
 *
 * Images report VIEWING/VIEWED with full certainty, but only once the viewer has actually
 * dwelled on the image for at least MIN_DWELL_MS — a fast swipe-through never sends anything at
 * all, leaving the ordinary checkmark in place, rather than flashing gray-then-green for a photo
 * nobody actually looked at (see MediaViewerActivity's debounced DisposableEffect).
 *
 * Video can't get that same certain signal (no in-app playback), so it infers from when the
 * viewer's Impulse comes back to the foreground after tapping play, relative to the video's own
 * duration — see onVideoPlayTapped/onAppForegrounded below for the exact thresholds. Four
 * outcomes: back too fast to mean anything (silently NOT_VIEWED, same as an image glance under a
 * second), back close to when the video should have ended (VIEWED), back late-but-not-that-late
 * (HALF_VIEWED — genuinely watched some of it, just not confirmed to the end), or never came back
 * before the duration+5s timer itself expired (UNKNOWN).
 *
 * Terminal VIEWED is persisted via the Message entity (see viewStatus there) so it survives
 * restarts; every other state is ephemeral and lives here only.
 */
object ViewStatusManager {

    enum class State { NOT_VIEWED, VIEWING, VIEWED, HALF_VIEWED, UNKNOWN }

    /** Wire tokens — descriptive strings, not integers, so the protocol stays readable. */
    const val WIRE_VIEWING = "viewing"
    const val WIRE_VIEWED = "viewed"
    const val WIRE_HALF_VIEWED = "half_viewed"
    const val WIRE_UNKNOWN = "unknown"

    /** Sent after a "viewing" the viewer then backed out of too quickly to count (a video they
     * came straight back from): clears the sender's gray eye so it returns to the plain checkmark
     * instead of staying "viewing" forever. */
    const val WIRE_NOT_VIEWED = "not_viewed"

    /** A return-to-foreground (video) or a viewer dwell (image) shorter than this never counts as
     * having actually looked at anything — no wire message is sent at all, so the sender's
     * checkmark just stays whatever it already was. */
    const val MIN_ENGAGEMENT_MS = 5_000L

    /** Same idea as MIN_ENGAGEMENT_MS above, but for images specifically — a full second, not
     * five, since dwelling on a still image doesn't need nearly as much time to count as a real
     * look as actually watching video does. */
    const val MIN_IMAGE_DWELL_MS = 1_000L

    /** How close to the video's own duration a foreground-return has to land, in either
     * direction, to count as a full VIEWED rather than just a HALF_VIEWED. */
    const val NEAR_DURATION_WINDOW_MS = 5_000L

    /** Keyed by the OUTGOING message's local uuid. Compose-observable. */
    val peerStates = mutableStateMapOf<String, State>()

    /** Called from MessageParser when a view-status stanza arrives for one of our messages. */
    @JvmStatic
    fun onPeerTransition(uuid: String, wireState: String) {
        if (wireState == WIRE_NOT_VIEWED) {
            peerStates.remove(uuid)
            return
        }
        peerStates[uuid] = when (wireState) {
            WIRE_VIEWING -> State.VIEWING
            WIRE_VIEWED -> State.VIEWED
            WIRE_HALF_VIEWED -> State.HALF_VIEWED
            WIRE_UNKNOWN -> State.UNKNOWN
            else -> return
        }
    }

    // Own long-lived scope, not tied to any Activity/Fragment -- a video's resolution (duration +
    // 5s, or the app coming back to the foreground) routinely outlives the MediaViewerActivity
    // that launched the external player, which is the whole point: we need to know what happened
    // *after* that screen is gone. Scoped to the process, same as peerStates above -- if the
    // process dies mid-timer the pending resolution is simply lost, no different from any other
    // in-memory state here.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private class PendingVideoView(
        val service: XmppConnectionService,
        val message: Message,
        val job: Job,
        val tappedAtMs: Long,
        val durationMs: Int,
    )

    /** Keyed by the INCOMING video message's local uuid -- the viewer's own pending timers, not
     * the peer's. Concurrent: written from this object's coroutines, read and cleared on the main
     * thread by onAppForegrounded(). */
    private val pendingVideoViews = java.util.concurrent.ConcurrentHashMap<String, PendingVideoView>()

    /** Called when the viewer taps a video's play button, right before handing off to the
     * external player -- Impulse has no built-in video playback yet, so this is the only real
     * signal available.
     *
     * The video's length is read from the downloaded file here, on a background thread: a video
     * message's body never carries a runtime (updateFileParams only writes one for audio), so
     * FileParams.runtime is always 0 for video and relying on it silently made this a no-op -- the
     * sender never saw the eye. The tap time is captured first, before that read, so the
     * "how long were they gone" arithmetic still starts at the tap. A video whose length still
     * can't be determined is skipped: there is nothing honest to time a guess against. */
    fun onVideoPlayTapped(service: XmppConnectionService, message: Message) {
        val uuid = message.getUuid() ?: return
        if (message.status != Message.STATUS_RECEIVED) return
        val conversation = message.conversation as? Conversation ?: return
        if (conversation.getMode() != Conversational.MODE_SINGLE) return
        val tappedAtMs = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            val durationMs = message.fileParams.runtime.takeIf { it > 0 }
                ?: service.fileBackend.getLocalMediaRuntime(message)
            if (durationMs <= 0) return@launch
            pendingVideoViews.remove(uuid)?.job?.cancel()
            sendViewStatusStanza(service, message, WIRE_VIEWING)
            val job = scope.launch {
                delay(durationMs.toLong() + NEAR_DURATION_WINDOW_MS)
                pendingVideoViews.remove(uuid)
                sendViewStatusStanza(service, message, WIRE_UNKNOWN)
            }
            pendingVideoViews[uuid] = PendingVideoView(service, message, job, tappedAtMs, durationMs)
        }
    }

    /** Called from XmppConnectionService's own ProcessLifecycleOwner observer whenever the app
     * returns to the foreground -- resolves every still-pending video view against how long ago
     * play was tapped, relative to that video's own duration:
     *  - under the engagement floor (5s, or the video's own length if shorter): too fast to mean
     *    anything -- the earlier "viewing" is taken back (WIRE_NOT_VIEWED), back to the checkmark.
     *  - within NEAR_DURATION_WINDOW_MS of the real duration (either side -- a short video
     *    finishing slightly early counts the same as one that ran a little long): VIEWED, the
     *    generous-but-real "they were gone about as long as the video runs" signal.
     *  - anything else past the engagement floor: HALF_VIEWED -- they came back, so they
     *    definitely looked at *something*, just not confirmed to the end.
     */
    @JvmStatic
    fun onAppForegrounded() {
        if (pendingVideoViews.isEmpty()) return
        val now = System.currentTimeMillis()
        // Removed one by one rather than copy-then-clear, so an entry added by a concurrent tap
        // between the two steps can't be silently dropped.
        val resolved = pendingVideoViews.keys.toList().mapNotNull { pendingVideoViews.remove(it) }
        for (pending in resolved) {
            pending.job.cancel()
            val elapsed = now - pending.tappedAtMs
            // The floor can't exceed the video itself: a 3-second video watched to the end and
            // returned from at 3-4s is a real view, not "too fast to count".
            val floor = minOf(MIN_ENGAGEMENT_MS, pending.durationMs.toLong())
            if (elapsed < floor) {
                // "viewing" already went out at the tap; take it back so the sender's eye doesn't
                // stay gray forever.
                sendViewStatusStanza(pending.service, pending.message, WIRE_NOT_VIEWED)
                continue
            }
            val nearDuration = elapsed in
                (pending.durationMs - NEAR_DURATION_WINDOW_MS)..(pending.durationMs + NEAR_DURATION_WINDOW_MS)
            val wireState = if (nearDuration) WIRE_VIEWED else WIRE_HALF_VIEWED
            sendViewStatusStanza(pending.service, pending.message, wireState)
        }
    }
}

/** Tells the sender what we're doing with their image/video -- own namespace, not reused from
 * ListenStatusManager's (see this file's own doc for why). 1:1 chats only; only ever fires for
 * INCOMING messages (checked by callers). Shared by MediaViewerActivity's image open/close pair
 * and ViewStatusManager's own video timer above, so the wire format lives in exactly one place. */
fun sendViewStatusStanza(
    service: XmppConnectionService,
    message: Message,
    wireState: String,
) {
    val conversation = message.conversation as? Conversation ?: return
    if (conversation.getMode() != Conversational.MODE_SINGLE) return
    val packet = im.conversations.android.xmpp.model.stanza.Message()
    packet.setFrom(conversation.getAccount().jid)
    packet.setTo(message.counterpart.asBareJid())
    val el = eu.siacs.conversations.xml.Element(
        "viewing",
        eu.siacs.conversations.xml.Namespace.IMPULSE_VIEW_STATUS,
    )
    el.setAttribute("id", message.remoteMsgId ?: message.getUuid())
    el.setAttribute("state", wireState)
    packet.addChild(el)
    // Same reasoning as listen-status: the ephemeral "viewing" transition is worthless hours
    // later, but the terminal "viewed" (and, for video, the best-effort "unknown") are worth
    // delivering even if the sender is offline right now.
    if (wireState == ViewStatusManager.WIRE_VIEWING || wireState == ViewStatusManager.WIRE_NOT_VIEWED) {
        packet.addExtension(im.conversations.android.xmpp.model.hints.NoStore())
    } else {
        packet.addExtension(im.conversations.android.xmpp.model.hints.Store())
    }
    service.sendMessagePacket(conversation.getAccount(), packet)
}
