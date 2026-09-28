package eu.siacs.conversations.ui

import androidx.compose.runtime.mutableStateMapOf

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
 * signal, exactly like voice listen-status). Video (once wired up) additionally uses UNKNOWN — the
 * external player was launched and a generous timer (duration + 5s) expired with the peer's
 * Impulse never having come back to the foreground, so it's presumed watched but not confirmed.
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
}
