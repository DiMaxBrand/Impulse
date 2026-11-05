package eu.siacs.conversations.services

import android.content.Context
import android.net.Uri
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.VideoProfile
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallsManager
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Android 15+ uses transactional Telecom, avoiding the legacy endpoint callback race. */
@RequiresApi(35)
internal class TransactionalCallIntegration(
    private val context: Context,
    private val connection: CallIntegration,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val state = MutableStateFlow(Connection.STATE_INITIALIZING)
    private var started = false
    private var ended = false
    private var systemDisconnected = false
    private var answeredBySystem = false
    private var control: CallControlScope? = null
    @Volatile private var endpoints: List<CallEndpointCompat> = emptyList()
    @Volatile var selectedAudioDevice = CallIntegration.AudioDevice.NONE
        private set
    @Volatile var failed = false
        private set

    val audioDevices: Set<CallIntegration.AudioDevice>
        get() = endpoints.mapNotNull { audioDevice(it) }.toSet()

    fun onStateChanged(next: Int) {
        scope.launch {
            state.value = next
            if (next == Connection.STATE_DISCONNECTED) {
                ended = true
                if (!started) scope.cancel()
            } else if (!started && !ended &&
                next in listOf(Connection.STATE_DIALING, Connection.STATE_RINGING, Connection.STATE_ACTIVE)) {
                started = true
                addCall(next == Connection.STATE_RINGING)
            }
        }
    }

    private suspend fun addCall(incoming: Boolean) {
        try {
            val manager = CallsManager(context)
            manager.registerAppWithTelecom(CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING)
            val callType = if (VideoProfile.isVideo(connection.videoState))
                CallAttributesCompat.CALL_TYPE_VIDEO_CALL else CallAttributesCompat.CALL_TYPE_AUDIO_CALL
            if (!incoming) connection.onTransactionalCallStarted()
            manager.addCall(
                CallAttributesCompat(
                    connection.callerDisplayName ?: context.getString(R.string.app_name),
                    connection.address ?: Uri.parse("xmpp:unknown"),
                    if (incoming) CallAttributesCompat.DIRECTION_INCOMING else CallAttributesCompat.DIRECTION_OUTGOING,
                    callType,
                    // Jingle has no hold support. Telecom must disconnect rather than pretend to hold it.
                    callCapabilities = 0,
                ),
                onAnswer = {
                    answeredBySystem = true
                    connection.onAnswer()
                },
                onDisconnect = {
                    systemDisconnected = true
                    connection.onDisconnect()
                },
                onSetActive = { /* The Jingle session continues to carry media. */ },
                onSetInactive = { connection.onDisconnect() },
            ) {
                control = this
                launch {
                    currentCallEndpoint.collect {
                        selectedAudioDevice = audioDevice(it) ?: CallIntegration.AudioDevice.NONE
                        notifyRoutes()
                    }
                }
                launch {
                    availableEndpoints.collect {
                        endpoints = it.toList()
                        notifyRoutes()
                    }
                }
                launch { isMuted.collect { connection.onMuteStateChanged(it) } }
                launch {
                    var active = false
                    state.collect { current ->
                        if (current == Connection.STATE_ACTIVE && !active) {
                            val result = if (answeredBySystem) CallControlResult.Success()
                                else if (incoming) answer(callType) else setActive()
                            active = result is CallControlResult.Success
                            if (!active) {
                                Log.w(Config.LOGTAG, "Telecom could not activate call: $result")
                                connection.onDisconnect()
                            }
                        } else if (current == Connection.STATE_DISCONNECTED) {
                            if (!systemDisconnected) {
                                val reason = connection.disconnectCause?.code
                                val code = when (reason) {
                                    DisconnectCause.REJECTED, DisconnectCause.BUSY -> DisconnectCause.REJECTED
                                    DisconnectCause.MISSED -> DisconnectCause.MISSED
                                    DisconnectCause.REMOTE -> DisconnectCause.REMOTE
                                    else -> DisconnectCause.LOCAL
                                }
                                val result = withTimeoutOrNull(3000) { disconnect(DisconnectCause(code)) }
                                if (result !is CallControlResult.Success) {
                                    Log.w(Config.LOGTAG, "Telecom disconnect failed, retrying: $result")
                                    withTimeoutOrNull(3000) { disconnect(DisconnectCause(code)) }
                                }
                            }
                            this@TransactionalCallIntegration.scope.cancel()
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(Config.LOGTAG, "Transactional call integration unavailable", error)
            failed = true
            if (!ended && !systemDisconnected) connection.onTransactionalIntegrationFailed()
        } finally {
            control = null
            endpoints = emptyList()
            selectedAudioDevice = CallIntegration.AudioDevice.NONE
            if (systemDisconnected || ended) scope.cancel()
        }
    }

    fun selectAudioDevice(device: CallIntegration.AudioDevice) {
        scope.launch {
            val endpoint = endpoints.firstOrNull { audioDevice(it) == device }
            val result = try {
                if (endpoint != null) control?.requestEndpointChange(endpoint) else null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(Config.LOGTAG, "Call endpoint request failed", error)
                null
            }
            if (result !is CallControlResult.Success) {
                Log.w(Config.LOGTAG, "Could not switch call endpoint to $device: $result")
                Toast.makeText(context, R.string.call_audio_route_failed, Toast.LENGTH_SHORT).show()
            }
            // The current-endpoint flow confirms the actual route; don't optimistically change it.
        }
    }

    private fun notifyRoutes() = connection.onAudioDeviceChanged(selectedAudioDevice, audioDevices)

    private fun audioDevice(endpoint: CallEndpointCompat): CallIntegration.AudioDevice? = when (endpoint.type) {
        CallEndpointCompat.TYPE_BLUETOOTH -> CallIntegration.AudioDevice.BLUETOOTH
        CallEndpointCompat.TYPE_EARPIECE -> CallIntegration.AudioDevice.EARPIECE
        CallEndpointCompat.TYPE_SPEAKER -> CallIntegration.AudioDevice.SPEAKER_PHONE
        CallEndpointCompat.TYPE_WIRED_HEADSET -> CallIntegration.AudioDevice.WIRED_HEADSET
        CallEndpointCompat.TYPE_STREAMING -> CallIntegration.AudioDevice.STREAMING
        else -> null
    }
}
