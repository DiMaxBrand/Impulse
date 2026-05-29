package eu.siacs.conversations.xmpp.manager

import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import androidx.annotation.Nullable
import com.google.common.base.Objects
import com.google.common.base.Optional
import com.google.common.base.Preconditions
import com.google.common.base.Strings
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.Collections2
import com.google.common.collect.ComparisonChain
import com.google.common.collect.ImmutableSet
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.entities.Contact
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.entities.RtpSessionStatus
import eu.siacs.conversations.entities.Transferable
import eu.siacs.conversations.services.CallIntegration
import eu.siacs.conversations.services.CallIntegrationConnectionService
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.utils.CryptoHelper
import eu.siacs.conversations.utils.NetworkManager
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import eu.siacs.conversations.xmpp.jingle.AbstractJingleConnection
import eu.siacs.conversations.xmpp.jingle.JingleFileTransferConnection
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection
import eu.siacs.conversations.xmpp.jingle.Media
import eu.siacs.conversations.xmpp.jingle.OngoingRtpSession
import eu.siacs.conversations.xmpp.jingle.RtpEndUserState
import eu.siacs.conversations.xmpp.jingle.stanzas.Content
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericDescription
import eu.siacs.conversations.xmpp.jingle.stanzas.RtpDescription
import eu.siacs.conversations.xmpp.jingle.transports.InbandBytestreamsTransport
import eu.siacs.conversations.xmpp.jingle.transports.Transport
import im.conversations.android.xmpp.IqProcessingException
import im.conversations.android.xmpp.model.error.Condition
import im.conversations.android.xmpp.model.ibb.InBandByteStream
import im.conversations.android.xmpp.model.jingle.Jingle
import im.conversations.android.xmpp.model.jingle.Reason
import im.conversations.android.xmpp.model.jingle.error.JingleCondition
import im.conversations.android.xmpp.model.jmi.Accept
import im.conversations.android.xmpp.model.jmi.JingleMessage
import im.conversations.android.xmpp.model.jmi.Proceed
import im.conversations.android.xmpp.model.jmi.Propose
import im.conversations.android.xmpp.model.jmi.Reject
import im.conversations.android.xmpp.model.jmi.Ringing
import im.conversations.android.xmpp.model.stanza.Iq
import java.lang.ref.WeakReference
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class JingleManager(
    private val service: XmppConnectionService,
    connection: XmppConnection
) : AbstractManager(service.applicationContext, connection) {

    private val rtpSessionProposals = HashMap<RtpSessionProposal, DeviceDiscoveryState>()
    private val connections =
        ConcurrentHashMap<AbstractJingleConnection.Id, AbstractJingleConnection>()

    private val terminatedSessions: Cache<PersistableSessionId, TerminatedRtpSession> =
        CacheBuilder.newBuilder().expireAfterWrite(24, TimeUnit.HOURS).build()

    fun process(packet: Iq) {
        val jingle = packet.getExtension(Jingle::class.java)
        Preconditions.checkNotNull(
            jingle, "Passed iq packet w/o jingle extension to Connection Manager"
        )
        val sessionId: String? = jingle.sessionId
        val action: Jingle.Action? = jingle.action
        if (sessionId == null) {
            this.sendErrorFor(packet, JingleCondition.UnknownSession())
            return
        }
        if (action == null) {
            this.connection.sendErrorFor(packet, Condition.BadRequest())
            return
        }
        val id = AbstractJingleConnection.Id.of(account, packet, jingle)
        val existingJingleConnection = connections[id]
        if (existingJingleConnection != null) {
            existingJingleConnection.deliverPacket(packet)
        } else if (action == Jingle.Action.SESSION_INITIATE) {
            val from: Jid = packet.from
            val content: Content? = jingle.jingleContent
            val descriptionNamespace: String? = content?.descriptionNamespace
            val connection: AbstractJingleConnection
            if (Namespace.JINGLE_APPS_FILE_TRANSFER == descriptionNamespace) {
                connection = JingleFileTransferConnection(this.service, id, from)
            } else if (Namespace.JINGLE_APPS_RTP == descriptionNamespace && isUsingClearNet()) {
                val sessionEnded =
                    this.terminatedSessions.asMap().containsKey(PersistableSessionId.of(id))
                val stranger = isWithStrangerAndStrangerNotificationsAreOff(id.with)
                val busy = isBusy(this.service.accounts)
                if (busy || sessionEnded || stranger) {
                    Log.d(
                        Config.LOGTAG,
                        "${id.account.jid.asBareJid()}: rejected session with ${id.with} because busy. sessionEnded=$sessionEnded, stranger=$stranger"
                    )
                    sendSessionTerminate(packet, id)
                    if (busy || stranger) {
                        writeLogMissedIncoming(
                            id.with, id.sessionId, null, System.currentTimeMillis(), stranger
                        )
                    }
                    return
                }
                connection = JingleRtpConnection(this.service, id, from)
            } else {
                this.sendErrorFor(packet, JingleCondition.UnsupportedInfo())
                return
            }
            connections[id] = connection
            this.service.updateConversationUi()
            connection.deliverPacket(packet)
            if (connection is JingleRtpConnection) {
                addNewIncomingCall(connection)
            }
        } else {
            Log.d(Config.LOGTAG, "unable to route jingle packet: $packet")
            this.sendErrorFor(packet, JingleCondition.UnknownSession())
        }
    }

    fun deliverMessage(
        packet: im.conversations.android.xmpp.model.stanza.Message,
        timestamp: Long
    ) {
        val account = account
        val message = packet.getExtension(JingleMessage::class.java)
        val from = packet.from
        val to = packet.to
        val sessionId = message.sessionId
        if (sessionId == null) {
            return
        }
        val serverMsgId = getManager(StanzaIdManager::class.java).get(packet)
        if (message is Accept) {
            for (connection in connections.values) {
                if (connection is JingleRtpConnection) {
                    val id: AbstractJingleConnection.Id = connection.id
                    if (id.sessionId == sessionId) {
                        connection.deliveryMessage(from, message, serverMsgId, timestamp)
                        return
                    }
                }
            }
            return
        }
        val fromSelf = from.asBareJid() == account.jid.asBareJid()
        val id: AbstractJingleConnection.Id
        if (fromSelf) {
            if (to != null && to.isFullJid) {
                id = AbstractJingleConnection.Id.of(account, to, sessionId)
            } else {
                return
            }
        } else {
            id = AbstractJingleConnection.Id.of(account, from, sessionId)
        }
        val existingJingleConnection: AbstractJingleConnection? = connections[id]
        if (existingJingleConnection != null) {
            if (existingJingleConnection is JingleRtpConnection) {
                existingJingleConnection.deliveryMessage(from, message, serverMsgId, timestamp)
            } else {
                Log.d(
                    Config.LOGTAG,
                    "${account.jid.asBareJid()}: ${existingJingleConnection.javaClass.name} does not support jingle messages"
                )
            }
            return
        }

        if (fromSelf) {
            this.processFromSelf(message, id, serverMsgId, timestamp)
            return
        }
        this.process(packet, id, serverMsgId, timestamp)
    }

    private fun process(
        packet: im.conversations.android.xmpp.model.stanza.Message,
        id: AbstractJingleConnection.Id,
        serverMsgId: String?,
        timestamp: Long
    ) {
        val account = account
        val from = packet.from
        val to = packet.to
        val addressedDirectly = to != null && to == account.jid
        val message = packet.getExtension(JingleMessage::class.java)
        if (message is Propose) {
            processPropose(id, from, message, serverMsgId, timestamp)
        } else if (addressedDirectly && message is Proceed) {
            synchronized(rtpSessionProposals) {
                processProceed(packet, id, serverMsgId, timestamp, message)
            }
        } else if (addressedDirectly && message is Reject) {
            val proposal = getRtpSessionProposal(from.asBareJid(), id.sessionId)
            synchronized(rtpSessionProposals) {
                if (proposal != null) {
                    setTerminalSessionState(proposal, RtpEndUserState.DECLINED_OR_BUSY)
                    rtpSessionProposals.remove(proposal)
                    proposal.callIntegration.busy()
                    writeLogMissedOutgoing(
                        proposal.with, proposal.sessionId, serverMsgId, timestamp
                    )
                    this.service.notifyJingleRtpConnectionUpdate(
                        account,
                        proposal.with,
                        proposal.sessionId,
                        RtpEndUserState.DECLINED_OR_BUSY
                    )
                } else {
                    Log.d(
                        Config.LOGTAG,
                        "${account.jid.asBareJid()}: no rtp session proposal found for $from to deliver reject"
                    )
                }
            }
        } else if (addressedDirectly && message is Ringing) {
            Log.d(Config.LOGTAG, account.jid.asBareJid().toString() + ": " + from + " started ringing")
            updateProposedSessionDiscovered(from, id.sessionId, DeviceDiscoveryState.DISCOVERED)
        } else {
            Log.d(
                Config.LOGTAG,
                "${account.jid}: received out of order jingle message from=$from, message=$message, addressedDirectly=$addressedDirectly"
            )
        }
    }

    private fun processProceed(
        packet: im.conversations.android.xmpp.model.stanza.Message,
        id: AbstractJingleConnection.Id,
        serverMsgId: String?,
        timestamp: Long,
        proceed: Proceed
    ) {
        val from = packet.from
        val account = account
        val proposal = getRtpSessionProposal(from.asBareJid(), id.sessionId)
        if (proposal != null) {
            rtpSessionProposals.remove(proposal)
            val rtpConnection =
                JingleRtpConnection(
                    this.service, id, account.jid, proposal.callIntegration
                )
            rtpConnection.setProposedMedia(proposal.media)
            this.connections[id] = rtpConnection
            rtpConnection.transitionOrThrow(AbstractJingleConnection.State.PROPOSED)
            rtpConnection.deliveryMessage(from, proceed, serverMsgId, timestamp)
        } else {
            Log.d(
                Config.LOGTAG,
                "${account.jid.asBareJid()}: no rtp session (${id.sessionId}) proposal found for $from to deliver proceed"
            )
            this.connection.sendErrorFor(packet, Condition.ItemNotFound())
        }
    }

    private fun processPropose(
        id: AbstractJingleConnection.Id,
        from: Jid,
        propose: Propose,
        serverMsgId: String?,
        timestamp: Long
    ) {
        val account = account
        val descriptions: List<GenericDescription> = propose.descriptions
        val rtpDescriptions: Collection<RtpDescription> =
            Collections2.transform(
                Collections2.filter(descriptions) { d -> d is RtpDescription },
                { input -> input as RtpDescription }
            )
        if (!rtpDescriptions.isEmpty()
            && rtpDescriptions.size == descriptions.size
            && isUsingClearNet()
        ) {
            val media: Collection<Media> =
                Collections2.transform(rtpDescriptions, RtpDescription::getMedia)
            if (media.contains(Media.UNKNOWN)) {
                Log.d(
                    Config.LOGTAG,
                    "${account.jid.asBareJid()}: encountered unknown media in session proposal. $propose"
                )
                return
            }
            val matchingSessionProposal =
                findMatchingSessionProposal(id.with, ImmutableSet.copyOf(media))
            if (matchingSessionProposal.isPresent) {
                val ourSessionId = matchingSessionProposal.get().sessionId
                val theirSessionId = id.sessionId
                if (ComparisonChain.start()
                        .compare(ourSessionId, theirSessionId)
                        .compare(account.jid.toString(), id.with.toString())
                        .result() > 0
                ) {
                    Log.d(
                        Config.LOGTAG,
                        "${account.jid.asBareJid()}: our session lost tie break. automatically accepting their session. winning Session=$theirSessionId"
                    )
                    retractSessionProposal(matchingSessionProposal.get())
                    val rtpConnection = JingleRtpConnection(this.service, id, from)
                    this.connections[id] = rtpConnection
                    rtpConnection.setProposedMedia(ImmutableSet.copyOf(media))
                    rtpConnection.deliveryMessage(from, propose, serverMsgId, timestamp)
                    addNewIncomingCall(rtpConnection)
                } else {
                    Log.d(
                        Config.LOGTAG,
                        "${account.jid.asBareJid()}: our session won tie break. waiting for other party to accept. winningSession=$ourSessionId"
                    )
                }
                return
            }
            val stranger = isWithStrangerAndStrangerNotificationsAreOff(id.with)
            if (isBusy(service.accounts) || stranger) {
                writeLogMissedIncoming(
                    id.with.asBareJid(), id.sessionId, serverMsgId, timestamp, stranger
                )
                if (stranger) {
                    Log.d(
                        Config.LOGTAG,
                        "${id.account.jid.asBareJid()}: ignoring call proposal from stranger ${id.with}"
                    )
                    return
                }
                val activeDevices = account.activeDevicesWithRtpCapability()
                Log.d(Config.LOGTAG, "active devices with rtp capability: $activeDevices")
                if (activeDevices == 0) {
                    getManager(JingleMessageManager::class.java).reject(from, id.sessionId)
                } else {
                    Log.d(
                        Config.LOGTAG,
                        "${id.account.jid.asBareJid()}: ignoring proposal because busy on this device but there are other devices"
                    )
                }
            } else {
                val rtpConnection = JingleRtpConnection(this.service, id, from)
                this.connections[id] = rtpConnection
                rtpConnection.setProposedMedia(ImmutableSet.copyOf(media))
                rtpConnection.deliveryMessage(from, propose, serverMsgId, timestamp)
                addNewIncomingCall(rtpConnection)
            }
        } else {
            Log.d(
                Config.LOGTAG,
                "${account.jid.asBareJid()}: unable to react to proposed session with ${rtpDescriptions.size} rtp descriptions of ${descriptions.size} total descriptions"
            )
        }
    }

    private fun processFromSelf(
        message: JingleMessage,
        id: AbstractJingleConnection.Id,
        serverMsgId: String?,
        timestamp: Long
    ) {
        if (message is Proceed) {
            val c =
                this.service.findOrCreateConversation(account, id.with, false, false)
            val previousBusy = c.findRtpSession(id.sessionId, Message.STATUS_RECEIVED)
            if (previousBusy != null) {
                previousBusy.setBody(RtpSessionStatus(true, 0).toString())
                if (serverMsgId != null) {
                    previousBusy.setServerMsgId(serverMsgId)
                }
                previousBusy.setTime(timestamp)
                this.service.updateMessage(previousBusy, true)
                Log.d(
                    Config.LOGTAG,
                    "${id.account.jid.asBareJid()}: updated previous busy because call got picked up by another device"
                )
                this.service.notificationService.clearMissedCall(previousBusy)
                return
            }
        }
        Log.d(
            Config.LOGTAG,
            account.jid.asBareJid().toString() + ": ignore jingle message from self"
        )
    }

    private fun addNewIncomingCall(rtpConnection: JingleRtpConnection) {
        if (rtpConnection.isTerminated) {
            Log.d(
                Config.LOGTAG,
                "skip call integration because something must have gone during initiate"
            )
            return
        }
        if (CallIntegrationConnectionService.addNewIncomingCall(context, rtpConnection.id)) {
            return
        }
        rtpConnection.integrationFailure()
    }

    private fun sendSessionTerminate(request: Iq, id: AbstractJingleConnection.Id) {
        this.connection.sendResultFor(request)
        val iq = Iq(Iq.Type.SET)
        iq.setTo(id.with)
        val sessionTermination =
            iq.addExtension(Jingle(Jingle.Action.SESSION_TERMINATE, id.sessionId))
        sessionTermination.setReason(Reason.Busy(), null)
        this.connection.sendIqPacket(iq)
    }

    private fun isUsingClearNet(): Boolean {
        val appSettings = AppSettings(context)
        val account = account
        return !account.isOnion && !appSettings.isUseTor
    }

    private fun isBusy(): Boolean {
        for (connection in this.connections.values) {
            if (connection is JingleRtpConnection) {
                if (connection.isTerminated && connection.getCallIntegration().isDestroyed) {
                    continue
                }
                return true
            }
        }
        synchronized(this.rtpSessionProposals) {
            return this.rtpSessionProposals.containsValue(DeviceDiscoveryState.DISCOVERED)
                    || this.rtpSessionProposals.containsValue(DeviceDiscoveryState.SEARCHING)
                    || this.rtpSessionProposals.containsValue(
                DeviceDiscoveryState.SEARCHING_ACKNOWLEDGED
            )
        }
    }

    fun hasJingleRtpConnection(): Boolean {
        for (connection in this.connections.values) {
            if (connection is JingleRtpConnection) {
                if (connection.isTerminated) {
                    continue
                }
                return true
            }
        }
        return false
    }

    private fun findMatchingSessionProposal(
        with: Jid,
        media: kotlin.collections.Set<Media>
    ): Optional<RtpSessionProposal> {
        synchronized(this.rtpSessionProposals) {
            for (entry in this.rtpSessionProposals.entries) {
                val proposal: RtpSessionProposal = entry.key
                val state: DeviceDiscoveryState = entry.value
                val openProposal =
                    state == DeviceDiscoveryState.DISCOVERED
                            || state == DeviceDiscoveryState.SEARCHING
                            || state == DeviceDiscoveryState.SEARCHING_ACKNOWLEDGED
                if (openProposal
                    && proposal.with == with.asBareJid()
                    && proposal.media == media
                ) {
                    return Optional.of(proposal)
                }
            }
        }
        return Optional.absent()
    }

    private fun hasMatchingRtpSession(with: Jid, media: kotlin.collections.Set<Media>): Boolean {
        for (connection in this.connections.values) {
            if (connection is JingleRtpConnection) {
                if (connection.isTerminated) {
                    continue
                }
                if (connection.id.with.asBareJid() == with.asBareJid()
                    && connection.getMedia() == media
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun isWithStrangerAndStrangerNotificationsAreOff(with: Jid): Boolean {
        val notifyForStrangers = AppSettings(context).isNotificationsFromStrangers
        if (notifyForStrangers) {
            return false
        }
        val contact: Contact = getManager(RosterManager::class.java).getContact(with)
        return !contact.showInContactList()
    }

    fun sendErrorFor(request: Iq, jingleCondition: JingleCondition) {
        val condition =
            Condition.asInstance(JingleCondition.getErrorCondition(jingleCondition))
        this.connection.sendErrorFor(request, condition, jingleCondition)
    }

    private fun getRtpSessionProposal(from: Jid, sessionId: String): RtpSessionProposal? {
        for (rtpSessionProposal in rtpSessionProposals.keys) {
            if (rtpSessionProposal.sessionId == sessionId
                && rtpSessionProposal.with == from
            ) {
                return rtpSessionProposal
            }
        }
        return null
    }

    private fun writeLogMissedOutgoing(
        with: Jid,
        sessionId: String,
        serverMsgId: String?,
        timestamp: Long
    ) {
        val conversation =
            this.service.findOrCreateConversation(account, with.asBareJid(), false, false)
        val message =
            Message(conversation, Message.STATUS_SEND, Message.TYPE_RTP_SESSION, sessionId)
        message.setBody(RtpSessionStatus(false, 0).toString())
        message.setServerMsgId(serverMsgId)
        message.setTime(timestamp)
        writeMessage(message)
    }

    private fun writeLogMissedIncoming(
        with: Jid,
        sessionId: String,
        serverMsgId: String?,
        timestamp: Long,
        stranger: Boolean
    ) {
        val conversation =
            this.service.findOrCreateConversation(account, with.asBareJid(), false, false)
        val message =
            Message(
                conversation, Message.STATUS_RECEIVED, Message.TYPE_RTP_SESSION, sessionId
            )
        message.setBody(RtpSessionStatus(false, 0).toString())
        message.setServerMsgId(serverMsgId)
        message.setTime(timestamp)
        message.setCounterpart(with)
        writeMessage(message)
        if (stranger) {
            return
        }
        this.service.notificationService.pushMissedCallNow(message)
    }

    private fun writeMessage(message: Message) {
        val conversational = message.conversation
        if (conversational is Conversation) {
            conversational.add(message)
            this.database.createMessage(message)
            this.service.updateConversationUi()
        } else {
            throw IllegalStateException("Somehow the conversation in a message was a stub")
        }
    }

    fun startJingleFileTransfer(message: Message) {
        Preconditions.checkArgument(
            message.isFileOrImage, "Message is not of type file or image"
        )
        val old: Transferable? = message.transferable
        old?.cancel()
        val connection = JingleFileTransferConnection(this.service, message)
        this.connections[connection.id] = connection
        connection.sendSessionInitialize()
    }

    fun getOngoingRtpConnection(contact: Contact): Optional<OngoingRtpSession> {
        for (entry in this.connections.entries) {
            if (entry.value is JingleRtpConnection) {
                val jingleRtpConnection = entry.value as JingleRtpConnection
                val id: AbstractJingleConnection.Id = entry.key
                if (id.account === contact.getAccount()
                    && id.with.asBareJid() == contact.getAddress().asBareJid()
                ) {
                    return Optional.of(jingleRtpConnection)
                }
            }
        }
        synchronized(this.rtpSessionProposals) {
            for (entry in this.rtpSessionProposals.entries) {
                val proposal: RtpSessionProposal = entry.key
                if (contact.getAddress().asBareJid() == proposal.with) {
                    val preexistingState: DeviceDiscoveryState? = entry.value
                    if (preexistingState != null
                        && preexistingState != DeviceDiscoveryState.FAILED
                    ) {
                        return Optional.of(proposal)
                    }
                }
            }
        }
        return Optional.absent()
    }

    fun getOngoingRtpConnection(): JingleRtpConnection? {
        for (jingleConnection in this.connections.values) {
            if (jingleConnection is JingleRtpConnection) {
                if (jingleConnection.isTerminated) {
                    continue
                }
                return jingleConnection
            }
        }
        return null
    }

    fun finishConnectionOrThrow(connection: AbstractJingleConnection) {
        val id: AbstractJingleConnection.Id = connection.id
        if (this.connections.remove(id) == null) {
            throw IllegalStateException(
                String.format("Unable to finish connection with id=%s", id)
            )
        }
        this.service.updateConversationUi()
    }

    fun fireJingleRtpConnectionStateUpdates(): Boolean {
        for (connection in this.connections.values) {
            if (connection is JingleRtpConnection) {
                if (connection.isTerminated) {
                    continue
                }
                connection.fireStateUpdate()
                return true
            }
        }
        return false
    }

    fun retractSessionProposal(with: Jid) {
        synchronized(this.rtpSessionProposals) {
            var matchingProposal: RtpSessionProposal? = null
            for (proposal in this.rtpSessionProposals.keys) {
                if (with.asBareJid() == proposal.with) {
                    matchingProposal = proposal
                    break
                }
            }
            if (matchingProposal != null) {
                retractSessionProposal(matchingProposal, false)
            }
        }
    }

    private fun retractSessionProposal(rtpSessionProposal: RtpSessionProposal) {
        retractSessionProposal(rtpSessionProposal, true)
    }

    private fun retractSessionProposal(
        rtpSessionProposal: RtpSessionProposal,
        refresh: Boolean
    ) {
        val account = account
        Log.d(
            Config.LOGTAG,
            "${account.jid.asBareJid()}: retracting rtp session proposal with ${rtpSessionProposal.with}"
        )
        this.rtpSessionProposals.remove(rtpSessionProposal)
        rtpSessionProposal.callIntegration.retracted()
        if (refresh) {
            this.service.notifyJingleRtpConnectionUpdate(
                account,
                rtpSessionProposal.with,
                rtpSessionProposal.sessionId,
                RtpEndUserState.RETRACTED
            )
        }
        writeLogMissedOutgoing(
            rtpSessionProposal.with,
            rtpSessionProposal.sessionId,
            null,
            System.currentTimeMillis()
        )
        getManager(JingleMessageManager::class.java)
            .retract(rtpSessionProposal.with, rtpSessionProposal.sessionId)
    }

    fun initializeRtpSession(with: Jid, media: kotlin.collections.Set<Media>): JingleRtpConnection {
        val account = account
        val id: AbstractJingleConnection.Id = AbstractJingleConnection.Id.of(account, with)
        val rtpConnection =
            JingleRtpConnection(this.service, id, account.jid)
        rtpConnection.setProposedMedia(media)
        rtpConnection.getCallIntegration().startAudioRouting()
        this.connections[id] = rtpConnection
        rtpConnection.sendSessionInitiate()
        return rtpConnection
    }

    @Nullable
    fun proposeJingleRtpSession(
        with: Jid,
        media: kotlin.collections.Set<Media>
    ): RtpSessionProposal? {
        val account = account
        synchronized(this.rtpSessionProposals) {
            for (entry in this.rtpSessionProposals.entries) {
                val proposal: RtpSessionProposal = entry.key
                if (with.asBareJid() == proposal.with) {
                    val preexistingState: DeviceDiscoveryState? = entry.value
                    if (preexistingState != null
                        && preexistingState != DeviceDiscoveryState.FAILED
                    ) {
                        val endUserState = preexistingState.toEndUserState()
                        this.service.notifyJingleRtpConnectionUpdate(
                            account, with, proposal.sessionId, endUserState
                        )
                        return proposal
                    }
                }
            }
            if (isBusy(this.service.accounts)) {
                if (hasMatchingRtpSession(with, media)) {
                    Log.d(
                        Config.LOGTAG,
                        "ignoring request to propose jingle session because the other party"
                                + " already created one for us"
                    )
                    return null
                }
                throw IllegalStateException(
                    "There is already a running RTP session. This should have been caught by the UI"
                )
            }
            val callIntegration = CallIntegration(context)
            callIntegration.setVideoState(
                if (Media.audioOnly(media))
                    VideoProfile.STATE_AUDIO_ONLY
                else
                    VideoProfile.STATE_BIDIRECTIONAL
            )
            callIntegration.setAddress(
                CallIntegration.address(with.asBareJid()), TelecomManager.PRESENTATION_ALLOWED
            )
            val contact = account.roster.getContact(with)
            callIntegration.setCallerDisplayName(
                contact.displayName, TelecomManager.PRESENTATION_ALLOWED
            )
            callIntegration.setInitialized()
            callIntegration.setInitialAudioDevice(CallIntegration.initialAudioDevice(media))
            callIntegration.startAudioRouting()
            val proposal = RtpSessionProposal.of(with.asBareJid(), media, callIntegration)
            callIntegration.setCallback(ProposalStateCallback(proposal))
            this.rtpSessionProposals[proposal] = DeviceDiscoveryState.SEARCHING
            this.service.notifyJingleRtpConnectionUpdate(
                account, proposal.with, proposal.sessionId, RtpEndUserState.FINDING_DEVICE
            )
            val triggerTimeout =
                Config.JINGLE_MESSAGE_INIT_STRICT_DEVICE_TIMEOUT
                        || contact.mutualPresenceSubscription()
            SCHEDULED_EXECUTOR_SERVICE.schedule(
                {
                    val currentProposalState = rtpSessionProposals[proposal]
                    Log.d(Config.LOGTAG, "proposal state after timeout $currentProposalState")
                    if (triggerTimeout
                        && Arrays.asList(
                            DeviceDiscoveryState.SEARCHING,
                            DeviceDiscoveryState.SEARCHING_ACKNOWLEDGED
                        ).contains(currentProposalState)
                    ) {
                        deviceDiscoveryTimeout(account, proposal)
                    }
                },
                Config.DEVICE_DISCOVERY_TIMEOUT.toLong(),
                TimeUnit.MILLISECONDS
            )
            this.sendSessionProposal(proposal)
            return proposal
        }
    }

    private fun deviceDiscoveryTimeout(account: Account, proposal: RtpSessionProposal) {
        val endUserState: RtpEndUserState
        if (NetworkManager(this.context).getHint() == NetworkManager.Hint.ACTIVE) {
            endUserState = RtpEndUserState.CONNECTIVITY_ERROR
        } else {
            endUserState = RtpEndUserState.NO_INTERNET
        }
        Log.d(Config.LOGTAG, "call proposal still in device discovery state after timeout")
        setTerminalSessionState(proposal, endUserState)

        rtpSessionProposals.remove(proposal)
        proposal.callIntegration.error()
        writeLogMissedOutgoing(proposal.with, proposal.sessionId, null, System.currentTimeMillis())
        this.service.notifyJingleRtpConnectionUpdate(
            account, proposal.with, proposal.sessionId, endUserState
        )
        getManager(JingleMessageManager::class.java).retract(proposal.with, proposal.sessionId)
    }

    fun matchingProposal(with: Jid): Optional<RtpSessionProposal> {
        synchronized(this.rtpSessionProposals) {
            for (entry in this.rtpSessionProposals.entries) {
                val proposal: RtpSessionProposal = entry.key
                if (with.asBareJid() == proposal.with) {
                    return Optional.of(proposal)
                }
            }
        }
        return Optional.absent()
    }

    fun hasMatchingProposal(with: Jid): Boolean {
        synchronized(this.rtpSessionProposals) {
            for (entry in this.rtpSessionProposals.entries) {
                val state = entry.value
                val proposal: RtpSessionProposal = entry.key
                if (with.asBareJid() == proposal.with) {
                    val endUserState = state.toEndUserState()
                    this.service.notifyJingleRtpConnectionUpdate(
                        account, proposal.with, proposal.sessionId, endUserState
                    )
                    return true
                }
            }
        }
        return false
    }

    fun deliverIbbPacket(packet: Iq) {
        val inbandByteStream = packet.getOnlyExtension(InBandByteStream::class.java)
        if (inbandByteStream == null) {
            this.connection.sendErrorFor(packet, Condition.BadRequest())
            return
        }
        val sid = inbandByteStream.sid
        if (Strings.isNullOrEmpty(sid)) {
            Log.d(
                Config.LOGTAG,
                "${account.jid.asBareJid()}: unable to deliver ibb packet. missing sid"
            )
            this.connection.sendErrorFor(packet, Condition.BadRequest())
            return
        }
        for (connection in this.connections.values) {
            if (connection is JingleFileTransferConnection) {
                val transport: Transport? = connection.transport
                if (transport is InbandBytestreamsTransport) {
                    if (sid == transport.streamId) {
                        try {
                            transport.deliverPacket(packet.from, inbandByteStream)
                            this.connection.sendResultFor(packet)
                        } catch (e: IqProcessingException) {
                            this.connection.sendErrorFor(packet, e)
                        }
                        return
                    }
                }
            }
        }
        Log.d(
            Config.LOGTAG,
            "${account.jid.asBareJid()}: unable to deliver ibb packet with sid=$sid"
        )
        connection.sendErrorFor(packet, Condition.ItemNotFound())
    }

    fun notifyRebound() {
        for (connection in this.connections.values) {
            connection.notifyRebound()
        }
        if (this.connection.features.sm()) {
            resendSessionProposals()
        }
    }

    fun findJingleRtpConnection(
        with: Jid,
        sessionId: String
    ): WeakReference<JingleRtpConnection>? {
        val id = AbstractJingleConnection.Id.of(account, with, sessionId)
        val connection = connections[id]
        if (connection is JingleRtpConnection) {
            return WeakReference(connection)
        }
        return null
    }

    private fun resendSessionProposals() {
        synchronized(this.rtpSessionProposals) {
            for (entry in this.rtpSessionProposals.entries) {
                val proposal: RtpSessionProposal = entry.key
                if (entry.value == DeviceDiscoveryState.SEARCHING) {
                    Log.d(
                        Config.LOGTAG,
                        "${account.jid.asBareJid()}: resending session proposal to ${proposal.with}"
                    )
                    this.sendSessionProposal(proposal)
                }
            }
        }
    }

    fun updateProposedSessionDiscovered(
        from: Jid,
        sessionId: String,
        target: DeviceDiscoveryState
    ) {
        synchronized(this.rtpSessionProposals) {
            val sessionProposal = getRtpSessionProposal(from.asBareJid(), sessionId)
            val currentState: DeviceDiscoveryState? =
                if (sessionProposal == null) null else rtpSessionProposals[sessionProposal]
            if (currentState == null) {
                Log.d(
                    Config.LOGTAG,
                    "unable to find session proposal for session id $sessionId target=$target"
                )
                return
            }
            if (currentState == DeviceDiscoveryState.DISCOVERED) {
                Log.d(
                    Config.LOGTAG,
                    "session proposal already at discovered. not going to fall back"
                )
                return
            }

            Log.d(
                Config.LOGTAG,
                "${account.jid.asBareJid()}: flagging session $sessionId as $target"
            )

            val endUserState = target.toEndUserState()

            if (target == DeviceDiscoveryState.FAILED) {
                Log.d(Config.LOGTAG, "removing session proposal after failure")
                setTerminalSessionState(sessionProposal!!, endUserState)
                this.rtpSessionProposals.remove(sessionProposal)
                sessionProposal.callIntegration.error()
                this.service.notifyJingleRtpConnectionUpdate(
                    account,
                    sessionProposal.with,
                    sessionProposal.sessionId,
                    endUserState
                )
                return
            }

            this.rtpSessionProposals[sessionProposal!!] = target

            if (endUserState == RtpEndUserState.RINGING) {
                sessionProposal.callIntegration.setDialing()
            }

            this.service.notifyJingleRtpConnectionUpdate(
                account, sessionProposal.with, sessionProposal.sessionId, endUserState
            )
        }
    }

    fun rejectRtpSession(sessionId: String) {
        for (connection in this.connections.values) {
            if (connection.id.sessionId == sessionId) {
                if (connection is JingleRtpConnection) {
                    try {
                        connection.rejectCall()
                        return
                    } catch (e: IllegalStateException) {
                        Log.w(
                            Config.LOGTAG,
                            "race condition on rejecting call from notification",
                            e
                        )
                    }
                }
            }
        }
    }

    fun endRtpSession(sessionId: String) {
        for (connection in this.connections.values) {
            if (connection.id.sessionId == sessionId) {
                if (connection is JingleRtpConnection) {
                    connection.endCall()
                }
            }
        }
    }

    fun failProceed(with: Jid, sessionId: String, message: String) {
        val id = AbstractJingleConnection.Id.of(account, with, sessionId)
        val existingJingleConnection = connections[id]
        if (existingJingleConnection is JingleRtpConnection) {
            existingJingleConnection.deliverFailedProceed(message)
        }
    }

    fun ensureConnectionIsRegistered(connection: AbstractJingleConnection) {
        if (connections.containsValue(connection)) {
            return
        }
        val e = IllegalStateException(
            "JingleConnection has not been registered with connection manager"
        )
        Log.e(Config.LOGTAG, "ensureConnectionIsRegistered() failed. Going to throw", e)
        throw e
    }

    fun setTerminalSessionState(
        id: AbstractJingleConnection.Id,
        state: RtpEndUserState,
        media: kotlin.collections.Set<Media>
    ) {
        this.terminatedSessions.put(
            PersistableSessionId.of(id), TerminatedRtpSession(state, media)
        )
    }

    fun setTerminalSessionState(proposal: RtpSessionProposal, state: RtpEndUserState) {
        this.terminatedSessions.put(
            PersistableSessionId.of(proposal), TerminatedRtpSession(state, proposal.media)
        )
    }

    fun getTerminalSessionState(with: Jid, sessionId: String): TerminatedRtpSession? {
        return this.terminatedSessions.getIfPresent(PersistableSessionId(with, sessionId))
    }

    private fun sendSessionProposal(proposal: RtpSessionProposal) {
        this.getManager(JingleMessageManager::class.java)
            .propose(proposal.with, proposal.sessionId, proposal.media)
    }

    private data class PersistableSessionId(val with: Jid, val sessionId: String) {
        companion object {
            @JvmStatic
            fun of(id: AbstractJingleConnection.Id): PersistableSessionId {
                return PersistableSessionId(id.with, id.sessionId)
            }

            @JvmStatic
            fun of(proposal: RtpSessionProposal): PersistableSessionId {
                return PersistableSessionId(proposal.with, proposal.sessionId)
            }
        }
    }

    @JvmRecord
    data class TerminatedRtpSession(val state: RtpEndUserState, val media: kotlin.collections.Set<Media>)

    enum class DeviceDiscoveryState {
        SEARCHING,
        SEARCHING_ACKNOWLEDGED,
        DISCOVERED,
        FAILED;

        fun toEndUserState(): RtpEndUserState {
            return when (this) {
                SEARCHING, SEARCHING_ACKNOWLEDGED -> RtpEndUserState.FINDING_DEVICE
                DISCOVERED -> RtpEndUserState.RINGING
                else -> RtpEndUserState.CONNECTIVITY_ERROR
            }
        }
    }

    class RtpSessionProposal private constructor(
        @JvmField val with: Jid,
        @JvmField val sessionId: String,
        @JvmField val media: kotlin.collections.Set<Media>,
        @JvmField val callIntegration: CallIntegration
    ) : OngoingRtpSession {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val proposal = other as RtpSessionProposal
            return Objects.equal(with, proposal.with)
                    && Objects.equal(sessionId, proposal.sessionId)
        }

        override fun hashCode(): Int {
            return Objects.hashCode(with, sessionId)
        }

        override fun getWith(): Jid = with

        override fun getSessionId(): String = sessionId

        override fun getCallIntegration(): CallIntegration = this.callIntegration

        override fun getMedia(): kotlin.collections.Set<Media> = this.media

        companion object {
            @JvmStatic
            fun of(
                with: Jid,
                media: kotlin.collections.Set<Media>,
                callIntegration: CallIntegration
            ): RtpSessionProposal {
                return RtpSessionProposal(with, CryptoHelper.random(16), media, callIntegration)
            }
        }
    }

    inner class ProposalStateCallback(private val proposal: RtpSessionProposal) :
        CallIntegration.Callback {

        override fun onCallIntegrationShowIncomingCallUi() {}

        override fun onCallIntegrationDisconnect() {
            Log.d(Config.LOGTAG, "a phone call has just been started. retracting proposal")
            retractSessionProposal(this.proposal)
        }

        override fun onAudioDeviceChanged(
            selectedAudioDevice: CallIntegration.AudioDevice,
            availableAudioDevices: kotlin.collections.Set<CallIntegration.AudioDevice>
        ) {
            service.notifyJingleRtpConnectionUpdate(selectedAudioDevice, availableAudioDevices)
        }

        override fun onCallIntegrationReject() {}

        override fun onCallIntegrationAnswer() {}

        override fun onCallIntegrationSilence() {}

        override fun onCallIntegrationMicrophoneEnabled(enabled: Boolean) {}
    }

    companion object {
        @JvmField
        val SCHEDULED_EXECUTOR_SERVICE: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor()

        @JvmStatic
        fun isBusy(accounts: Collection<Account>): Boolean {
            for (account in accounts) {
                val manager = account.xmppConnection.getManager(JingleManager::class.java)
                if (manager.isBusy()) {
                    return true
                }
            }
            return false
        }

        @JvmStatic
        fun schedule(
            runnable: Runnable,
            delay: Long,
            timeUnit: TimeUnit
        ): ScheduledFuture<*> {
            return SCHEDULED_EXECUTOR_SERVICE.schedule(runnable, delay, timeUnit)
        }
    }
}
