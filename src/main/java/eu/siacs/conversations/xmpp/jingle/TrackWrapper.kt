package eu.siacs.conversations.xmpp.jingle

import android.util.Log
import com.google.common.base.CaseFormat
import com.google.common.base.Optional
import com.google.common.base.Preconditions
import eu.siacs.conversations.Config
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RtpSender
import org.webrtc.RtpTransceiver
import java.util.UUID

internal class TrackWrapper<T : MediaStreamTrack>(val track: T, val rtpSender: RtpSender) {

    init {
        Preconditions.checkNotNull(track)
        Preconditions.checkNotNull(rtpSender)
    }

    companion object {
        @JvmStatic
        fun <T : MediaStreamTrack> addTrack(
            peerConnection: PeerConnection,
            mediaStreamTrack: T
        ): TrackWrapper<T> {
            val rtpSender: RtpSender = peerConnection.addTrack(mediaStreamTrack)
            return TrackWrapper(mediaStreamTrack, rtpSender)
        }

        @JvmStatic
        fun <T : MediaStreamTrack> get(
            peerConnection: PeerConnection?,
            trackWrapper: TrackWrapper<T>?
        ): Optional<T> {
            if (trackWrapper == null) {
                return Optional.absent()
            }
            val transceiver: RtpTransceiver? =
                if (peerConnection == null) null else getTransceiver(peerConnection, trackWrapper)
            if (transceiver == null) {
                val id: String
                try {
                    id = trackWrapper.rtpSender.id()
                } catch (e: IllegalStateException) {
                    return Optional.absent()
                }
                Log.w(Config.LOGTAG, "unable to detect transceiver for $id")
                return Optional.of(trackWrapper.track)
            }
            val direction = transceiver.direction
            return if (direction == RtpTransceiver.RtpTransceiverDirection.SEND_ONLY ||
                direction == RtpTransceiver.RtpTransceiverDirection.SEND_RECV
            ) {
                Optional.of(trackWrapper.track)
            } else {
                Log.d(Config.LOGTAG, "withholding track because transceiver is $direction")
                Optional.absent()
            }
        }

        @JvmStatic
        fun <T : MediaStreamTrack> getTransceiver(
            peerConnection: PeerConnection,
            trackWrapper: TrackWrapper<T>
        ): RtpTransceiver? {
            val rtpSender = trackWrapper.rtpSender
            val rtpSenderId: String
            try {
                rtpSenderId = rtpSender.id()
            } catch (e: IllegalStateException) {
                return null
            }
            for (transceiver in peerConnection.transceivers) {
                if (transceiver.sender.id() == rtpSenderId) {
                    return transceiver
                }
            }
            return null
        }

        @JvmStatic
        fun id(clazz: Class<out MediaStreamTrack>): String {
            return String.format(
                "%s-%s",
                CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, clazz.simpleName),
                UUID.randomUUID().toString()
            )
        }
    }
}
