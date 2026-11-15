package eu.siacs.conversations.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.siacs.conversations.entities.Message

/** The real chat screen with an action adapter that cannot reach an account service. */
@Composable
internal fun DemoChatHost(session: DemoSession, chat: DemoConversation, onBack: () -> Unit) {
    val state = remember(chat) { ConversationScreenState().apply { update(chat, chat.snapshotMessages()) } }
    val context = LocalContext.current
    val listener = remember(session, chat) { DemoChatActions(session, chat, state, context, onBack) }
    BackHandler { onBack() }
    ImpulseExpressiveTheme { ConversationScreen(state, listener) }
}

private class DemoChatActions(
    private val session: DemoSession,
    private val chat: DemoConversation,
    private val state: ConversationScreenState,
    private val context: android.content.Context,
    private val back: () -> Unit,
) : ConversationScreenListener {
    private fun refresh() = state.update(chat, chat.snapshotMessages())
    private fun unavailable() { android.widget.Toast.makeText(context, eu.siacs.conversations.R.string.demo_mode_unavailable, android.widget.Toast.LENGTH_SHORT).show() }
    private fun remove(message: Message) { session.remove(chat, message); refresh() }
    override fun onBackPressed() = back()
    override fun onSendTextMessage(body: String) {
        if (body.isBlank()) return
        val editing = state.correcting.value
        var launch = false
        if (editing != null) {
            editing.pendingMorphFromBody = editing.body
            editing.setBody(body)
            editing.putEdited(editing.getUuid(), editing.serverMsgId)
            editing.replaceUuid(java.util.UUID.randomUUID().toString())
        } else {
            val message = session.send(chat, body)
            state.replyingTo.value?.getUuid()?.let(message::setRepliedTo)
            if (LiquidGlass.enabled(context) && AppearanceState.fullEffects) {
                launch = state.sendLaunches.enqueue(message.getUuid(), chat.getUuid(), state.sendLaunches.textOrigin, body, false) { state.setInput("") }
            }
        }
        if (!launch) state.setInput("")
        state.correcting.value = null
        state.replyingTo.value = null
        if (chat.demoStatuses[true]?.clearOnSend == true) chat.demoStatuses.remove(true)
        refresh()
    }
    override fun onInputChanged(text: String) { state.setInput(text) }
    override fun onSetReplyStatus(code: String, clearOnSend: Boolean) {
        if (code == "clear") chat.demoStatuses.remove(true) else {
            val now = System.currentTimeMillis()
            chat.demoStatuses[true] = ReplyStatuses.Status(code, now, now + ReplyStatuses.TTL_MS, clearOnSend)
        }
        state.replyStatusPickerVisible.value = false
        state.attachCardOpen.value = false
        refresh()
    }
    override fun onDeleteMessage(message: Message) = remove(message)
    override fun onDeleteForEveryone(message: Message) = remove(message)
    override fun onDeleteForMyself(message: Message) = remove(message)
    override fun onModerateMessage(message: Message) = remove(message)
    override fun onDeleteSelectedMessages(messages: List<Message>) { messages.forEach { session.remove(chat, it) }; refresh() }
    override fun onDeleteMediaGroupForEveryone(messages: List<Message>) = onDeleteSelectedMessages(messages)
    override fun onModerateMediaGroup(messages: List<Message>) = onDeleteSelectedMessages(messages)
    override fun onScrollToMessage(message: Message) { state.requestScrollToUuid.value = message.getUuid() }
    override fun onPinMessage(message: Message) { state.updatePinned(state.pinnedMessages.value + message) }
    override fun onUnpinMessage(message: Message) { state.updatePinned(state.pinnedMessages.value - message) }
    override fun onOpenHelp() = unavailable()
    override fun onAddContact() = unavailable()
    override fun onAttachImage() {
        val photo = session.photo(chat)
        state.getInput().trim().takeIf { it.isNotEmpty() }?.let { caption ->
            session.send(chat, caption).setRepliedTo(photo.getUuid())
            state.setInput("")
        }
        state.attachCardOpen.value = false
        refresh()
    }
    override fun onTakePhoto() = unavailable()
    override fun onAttachFile() = unavailable()
    override fun onShareLocation() = unavailable()
    override fun onSharePhoneContact() = unavailable()
    override fun onShareImpulseContact() = unavailable()
    override fun onTrackForContact(kind: String, titleEn: String, titleRu: String, checkNow: Boolean) = unavailable()
    override fun onSupportCheckUpdates() = unavailable()
    override fun onOpenUpdates() = unavailable()
    override fun onCall(video: Boolean) = unavailable()
    override fun onOpenDetails() = unavailable()
    override fun onOpenMessage(message: Message) = unavailable()
    override fun onOpenMediaGroup(messages: List<Message>, tapped: Message) = unavailable()
    override fun onDownloadMessage(message: Message) = unavailable()
    override fun onLoadMoreMessages() {}
    override fun onScrolledToBottom() {}
    override fun onMarkReadUpTo(uuid: String) {}
    override fun onStartRecording() = unavailable()
    override fun onPauseRecording() = unavailable()
    override fun onCancelRecording() = unavailable()
    override fun onSendRecording() = unavailable()
    override fun onCommitAttachments() = unavailable()
    override fun onSearchMessages() = unavailable()
    override fun onInviteContact() = unavailable()
    override fun onChooseEncryption() = unavailable()
    override fun onMuteConversation() = unavailable()
    override fun onUnmuteConversation() = unavailable()
    override fun onTogglePinned() = unavailable()
    override fun onClearHistory() = unavailable()
    override fun onBlockContact() = unavailable()
    override fun onArchiveConversation() = unavailable()
    override fun onSendReactions(message: Message, reactions: Set<String>) = unavailable()
    override fun onAddReaction(message: Message) = unavailable()
    override fun onDoubleTapReaction(message: Message) = unavailable()
    override fun onShowReactionDetails(message: Message, emoji: String) = unavailable()
    override fun onCopyLink(message: Message) = unavailable()
    override fun onCopyUrl(message: Message) = unavailable()
    override fun onShareMessage(message: Message) = unavailable()
    override fun onForwardMessage(message: Message) = unavailable()
    override fun onPrivateMessage(message: Message) = unavailable()
    override fun onSaveFile(message: Message) = unavailable()
    override fun onEditingStarted(message: Message) {}
    override fun onEditingStopped(message: Message) {}
    override fun onCancelTransmission(message: Message) = unavailable()
    override fun onResendMessage(message: Message) = unavailable()
    override fun onRetryAsP2P(message: Message) = unavailable()
    override fun onCopySelectedMessages(messages: List<Message>) = unavailable()
    override fun onForwardSelectedMessages(messages: List<Message>) = unavailable()
    override fun onOpenMediaSelector(messages: List<Message>, forDelete: Boolean) = unavailable()
}
