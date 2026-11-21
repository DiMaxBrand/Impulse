package eu.siacs.conversations.xmpp.manager

import android.os.Handler
import android.os.Looper
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xml.Element
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.hints.NoStore
import im.conversations.android.xmpp.model.stanza.Message
import java.util.concurrent.ConcurrentHashMap

/**
 * Impulse's ephemeral rich activity layer. This is deliberately separate from XEP-0085 typing:
 * older clients ignore the extension, while all activity packets are explicitly non-archived.
 */
class ActivityStatusManager(private val service: XmppConnectionService, private val connection: XmppConnection) :
    AbstractManager(service.applicationContext, connection) {

    enum class Type(val wireName: String) {
        RECORDING_AUDIO("recording-audio"),
        RECORDING_VIDEO("recording-video"),
        CHOOSING_MEDIA("choosing-media"),
        CHOOSING_FILE("choosing-file"),
        CHOOSING_LOCATION("choosing-location"),
        CHOOSING_CONTACT("choosing-contact"),
        UPLOADING_AUDIO("uploading-audio"),
        UPLOADING_PHOTO("uploading-photo"),
        UPLOADING_VIDEO("uploading-video"),
        UPLOADING_FILE("uploading-file"),
        CLEAR("clear");

        companion object {
            fun fromWire(value: String?): Type? = entries.firstOrNull { it.wireName == value }
        }
    }

    data class Status(val type: Type, val progress: Int? = null)

    private data class TimedStatus(val status: Status, val expiresAt: Long)

    private val incoming = ConcurrentHashMap<Jid, TimedStatus>()
    private val appSettings = AppSettings(service.applicationContext)
    private val outgoing = ConcurrentHashMap<Jid, Status>()
    private val outgoingEpoch = ConcurrentHashMap<Jid, Long>()
    private val lastSentAt = ConcurrentHashMap<Jid, Long>()
    private val lastSentProgress = ConcurrentHashMap<Jid, Int>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun process(message: Message): Boolean {
        val element = message.findChild("activity", Namespace.IMPULSE_ACTIVITY_STATUS) ?: return false
        val from = message.from ?: return true
        val type = Type.fromWire(element.getAttribute("type")) ?: return true
        val address = from.asBareJid()
        if (type == Type.CLEAR) {
            incoming.remove(address)
        } else {
            val progress = element.getAttribute("progress")?.toIntOrNull()?.coerceIn(0, 100)
            incoming[address] = TimedStatus(Status(type, progress), android.os.SystemClock.elapsedRealtime() + EXPIRY_MS)
            expireLater(address)
        }
        service.updateConversationUi()
        return true
    }

    fun getIncoming(address: Jid): Status? {
        val timed = incoming[address.asBareJid()] ?: return null
        if (timed.expiresAt > android.os.SystemClock.elapsedRealtime()) return timed.status
        incoming.remove(address.asBareJid(), timed)
        return null
    }

    private fun expireLater(address: Jid) {
        mainHandler.postDelayed({
            val timed = incoming[address] ?: return@postDelayed
            if (timed.expiresAt <= android.os.SystemClock.elapsedRealtime()) {
                incoming.remove(address, timed)
                service.updateConversationUi()
            }
        }, EXPIRY_MS + 50)
    }

    companion object {
        private const val EXPIRY_MS = 12_000L
        private const val HEARTBEAT_MS = 8_000L

        @JvmStatic
        fun send(conversation: Conversation, type: Type, progress: Int? = null) {
            if (conversation.getMode() != Conversational.MODE_SINGLE || conversation.getContact().isSelf()) return
            val account = conversation.getAccount()
            val connection = account.getXmppConnection() ?: return
            val manager = connection.getManager(ActivityStatusManager::class.java) ?: return
            manager.send(conversation, type, progress)
        }
    }

    private fun send(conversation: Conversation, type: Type, progress: Int?) {
        // Rich activity is the same category of presence signal as typing. Respect the existing
        // “send chat states” privacy switch instead of silently adding a new outbound signal.
        if (!appSettings.isSendChatStates()) return
        val address = conversation.getAddress().asBareJid()
        val normalizedProgress = progress?.coerceIn(0, 100)
        val previous = outgoing[address]
        val typeChanged = previous?.type != type
        val epoch = if (typeChanged) (outgoingEpoch[address] ?: 0L) + 1L else outgoingEpoch[address] ?: 1L
        outgoingEpoch[address] = epoch
        if (type == Type.CLEAR) {
            outgoing.remove(address)
            lastSentProgress.remove(address)
        } else {
            outgoing[address] = Status(type, normalizedProgress)
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val lastProgress = lastSentProgress[address]
        val progressChangedEnough = normalizedProgress != null &&
            (lastProgress == null || kotlin.math.abs(normalizedProgress - lastProgress) >= PROGRESS_STEP)
        val needsTimeRefresh = now - (lastSentAt[address] ?: 0L) >= PROGRESS_MAX_SILENCE_MS
        val transmit = type == Type.CLEAR || typeChanged || !type.isUpload() || progressChangedEnough || needsTimeRefresh
        if (transmit) {
            sendPacket(address, type, normalizedProgress)
            lastSentAt[address] = now
            normalizedProgress?.let { lastSentProgress[address] = it }
        }
        if (typeChanged && type != Type.CLEAR) scheduleHeartbeat(address, epoch)
    }

    private fun scheduleHeartbeat(address: Jid, epoch: Long) {
        mainHandler.postDelayed({
            val current = outgoing[address] ?: return@postDelayed
            // A newer progress update or a clear supersedes this scheduled turn.
            if (outgoingEpoch[address] != epoch) return@postDelayed
            // A heartbeat lets a long recording or a slow upload outlive the receiver's expiry
            // without pretending that every frame of local work is progress data.
            sendPacket(address, current.type, current.progress)
            lastSentAt[address] = android.os.SystemClock.elapsedRealtime()
            current.progress?.let { lastSentProgress[address] = it }
            scheduleHeartbeat(address, epoch)
        }, HEARTBEAT_MS)
    }

    private fun sendPacket(address: Jid, type: Type, progress: Int?) {
        val packet = Message()
        packet.setType(Message.Type.CHAT)
        packet.setTo(address)
        val element = Element("activity", Namespace.IMPULSE_ACTIVITY_STATUS)
        element.setAttribute("type", type.wireName)
        progress?.coerceIn(0, 100)?.let { element.setAttribute("progress", it.toString()) }
        packet.addChild(element)
        packet.addExtension(NoStore())
        connection.sendMessagePacket(packet)
    }

    private fun Type.isUpload(): Boolean = when (this) {
        Type.UPLOADING_AUDIO, Type.UPLOADING_PHOTO, Type.UPLOADING_VIDEO, Type.UPLOADING_FILE -> true
        else -> false
    }
}
