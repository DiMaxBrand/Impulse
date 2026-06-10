package eu.siacs.conversations.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.services.CallIntegrationConnectionService
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.ui.interfaces.OnConversationRead
import eu.siacs.conversations.ui.util.PresenceSelector
import eu.siacs.conversations.ui.util.ViewUtil
import eu.siacs.conversations.xmpp.jingle.RtpCapability
import java.util.concurrent.atomic.AtomicBoolean

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
    private var pendingSendText: String? = null

    private val pickImagesLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            attachUris(uris, asImage = true)
        }

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) attachUris(listOf(uri), asImage = false)
        }

    private val trustKeysLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                val text = pendingSendText
                pendingSendText = null
                if (text != null) onSendTextMessage(text)
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
    }

    fun reInit(conversation: Conversation, extras: Bundle) {
        this.conversation = conversation
        this.pendingConversationUuid = null
        conversation.messagesLoaded.set(true)
        val text = extras.getString(Intent.EXTRA_TEXT)
        if (!text.isNullOrBlank() &&
            !extras.getBoolean(ConversationsActivity.EXTRA_DO_NOT_APPEND, false)
        ) {
            state.setInput(text)
        }
        refreshMessages()
        markRead()
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
    }

    private fun markRead() {
        val c = conversation ?: return
        if (!isResumed) return
        val activity = activity as? OnConversationRead ?: return
        val lastUuid = state.messages.lastOrNull()?.getUuid()
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
                if (trustKeysIfNeeded(c, body)) return
            }
            Message.ENCRYPTION_PGP -> {
                // OpenPGP sending is not wired into the Compose screen yet.
                Toast.makeText(requireContext(), R.string.openpgp_error, Toast.LENGTH_SHORT).show()
                return
            }
        }
        val message = Message(c, body, c.nextEncryption)
        Message.configurePrivateMessage(message)
        service.sendMessage(message)
        state.setInput("")
        refreshMessages()
        markRead()
    }

    /** Returns true when the OMEMO trust screen had to be opened first. */
    private fun trustKeysIfNeeded(conversation: Conversation, body: String): Boolean {
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
            pendingSendText = body
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
        pickImagesLauncher.launch("image/*")
    }

    override fun onAttachFile() {
        pickFileLauncher.launch("*/*")
    }

    private fun attachUris(uris: List<Uri>, asImage: Boolean) {
        val c = conversation ?: return
        val service = getXmppConnectionService() ?: return
        val context = context ?: return
        for (uri in uris) {
            val type = context.contentResolver.getType(uri)
            val future =
                if (asImage) service.attachImageToConversation(c, uri, type)
                else service.attachFileToConversation(c, uri, type)
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

    override fun onCall(video: Boolean) {
        val c = conversation ?: return
        if (c.getMode() != Conversational.MODE_SINGLE) return
        val service = getXmppConnectionService() ?: return
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
        val oldest = state.messages.firstOrNull() ?: return
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
