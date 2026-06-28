package eu.siacs.conversations.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.media.MediaRecorder
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import eu.siacs.conversations.utils.Compatibility
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.utils.Emoticons
import eu.siacs.conversations.services.CallIntegrationConnectionService
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.ui.interfaces.OnConversationRead
import androidx.lifecycle.lifecycleScope
import eu.siacs.conversations.ui.util.Attachment
import eu.siacs.conversations.ui.util.PresenceSelector
import eu.siacs.conversations.ui.util.ViewUtil
import eu.siacs.conversations.xmpp.jingle.RtpCapability
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch

/**
 * Jetpack Compose replacement for [ConversationFragment]: the screen showing a single
 * conversation. Rendering lives in [ConversationScreen]; this fragment bridges the screen to
 * [XmppConnectionService].
 */
class ConversationComposeFragment : XmppFragment(), ConversationScreenListener {

    private val state = ConversationScreenState()
    private var conversation: Conversation? = null
    private var pendingConversationUuid: String? = null
    private val loadingMoreMessages = AtomicBoolean(false)
    private var pendingTrustAction: (() -> Unit)? = null
    private var lastComposedText = ""
    private var typingPauseJob: kotlinx.coroutines.Job? = null
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: java.io.File? = null
    private var recordingStartMs = 0L
    private var totalPausedMs = 0L
    private var pauseStartMs = 0L
    private var timerJob: kotlinx.coroutines.Job? = null
    private val pendingTakePhotoUri = eu.siacs.conversations.ui.util.PendingItem<Uri>()
    private var pendingSharedUris: List<Uri> = emptyList()
    private var pendingSharedMimeType: String? = null

    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                val uri = pendingTakePhotoUri.pop()
                if (uri != null) {
                    stageUris(listOf(uri), eu.siacs.conversations.ui.util.Attachment.Type.IMAGE)
                }
            } else {
                pendingTakePhotoUri.pop()
            }
        }

    private val pickMediaLauncher =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            val ctx = context ?: return@registerForActivityResult
            uris.forEach { uri ->
                val mime = ctx.contentResolver.getType(uri)
                val type = if (mime?.startsWith("video/") == true) Attachment.Type.FILE else Attachment.Type.IMAGE
                stageUris(listOf(uri), type)
            }
        }

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) stageUris(listOf(uri), Attachment.Type.FILE)
        }

    private val trustKeysLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                val action = pendingTrustAction
                pendingTrustAction = null
                action?.invoke()
            }
        }

    private val audioCallPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VOICE_CALL)
        }

    private val videoCallPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) {
                triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL)
            }
        }

    private val recordVoiceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                val data = result.data
                val uri = data?.data
                if (uri != null) {
                    val autoSend =
                        data.getBooleanExtra(RecordingActivity.EXTRA_AUTO_SEND_RECORDING, false)
                    if (autoSend) {
                        commitAttachments(
                            Attachment.of(requireContext(), uri, Attachment.Type.RECORDING)
                        )
                    } else {
                        stageUris(listOf(uri), Attachment.Type.RECORDING)
                    }
                }
            }
        }

    private val inviteLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK && result.data != null) {
                val invite = XmppActivity.ConferenceInvite.parse(result.data)
                if (invite != null && invite.execute(requireXmppActivity())) {
                    Toast.makeText(
                            requireContext(),
                            R.string.creating_conference,
                            Toast.LENGTH_LONG,
                        )
                        .show()
                }
            }
        }

    private val recordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startNativeRecording()
            }
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (pendingSharedUris.isNotEmpty()) {
            val uris = pendingSharedUris
            val mimeType = pendingSharedMimeType
            pendingSharedUris = emptyList()
            pendingSharedMimeType = null
            stageUris(uris, if (mimeType?.startsWith("image/") == true) Attachment.Type.IMAGE else Attachment.Type.FILE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val composeView = ComposeView(requireContext())
        ConversationScreenHelper.setup(composeView, state, this)
        return composeView
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            pendingConversationUuid = savedInstanceState.getString(STATE_CONVERSATION_UUID)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val uuid = conversation?.getUuid() ?: pendingConversationUuid
        if (uuid != null) outState.putString(STATE_CONVERSATION_UUID, uuid)
    }

    override fun onResume() {
        super.onResume()
        markRead()
        val activity = activity ?: return
        val color = com.google.android.material.elevation.SurfaceColors.SURFACE_2.getColor(activity)
        activity.window.statusBarColor = color
        val isLight = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) !=
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        androidx.core.view.WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars = isLight
    }

    override fun onStop() {
        super.onStop()
        if (state.recordingState.value is RecordingUiState.Active) {
            stopRecordingSession(save = false)
            state.recordingState.value = RecordingUiState.Idle
        }
        AudioPlaybackController.pauseForBackground()
    }

    fun reInit(conversation: Conversation, extras: Bundle) {
        if (this.conversation !== conversation) {
            state.replyingTo.value = null
            state.correcting.value = null
            state.setInput("")
        }
        this.conversation = conversation
        this.pendingConversationUuid = null
        conversation.messagesLoaded.set(true)
        val text = extras.getString(Intent.EXTRA_TEXT)
        if (!text.isNullOrBlank() &&
            !extras.getBoolean(ConversationsActivity.EXTRA_DO_NOT_APPEND, false)
        ) {
            state.setInput(text)
        }
        val sharedUris: List<Uri> = run {
            @Suppress("DEPRECATION")
            val list = extras.getParcelableArrayList<Uri>(Intent.EXTRA_STREAM)
            if (!list.isNullOrEmpty()) list
            else {
                @Suppress("DEPRECATION")
                listOfNotNull(extras.getParcelable<Uri>(Intent.EXTRA_STREAM))
            }
        }
        if (sharedUris.isNotEmpty()) {
            val mimeType = extras.getString(ConversationsActivity.EXTRA_TYPE)
            if (context != null) {
                stageUris(sharedUris, if (mimeType?.startsWith("image/") == true) Attachment.Type.IMAGE else Attachment.Type.FILE)
            } else {
                pendingSharedUris = sharedUris
                pendingSharedMimeType = mimeType
            }
        }
        val nick = extras.getString(ConversationsActivity.EXTRA_NICK)
        if (!nick.isNullOrEmpty()) {
            if (extras.getBoolean(ConversationsActivity.EXTRA_IS_PRIVATE_MESSAGE, false)) {
                val address = conversation.getAddress()
                try {
                    privateMessageWith(
                        eu.siacs.conversations.xmpp.Jid.of(
                            address.getLocal(),
                            address.getDomain(),
                            nick,
                        )
                    )
                } catch (_: IllegalArgumentException) {}
            } else if (conversation.mucOptions.participating() ||
                conversation.getNextCounterpart() != null
            ) {
                highlightInConference(nick)
            }
        }
        refreshMessages()
        markRead()
    }

    /** Switches the composer to a private message addressed to the given MUC participant. */
    fun privateMessageWith(counterpart: eu.siacs.conversations.xmpp.Jid) {
        val c = conversation ?: return
        eu.siacs.conversations.xmpp.manager.ChatStateManager.send(c, Config.DEFAULT_CHAT_STATE)
        c.setNextCounterpart(counterpart)
        state.setInput("")
        refreshMessages()
    }

    private fun highlightInConference(nick: String) {
        val current = state.getInput()
        state.setInput(
            when {
                current.isBlank() -> "$nick: "
                current.endsWith(" ") -> "$current$nick "
                else -> "$current $nick "
            }
        )
    }

    fun getConversation(): Conversation? = conversation

    fun onArrowUpCtrlPressed(): Boolean = false

    override fun refresh() {
        refreshMessages()
    }

    override fun onBackendConnected() {
        val service = getXmppConnectionService() ?: return
        val uuid = pendingConversationUuid
        if (conversation == null && uuid != null) {
            val restored = service.findConversationByUuidReliable(uuid)
            if (restored != null) {
                reInit(restored, Bundle())
                return
            }
        }
        refreshMessages()
    }

    private fun refreshMessages() {
        val c = conversation
        if (c == null) {
            state.update(null, emptyList())
            return
        }
        val list = ArrayList<Message>()
        c.populateWithMessages(list)
        state.update(c, list)
        // Sync remote-editing indicators: in-memory map (live) OR persisted DB flag (survives navigation)
        val service = getXmppConnectionService()
        val indicators = service?.remoteEditingIndicators
        for (msg in list) {
            val uuid = msg.getUuid() ?: continue
            val active = indicators?.get(uuid) == true || msg.isRemoteEditing()
            state.setRemoteEditing(uuid, active)
        }
        refreshPinned()
    }

    private fun markRead() {
        val c = conversation ?: return
        if (!isResumed) return
        val activity = activity as? OnConversationRead ?: return
        val lastUuid = state.messages.value.lastOrNull()?.getUuid()
        activity.onConversationRead(c, lastUuid ?: "")
        state.unreadCount.intValue = 0
    }

    // ---- ConversationScreenListener ----

    override fun onBackPressed() {
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onSendTextMessage(body: String) {
        val c = conversation ?: return
        if (body.isBlank()) return
        val service = getXmppConnectionService() ?: return
        when (c.nextEncryption) {
            Message.ENCRYPTION_AXOLOTL -> {
                if (trustKeysIfNeeded(c) { onSendTextMessage(body) }) return
            }
            Message.ENCRYPTION_PGP -> {
                // OpenPGP sending is not wired into the Compose screen yet.
                Toast.makeText(requireContext(), R.string.openpgp_error, Toast.LENGTH_SHORT).show()
                return
            }
        }
        val correcting = state.correcting.value
        val message: Message
        if (correcting != null) {
            onEditingStopped(correcting)
            message = correcting
            message.setBody(body)
            message.putEdited(message.getUuid(), message.serverMsgId)
            message.replaceUuid(java.util.UUID.randomUUID().toString())
        } else {
            message = Message(c, body, c.nextEncryption)
            Message.configurePrivateMessage(message)
        }
        val reply = state.replyingTo.value
        if (reply != null) {
            // XEP-0461: for received messages, use remoteMsgId (the sender's original stanza id)
            // — both sides know it. Our server's archive id (serverMsgId) is meaningless to the
            // sender's client. For own sent messages, use serverMsgId ?? uuid (uuid = stanza id).
            val replyId = if (reply.status == Message.STATUS_RECEIVED) {
                reply.remoteMsgId ?: reply.serverMsgId ?: reply.getUuid()
            } else {
                reply.serverMsgId ?: reply.getUuid()
            }
            message.setRepliedTo(replyId)
        }
        service.sendMessage(message)
        state.replyingTo.value = null
        state.correcting.value = null
        state.setInput("")
        refreshMessages()
        markRead()
    }

    override fun onStartRecording() {
        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onPauseRecording() {
        val recording = state.recordingState.value as? RecordingUiState.Active ?: return
        val recorder = mediaRecorder ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        if (!recording.paused) {
            try {
                recorder.pause()
                pauseStartMs = System.currentTimeMillis()
                timerJob?.cancel()
            } catch (_: IllegalStateException) {
                return
            }
            state.recordingState.value = RecordingUiState.Active(recording.elapsedMs, paused = true)
        } else {
            try {
                recorder.resume()
                totalPausedMs += System.currentTimeMillis() - pauseStartMs
                pauseStartMs = 0L
            } catch (_: IllegalStateException) {
                return
            }
            state.recordingState.value = RecordingUiState.Active(recording.elapsedMs, paused = false)
            startRecordingTimer()
        }
    }

    override fun onCancelRecording() {
        stopRecordingSession(save = false)
        state.recordingState.value = RecordingUiState.Idle
    }

    override fun onSendRecording() {
        val currentElapsed = (state.recordingState.value as? RecordingUiState.Active)?.elapsedMs ?: 0L
        stopRecordingSession(save = true)
        val file = recordingFile ?: run {
            state.recordingState.value = RecordingUiState.Idle
            return
        }
        val ctx = context ?: run {
            state.recordingState.value = RecordingUiState.Idle
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.files",
            file,
        )
        val attachment = eu.siacs.conversations.ui.util.Attachment.of(ctx, uri, eu.siacs.conversations.ui.util.Attachment.Type.RECORDING).firstOrNull()
        if (attachment != null) {
            commitAttachments(listOf(attachment))
        }
        state.recordingState.value = RecordingUiState.Idle
        recordingFile = null
    }

    private fun startNativeRecording() {
        val ctx = context ?: return
        val outputFormat =
            if (eu.siacs.conversations.Config.USE_OPUS_VOICE_MESSAGES &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ) {
                MediaRecorder.OutputFormat.OGG
            } else {
                MediaRecorder.OutputFormat.MPEG_4
            }
        val file = eu.siacs.conversations.persistance.FileBackend.Cache(ctx).recording(outputFormat)
        recordingFile = file

        @Suppress("DEPRECATION")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(ctx)
        } else {
            MediaRecorder()
        }
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        } catch (e: RuntimeException) {
            recorder.release()
            Toast.makeText(ctx, R.string.unable_to_start_recording, Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            recorder.setPrivacySensitive(true)
        }
        if (eu.siacs.conversations.Config.USE_OPUS_VOICE_MESSAGES &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            recorder.setOutputFormat(MediaRecorder.OutputFormat.OGG)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            recorder.setAudioEncodingBitRate(32_000)
        } else {
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC)
            recorder.setAudioSamplingRate(24_000)
            recorder.setAudioEncodingBitRate(28_000)
        }
        recorder.setOutputFile(file.absolutePath)
        try {
            recorder.prepare()
            recorder.start()
        } catch (e: Exception) {
            recorder.release()
            Toast.makeText(ctx, R.string.unable_to_start_recording, Toast.LENGTH_SHORT).show()
            return
        }

        mediaRecorder = recorder
        recordingStartMs = System.currentTimeMillis()
        totalPausedMs = 0L
        pauseStartMs = 0L
        state.recordingState.value = RecordingUiState.Active(0L, paused = false)
        startRecordingTimer()
    }

    private fun startRecordingTimer() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - recordingStartMs - totalPausedMs
                val current = state.recordingState.value
                if (current is RecordingUiState.Active && !current.paused) {
                    state.recordingState.value = RecordingUiState.Active(elapsed, paused = false)
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }

    private fun stopRecordingSession(save: Boolean) {
        timerJob?.cancel()
        timerJob = null
        val recorder = mediaRecorder ?: return
        try {
            recorder.stop()
            recorder.release()
        } catch (_: Exception) {
        } finally {
            mediaRecorder = null
        }
        if (!save) {
            recordingFile?.delete()
            recordingFile = null
        }
    }

    private fun stageUris(uris: List<Uri>, type: Attachment.Type) {
        val context = context ?: return
        for (uri in uris) {
            state.attachments.addAll(Attachment.of(context, uri, type))
        }
    }

    override fun onCommitAttachments() {
        val c = conversation ?: return
        if (c.nextEncryption == Message.ENCRYPTION_AXOLOTL &&
            trustKeysIfNeeded(c) { onCommitAttachments() }
        ) {
            return
        }
        val attachments = state.attachments.toList()
        state.attachments.clear()
        commitAttachments(attachments)
    }

    private fun commitAttachments(attachments: List<Attachment>) {
        val c = conversation ?: return
        val service = getXmppConnectionService() ?: return
        val context = context ?: return
        for (attachment in attachments) {
            val future =
                if (attachment.type == Attachment.Type.IMAGE)
                    service.attachImageToConversation(c, attachment.uri, attachment.mime)
                else service.attachFileToConversation(c, attachment.uri, attachment.mime)
            Futures.addCallback(
                future,
                object : FutureCallback<Void?> {
                    override fun onSuccess(result: Void?) {
                        runOnUiThread { refreshMessages() }
                    }

                    override fun onFailure(t: Throwable) {
                        runOnUiThread {
                            val ctx = getContext() ?: return@runOnUiThread
                            Toast.makeText(ctx, t.message, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }
    }

    override fun onInputChanged(text: String) {
        val c = conversation ?: return
        if (getXmppConnectionService() == null) return
        typingPauseJob?.cancel()
        if (text.isEmpty() && lastComposedText.isNotEmpty()) {
            eu.siacs.conversations.xmpp.manager.ChatStateManager.send(
                c,
                Config.DEFAULT_CHAT_STATE,
            )
        } else if (text.isNotEmpty() && text != lastComposedText) {
            eu.siacs.conversations.xmpp.manager.ChatStateManager.send(
                c,
                im.conversations.android.xmpp.model.state.Composing::class.java,
            )
            typingPauseJob =
                viewLifecycleOwner.lifecycleScope.launch {
                    kotlinx.coroutines.delay(3000)
                    eu.siacs.conversations.xmpp.manager.ChatStateManager.send(
                        c,
                        im.conversations.android.xmpp.model.state.Paused::class.java,
                    )
                }
        }
        lastComposedText = text
    }

    /** Returns true when the OMEMO trust screen had to be opened first. */
    private fun trustKeysIfNeeded(conversation: Conversation, action: () -> Unit): Boolean {
        val axolotlService = conversation.getAccount().axolotlService
        val targets = axolotlService.getCryptoTargets(conversation)
        val hasUnaccepted = !conversation.acceptedCryptoTargets.containsAll(targets)
        val hasUndecidedOwn =
            axolotlService
                .getKeysWithTrust(
                    eu.siacs.conversations.crypto.axolotl.FingerprintStatus
                        .createActiveUndecided()
                )
                .isNotEmpty()
        val hasUndecidedContacts =
            axolotlService
                .getKeysWithTrust(
                    eu.siacs.conversations.crypto.axolotl.FingerprintStatus
                        .createActiveUndecided(),
                    targets,
                )
                .isNotEmpty()
        val hasPendingKeys = axolotlService.findDevicesWithoutSession(conversation).isNotEmpty()
        val hasNoTrustedKeys = axolotlService.anyTargetHasNoTrustedKeys(targets)
        val downloadInProgress = axolotlService.hasPendingKeyFetches(targets)
        return if (hasUndecidedOwn ||
            hasUndecidedContacts ||
            hasPendingKeys ||
            hasNoTrustedKeys ||
            hasUnaccepted ||
            downloadInProgress
        ) {
            axolotlService.createSessionsIfNeeded(conversation)
            pendingTrustAction = action
            val intent = Intent(requireActivity(), TrustKeysActivity::class.java)
            intent.putExtra("contacts", targets.map { it.toString() }.toTypedArray())
            intent.putExtra(
                XmppActivity.EXTRA_ACCOUNT,
                conversation.getAccount().jid.asBareJid().toString(),
            )
            intent.putExtra("conversation", conversation.getUuid())
            trustKeysLauncher.launch(intent)
            true
        } else {
            false
        }
    }

    override fun onAttachImage() {
        pickMediaLauncher.launch(
            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    override fun onTakePhoto() {
        val ctx = requireContext()
        val takePhotoFile = eu.siacs.conversations.persistance.FileBackend.Cache(ctx).takePicture()
        val photoUri = Uri.fromFile(takePhotoFile)
        pendingTakePhotoUri.push(photoUri)
        val intent = android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(
                android.provider.MediaStore.EXTRA_OUTPUT,
                eu.siacs.conversations.persistance.FileBackend.getUriForFile(ctx, takePhotoFile),
            )
            addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        takePhotoLauncher.launch(intent)
    }

    override fun onAttachFile() {
        pickFileLauncher.launch("*/*")
    }

    override fun onCall(video: Boolean) {
        val c = conversation ?: return
        if (c.getMode() != Conversational.MODE_SINGLE) return
        val service = getXmppConnectionService() ?: return
        // An ongoing call with this contact takes precedence: return to it instead.
        val ongoing =
            try {
                c.getAccount()
                    .xmppConnection
                    .getManager(eu.siacs.conversations.xmpp.manager.JingleManager::class.java)
                    .getOngoingRtpConnection(c.getContact())
            } catch (_: Exception) {
                com.google.common.base.Optional.absent()
            }
        if (ongoing.isPresent) {
            returnToOngoingCall(c, ongoing.get())
            return
        }
        if (eu.siacs.conversations.xmpp.manager.JingleManager.isBusy(service.accounts)) {
            Toast.makeText(requireContext(), R.string.only_one_call_at_a_time, Toast.LENGTH_LONG)
                .show()
            return
        }
        if (video) {
            videoCallPermissionLauncher.launch(
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            )
        } else {
            audioCallPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun returnToOngoingCall(
        c: Conversation,
        id: eu.siacs.conversations.xmpp.jingle.OngoingRtpSession,
    ) {
        val account = c.getAccount()
        val intent = Intent(requireActivity(), RtpSessionActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        intent.putExtra(RtpSessionActivity.EXTRA_ACCOUNT, account.jid.asBareJid().toString())
        intent.putExtra(RtpSessionActivity.EXTRA_WITH, id.getWith().toString())
        when (id) {
            is eu.siacs.conversations.xmpp.jingle.AbstractJingleConnection -> {
                intent.putExtra(RtpSessionActivity.EXTRA_SESSION_ID, id.getSessionId())
                startActivity(intent)
            }
            is eu.siacs.conversations.xmpp.manager.JingleManager.RtpSessionProposal -> {
                intent.putExtra(
                    RtpSessionActivity.EXTRA_LAST_ACTION,
                    if (eu.siacs.conversations.xmpp.jingle.Media.audioOnly(id.media))
                        RtpSessionActivity.ACTION_MAKE_VOICE_CALL
                    else RtpSessionActivity.ACTION_MAKE_VIDEO_CALL,
                )
                intent.putExtra(RtpSessionActivity.EXTRA_PROPOSED_SESSION_ID, id.sessionId)
                startActivity(intent)
            }
        }
    }

    private fun triggerRtpSession(action: String) {
        val c = conversation ?: return
        val service = getXmppConnectionService() ?: return
        val account = c.getAccount()
        val contact = c.getContact()
        if (Config.USE_JINGLE_MESSAGE_INIT && RtpCapability.jmiSupport(contact)) {
            CallIntegrationConnectionService.placeCall(
                service,
                account,
                contact.getAddress().asBareJid(),
                RtpSessionActivity.actionToMedia(action),
            )
        } else {
            val capability =
                if (action == RtpSessionActivity.ACTION_MAKE_VIDEO_CALL)
                    RtpCapability.Capability.VIDEO
                else RtpCapability.Capability.AUDIO
            PresenceSelector.selectFullJidForDirectRtpConnection(requireActivity(), contact, capability) {
                fullJid ->
                CallIntegrationConnectionService.placeCall(
                    service,
                    account,
                    fullJid,
                    RtpSessionActivity.actionToMedia(action),
                )
            }
        }
    }

    override fun onOpenDetails() {
        val c = conversation ?: return
        if (c.getMode() == Conversational.MODE_SINGLE) {
            requireXmppActivity().switchToContactDetails(c.getContact())
        } else {
            ConferenceDetailsActivity.open(requireActivity(), c)
        }
    }

    override fun onOpenMessage(message: Message) {
        val service = getXmppConnectionService() ?: return
        val context = context ?: return
        when {
            message.isGeoUri -> {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(message.body)))
                } catch (e: Exception) {
                    Toast.makeText(context, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT)
                        .show()
                }
            }
            message.isFileOrImage && !message.isDeleted -> {
                val file = service.fileBackend.getFile(message)
                ViewUtil.view(context, file, message.getUuid() ?: "")
            }
            message.treatAsDownloadable() -> onDownloadMessage(message)
        }
    }

    override fun onDownloadMessage(message: Message) {
        val service = getXmppConnectionService() ?: return
        service.httpConnectionManager.createNewDownloadConnection(message, true)
    }

    override fun onLoadMoreMessages() {
        val c = conversation ?: return
        val service = getXmppConnectionService() ?: return
        if (!c.messagesLoaded.get()) return
        val oldest = state.messages.value.firstOrNull() ?: return
        if (!loadingMoreMessages.compareAndSet(false, true)) return
        service.loadMoreMessages(
            c,
            oldest.timeSent,
            object : XmppConnectionService.OnMoreMessagesLoaded {
                override fun onMoreMessagesLoaded(count: Int, conversation: Conversation) {
                    loadingMoreMessages.set(false)
                    runOnUiThread {
                        if (this@ConversationComposeFragment.conversation == conversation) {
                            refreshMessages()
                        }
                    }
                }

                override fun informUser(r: Int) {
                    loadingMoreMessages.set(false)
                    runOnUiThread {
                        val ctx = context ?: return@runOnUiThread
                        Toast.makeText(ctx, r, Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }

    override fun onScrolledToBottom() {
        markRead()
    }

    override fun onSearchMessages() {
        val c = conversation ?: return
        val intent = Intent(requireActivity(), SearchActivity::class.java)
        intent.putExtra(SearchActivity.EXTRA_CONVERSATION_UUID, c.getUuid())
        startActivity(intent)
    }

    override fun onInviteContact() {
        val c = conversation ?: return
        inviteLauncher.launch(ChooseContactActivity.create(requireActivity(), c))
    }

    override fun onChooseEncryption() {
        val c = conversation ?: return
        val service = getXmppConnectionService() ?: return
        val labels =
            arrayOf<CharSequence>(
                getString(R.string.encryption_choice_omemo),
                getString(R.string.encryption_choice_unencrypted),
            )
        val encryptions = intArrayOf(Message.ENCRYPTION_AXOLOTL, Message.ENCRYPTION_NONE)
        val checked = encryptions.indexOf(c.nextEncryption).coerceAtLeast(0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.choose_encryption)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                if (c.setNextEncryption(encryptions[which])) {
                    service.updateConversation(c)
                }
                refreshMessages()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onMuteConversation() {
        val c = conversation ?: return
        val service = getXmppConnectionService() ?: return
        val durations = resources.getIntArray(R.array.mute_options_durations)
        val labels =
            Array<CharSequence>(durations.size) { i ->
                if (durations[i] == -1) getString(R.string.until_further_notice)
                else
                    eu.siacs.conversations.utils.TimeFrameUtils.resolve(
                        requireContext(),
                        1000L * durations[i],
                    )
            }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.disable_notifications)
            .setItems(labels) { _, which ->
                val till =
                    if (durations[which] == -1) Long.MAX_VALUE
                    else System.currentTimeMillis() + durations[which] * 1000L
                c.setMutedTill(till)
                service.updateConversation(c)
                (activity as? ConversationsActivity)?.onConversationsListItemUpdated()
                refreshMessages()
            }
            .show()
    }

    override fun onUnmuteConversation() {
        val c = conversation ?: return
        val service = getXmppConnectionService() ?: return
        c.setMutedTill(0)
        service.updateConversation(c)
        (activity as? ConversationsActivity)?.onConversationsListItemUpdated()
        refreshMessages()
    }

    override fun onTogglePinned() {
        val c = conversation ?: return
        val service = getXmppConnectionService() ?: return
        val pinned = c.getBooleanAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, false)
        c.setAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, !pinned)
        service.updateConversation(c)
        refreshMessages()
    }

    override fun onClearHistory() {
        val c = conversation ?: return
        val service = getXmppConnectionService() ?: return
        val dialogView =
            requireActivity().layoutInflater.inflate(R.layout.dialog_clear_history, null)
        val endConversationCheckBox =
            dialogView.findViewById<android.widget.CheckBox>(R.id.end_conversation_checkbox)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.clear_conversation_history)
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                service.clearConversationHistory(c)
                if (endConversationCheckBox.isChecked) {
                    service.archiveConversation(c)
                    (activity as? ConversationsActivity)?.onConversationArchived(c)
                } else {
                    (activity as? ConversationsActivity)?.onConversationsListItemUpdated()
                    refreshMessages()
                }
            }
            .show()
    }

    override fun onBlockContact() {
        val c = conversation ?: return
        val activity = activity as? XmppActivity ?: return
        BlockContactDialog.show(activity, c)
    }

    override fun onArchiveConversation() {
        val c = conversation ?: return
        getXmppConnectionService()?.archiveConversation(c)
    }

    override fun onSendReactions(message: Message, reactions: Set<String>) {
        val activity = activity as? XmppActivity ?: return
        activity.sendReactions(message, reactions)
    }

    override fun onAddReaction(message: Message) {
        val activity = activity as? XmppActivity ?: return
        activity.addReaction(message) { reactions ->
            activity.sendReactions(message, reactions.toSet())
        }
    }

    override fun onShowReactionDetails(message: Message, emoji: String) {
        val ctx = context ?: return
        val normalized = Emoticons.normalizeToVS16(emoji)
        val reactions = message.getReactions().filter { it.normalizedReaction() == normalized }
        val isMuc = message.conversation.getMode() == Conversational.MODE_MULTI
        val names = reactions.map { r ->
            if (isMuc) r.from?.getResource() ?: "?"
            else r.from?.asBareJid()?.toString() ?: "?"
        }.toTypedArray()
        MaterialAlertDialogBuilder(ctx)
            .setTitle(emoji)
            .setItems(names, null)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onCopyLink(message: Message) {
        val activity = requireXmppActivity()
        eu.siacs.conversations.ui.util.ShareUtil.copyLinkToClipboard(activity, message)
    }

    override fun onCopyUrl(message: Message) {
        val activity = requireXmppActivity()
        eu.siacs.conversations.ui.util.ShareUtil.copyUrlToClipboard(activity, message)
    }

    override fun onShareMessage(message: Message) {
        val activity = requireXmppActivity()
        eu.siacs.conversations.ui.util.ShareUtil.share(activity, message)
    }

    override fun onSaveFile(message: Message) {
        val ctx = context ?: return
        val storageLocation = message.getRelativeFilePath() ?: return
        if (storageLocation.sharedStorage()) return
        val future = requireXmppActivity().xmppConnectionService.fileBackend
            .saveInternalToExternal(storageLocation)
        Futures.addCallback(future, object : FutureCallback<Void?> {
            override fun onSuccess(result: Void?) {
                Toast.makeText(ctx,
                    resources.getQuantityString(R.plurals.attachments_saved, 1, 1),
                    Toast.LENGTH_LONG).show()
            }
            override fun onFailure(t: Throwable) {
                Toast.makeText(ctx, R.string.could_not_save_files, Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    override fun onDeleteMessage(message: Message) {
        state.deleteTarget.value = message
    }

    override fun onDeleteForEveryone(message: Message) {
        retractMessage(message)
    }

    override fun onDeleteForMyself(message: Message) {
        deleteMessageLocally(message)
    }

    override fun onEditingStarted(message: Message) {
        sendEditingStanza(message, "start")
    }

    override fun onEditingStopped(message: Message) {
        sendEditingStanza(message, "stop")
    }

    private fun sendEditingStanza(message: Message, action: String) {
        val service = getXmppConnectionService() ?: return
        val c = message.conversation as? Conversation ?: return
        val packet = im.conversations.android.xmpp.model.stanza.Message()
        packet.setFrom(c.getAccount().jid)
        if (c.getMode() == eu.siacs.conversations.entities.Conversational.MODE_SINGLE) {
            packet.setTo(message.counterpart.asBareJid())
        } else {
            packet.setTo(c.getAddress().asBareJid())
        }
        val editing = eu.siacs.conversations.xml.Element("editing", eu.siacs.conversations.xml.Namespace.IMPULSE_EDITING)
        // For already-edited messages, the current UUID is a replacement UUID — the receiver
        // stored the original UUID as remoteMsgId. Use editedIdWireFormat (= original UUID before
        // any edits) to stay in sync with what the Replace stanza also references.
        val indicatorId = if (message.edited()) message.editedIdWireFormat
                          else message.remoteMsgId ?: message.getUuid()
        editing.setAttribute("id", indicatorId)
        editing.setAttribute("action", action)
        packet.addChild(editing)
        service.sendMessagePacket(c.getAccount(), packet)
    }

    private fun retractMessage(message: Message) {
        val service = getXmppConnectionService() ?: return
        val c = message.conversation as? Conversation ?: return
        val packet = service.getMessageGenerator().generateRetraction(message)
        service.sendMessagePacket(c.getAccount(), packet)
        deleteMessageEntirely(message)
    }

    private fun deleteMessageLocally(message: Message) {
        if (message.isFileOrImage && !message.isDeleted && message.getRelativeFilePath() != null) {
            val service = getXmppConnectionService() ?: return
            if (service.fileBackend.deleteFile(message)) {
                message.setDeleted(true)
                service.evictPreview(message.getUuid())
                service.updateMessage(message, false)
                refreshMessages()
            }
            return
        }
        deleteMessageEntirely(message)
    }

    private fun deleteMessageEntirely(message: Message) {
        val service = getXmppConnectionService() ?: return
        val c = message.conversation as? Conversation ?: return
        if (message.isFileOrImage && message.getRelativeFilePath() != null) {
            service.fileBackend.deleteFile(message)
            service.evictPreview(message.getUuid())
        }
        c.remove(message)
        service.databaseBackend.deleteMessage(message.getUuid())
        service.getNotificationService().clear(message)
        refreshMessages()
    }

    override fun onCancelTransmission(message: Message) {
        val service = getXmppConnectionService() ?: return
        val t = message.transferable
        if (t != null) {
            t.cancel()
        } else if (message.status != Message.STATUS_RECEIVED) {
            service.markMessage(message, Message.STATUS_SEND_FAILED, Message.ERROR_MESSAGE_CANCELLED)
        }
    }

    override fun onResendMessage(message: Message) {
        val service = getXmppConnectionService() ?: return
        val activity = activity as? XmppActivity ?: return
        val c = message.conversation as? Conversation ?: return
        if (message.isFileOrImage) {
            val file = service.fileBackend.getFile(message)
            if (!(file.exists() && file.canRead()) && !message.hasFileOnRemoteHost()) {
                if (!Compatibility.hasStoragePermission(activity)) {
                    Toast.makeText(
                        activity,
                        getString(R.string.no_storage_permission, getString(R.string.app_name)),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(activity, R.string.file_deleted, Toast.LENGTH_SHORT).show()
                    message.setDeleted(true)
                    service.updateMessage(message, false)
                    (activity as? ConversationsActivity)?.onConversationsListItemUpdated()
                    refreshMessages()
                }
                return
            }
        }
        service.resendFailedMessages(message, false)
    }

    override fun onScrollToMessage(message: Message) {
        val uuid = message.getUuid()
        if (state.messages.value.none { it.getUuid() == uuid }) {
            val service = getXmppConnectionService() ?: return
            val c = conversation ?: return
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                // Load a batch ending just after this message so it's included in the window.
                val batch = service.databaseBackend.getMessages(c, 50, message.timeSent + 1)
                val existingUuids = state.messages.value.mapTo(HashSet()) { it.getUuid() }
                val fresh = batch.filter { it.getUuid() !in existingUuids }
                if (fresh.isNotEmpty()) c.addAll(0, fresh)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    refreshMessages()
                    state.requestScrollToUuid.value = uuid
                }
            }
        } else {
            state.requestScrollToUuid.value = uuid
        }
    }

    override fun onPinMessage(message: Message) {
        val service = getXmppConnectionService() ?: return
        message.setPinned(true)
        service.updateMessage(message, false)
        refreshPinned()
        refresh()
    }

    override fun onUnpinMessage(message: Message) {
        val service = getXmppConnectionService() ?: return
        message.setPinned(false)
        service.updateMessage(message, false)
        refreshPinned()
        refresh()
    }

    private fun refreshPinned() {
        val c = conversation ?: return
        val service = getXmppConnectionService() ?: return
        val pinned = service.databaseBackend.getPinnedMessages(c)
        state.updatePinned(pinned)
    }

    companion object {
        private const val STATE_CONVERSATION_UUID =
            "eu.siacs.conversations.ui.ConversationComposeFragment.uuid"

        @JvmStatic
        fun get(activity: AppCompatActivity): ConversationComposeFragment? {
            val fragmentManager = activity.supportFragmentManager
            for (@IdRes id in intArrayOf(R.id.main_fragment, R.id.secondary_fragment)) {
                val fragment = fragmentManager.findFragmentById(id)
                if (fragment is ConversationComposeFragment) return fragment
            }
            return null
        }

        @JvmStatic
        fun getConversation(activity: FragmentActivity): Conversation? {
            for (@IdRes id in intArrayOf(R.id.main_fragment, R.id.secondary_fragment)) {
                val fragment = activity.supportFragmentManager.findFragmentById(id)
                if (fragment is ConversationComposeFragment) return fragment.conversation
            }
            return null
        }

        @JvmStatic
        fun getConversationReliable(activity: AppCompatActivity): Conversation? {
            return getConversation(activity)
        }
    }
}
