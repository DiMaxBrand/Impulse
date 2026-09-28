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
 * Images report VIEWING/VIEWED with full certainty (the in-app viewer opening/closing is a hard
 * signal, exactly like voice listen-status). Video uses the same three states, but VIEWED and
 * UNKNOWN are both inferred, never certain — see onVideoPlayTapped/onAppForegrounded below.
 *
 * Terminal VIEWED is persisted via the Message entity (see viewStatus there) so it survives
 * restarts; VIEWING/UNKNOWN are ephemeral and live here only.
 */
object ViewStatusManager {

    enum class State { NOT_VIEWED, VIEWING, VIEWED, UNKNOWN }

    /** Wire tokens — descriptive strings, not integers, so the protocol stays readable. */
    const val WIRE_VIEWING = "viewing"
    const val WIRE_VIEWED = "viewed"
    const val WIRE_UNKNOWN = "unknown"

    /** Keyed by the OUTGOING message's local uuid. Compose-observable. */
    val peerStates = mutableStateMapOf<String, State>()

    /** Called from MessageParser when a view-status stanza arrives for one of our messages. */
    @JvmStatic
    fun onPeerTransition(uuid: String, wireState: String) {
        peerStates[uuid] = when (wireState) {
            WIRE_VIEWING -> State.VIEWING
            WIRE_VIEWED -> State.VIEWED
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
    )

    /** Keyed by the INCOMING video message's local uuid -- the viewer's own pending timers, not
     * the peer's. */
    private val pendingVideoViews = mutableMapOf<String, PendingVideoView>()

    /** Called when the viewer taps a video's play button, right before handing off to the
     * external player -- Impulse has no built-in video playback yet, so this is the only real
     * signal available. [durationMs] is the video's own known runtime (FileParams.runtime, itself
     * already milliseconds — see AudioPlayer.formatTime's identical assumption for voice
     * messages); a message with no known duration is silently skipped, since there is nothing
     * honest to time a guess against. */
    fun onVideoPlayTapped(service: XmppConnectionService, message: Message, durationMs: Int) {
        val uuid = message.getUuid() ?: return
        if (message.status != Message.STATUS_RECEIVED) return
        val conversation = message.conversation as? Conversation ?: return
        if (conversation.getMode() != Conversational.MODE_SINGLE) return
        if (durationMs <= 0) return
        pendingVideoViews.remove(uuid)?.job?.cancel()
        sendViewStatusStanza(service, message, WIRE_VIEWING)
        val job = scope.launch {
            delay(durationMs.toLong() + 5_000L)
            pendingVideoViews.remove(uuid)
            sendViewStatusStanza(service, message, WIRE_UNKNOWN)
        }
        pendingVideoViews[uuid] = PendingVideoView(service, message, job)
    }

    /** Called from XmppConnectionService's own ProcessLifecycleOwner observer whenever the app
     * returns to the foreground -- resolves every still-pending video view as VIEWED, on the
     * (deliberately generous) theory that coming back to Impulse at all after tapping play means
     * the video was actually watched, not that it's being timed to the second. */
    @JvmStatic
    fun onAppForegrounded() {
        if (pendingVideoViews.isEmpty()) return
        val resolved = ArrayList(pendingVideoViews.values)
        pendingVideoViews.clear()
        for (pending in resolved) {
            pending.job.cancel()
            sendViewStatusStanza(pending.service, pending.message, WIRE_VIEWED)
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
    if (wireState == ViewStatusManager.WIRE_VIEWING) {
        packet.addExtension(im.conversations.android.xmpp.model.hints.NoStore())
    } else {
        packet.addExtension(im.conversations.android.xmpp.model.hints.Store())
    }
    service.sendMessagePacket(conversation.getAccount(), packet)
}
