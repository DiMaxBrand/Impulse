package eu.siacs.conversations.ui

import androidx.compose.runtime.mutableStateMapOf

/**
 * Tracks what the peer is doing with voice messages WE sent (1:1 chats only, Impulse-to-Impulse).
 *
 * The peer sends a stanza only on state TRANSITIONS (listening / paused / listened) — never
 * periodic progress. Between transitions the sender's UI extrapolates the peer's position
 * locally from wall-clock time, which works because playback is 1x speed: position advances
 * exactly as fast as time does. [accumulatedMs] holds listened time up to the last transition;
 * while LISTENING, the live estimate adds `now - listeningSinceMs` on top.
 *
 * Also tracks, in [localListened], which INCOMING voice messages the local user has fully
 * listened to (drives the "Not listened" footer label on incoming bubbles). Purely local —
 * nothing is sent for this beyond the transition stanzas themselves.
 *
 * Everything here is in-memory; terminal states additionally persist via the Message entity
 * (see listenStatus there) so they survive restarts.
 */
object ListenStatusManager {

    enum class State { NOT_LISTENED, LISTENING, PAUSED, LISTENED, UNKNOWN }

    /** Wire tokens — descriptive strings, not integers, so the protocol stays readable. */
    const val WIRE_LISTENING = "listening"
    const val WIRE_PAUSED = "paused"
    const val WIRE_LISTENED = "listened"

    data class PeerState(
        val state: State,
        /** Wall-clock ms when the current LISTENING stretch began; 0 unless state == LISTENING. */
        val listeningSinceMs: Long,
        /** Total listened ms accumulated across previous listening stretches. */
        val accumulatedMs: Long,
    )

    /** Keyed by the OUTGOING message's local uuid. Compose-observable. */
    val peerStates = mutableStateMapOf<String, PeerState>()

    /** Incoming-message uuids the local user has fully listened to. Compose-observable. */
    val localListened = mutableStateMapOf<String, Boolean>()

    fun estimatedListenedMs(uuid: String, nowMs: Long = System.currentTimeMillis()): Long {
        val s = peerStates[uuid] ?: return 0L
        return if (s.state == State.LISTENING) s.accumulatedMs + (nowMs - s.listeningSinceMs)
        else s.accumulatedMs
    }

    /** Called from MessageParser when a listen-status stanza arrives for one of our messages. */
    @JvmStatic
    fun onPeerTransition(uuid: String, wireState: String) {
        val now = System.currentTimeMillis()
        val prev = peerStates[uuid]
        when (wireState) {
            WIRE_LISTENING ->
                peerStates[uuid] = PeerState(
                    State.LISTENING,
                    listeningSinceMs = now,
                    // Only a genuine resume (from PAUSED) carries prior progress forward.
                    // Starting fresh from any other prior state — including LISTENED, whose
                    // accumulatedMs is the Long.MAX_VALUE sentinel — must reset to 0. Feeding
                    // that sentinel through estimatedListenedMs() here used to overflow into a
                    // deeply negative number, which coerced the progress fraction to a
                    // permanent 0 for the rest of the replay (looked like the handle jumping
                    // to the far left and never moving again).
                    accumulatedMs = if (prev?.state == State.PAUSED) prev.accumulatedMs else 0L,
                )
            WIRE_PAUSED ->
                peerStates[uuid] = PeerState(
                    State.PAUSED,
                    listeningSinceMs = 0L,
                    // Same reasoning: only meaningful when pausing FROM an active listen.
                    accumulatedMs =
                        if (prev?.state == State.LISTENING) estimatedListenedMs(uuid, now)
                        else 0L,
                )
            WIRE_LISTENED ->
                peerStates[uuid] = PeerState(
                    State.LISTENED,
                    listeningSinceMs = 0L,
                    accumulatedMs = Long.MAX_VALUE, // renders as full regardless of duration
                )
        }
    }

    /** Extrapolation overran the duration with no "listened" confirmation — we genuinely
     * don't know where the peer is. Frozen at full, desaturated (see UI). */
    fun markUnknown(uuid: String) {
        val prev = peerStates[uuid] ?: return
        if (prev.state == State.LISTENING) {
            peerStates[uuid] = PeerState(State.UNKNOWN, 0L, prev.accumulatedMs)
        }
    }

    fun markLocallyListened(uuid: String) {
        localListened[uuid] = true
    }
}
