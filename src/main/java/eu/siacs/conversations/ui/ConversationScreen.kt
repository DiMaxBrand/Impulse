package eu.siacs.conversations.ui

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.entities.Reaction
import eu.siacs.conversations.entities.Transferable
import eu.siacs.conversations.ui.adapter.MessageAdapter
import eu.siacs.conversations.utils.MessageUtils
import eu.siacs.conversations.utils.UIHelper
import im.conversations.android.xmpp.model.reactions.Restrictions
import im.conversations.android.xmpp.model.stanza.Presence
import im.conversations.android.xmpp.model.state.Composing
import eu.siacs.conversations.xmpp.manager.ChatStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class RecordingUiState {
    object Idle : RecordingUiState()
    data class Active(val elapsedMs: Long, val paused: Boolean) : RecordingUiState()
}

/** Observable state for the Compose conversation screen. */
class ConversationScreenState {
    // neverEqualPolicy: Conversation is a mutable Java object — same reference after an
    // in-place status update looks equal to structuralEqualityPolicy, so Compose would skip
    // recomposition. neverEqualPolicy treats every write as a change.
    internal val conversation = mutableStateOf<Conversation?>(null, androidx.compose.runtime.neverEqualPolicy())
    // neverEqualPolicy: same Message references after in-place status mutation look equal to
    // structuralEqualityPolicy, causing Compose to skip recomposition and leaving the status
    // icon stale. neverEqualPolicy treats every write as a change.
    internal val messages = mutableStateOf<List<Message>>(emptyList(), androidx.compose.runtime.neverEqualPolicy())
    internal val revision = mutableIntStateOf(0)
    internal val unreadCount = mutableIntStateOf(0)
    internal val inputText = mutableStateOf("")
    internal val replyingTo = mutableStateOf<Message?>(null)
    internal val correcting = mutableStateOf<Message?>(null)
    internal val attachments: SnapshotStateList<eu.siacs.conversations.ui.util.Attachment> =
        mutableStateListOf()
    val recordingState = mutableStateOf<RecordingUiState>(RecordingUiState.Idle)

    internal val pinnedMessages = mutableStateOf<List<Message>>(emptyList())
    internal val pinnedBannerVisible = mutableStateOf(false)
    internal val requestScrollToUuid = mutableStateOf<String?>(null)
    internal val deleteTarget = mutableStateOf<Message?>(null)
    // message UUIDs that a remote peer is actively editing right now
    internal val remoteEditingIds = mutableStateOf<Set<String>>(emptySet())

    fun update(conversation: Conversation?, source: List<Message>) {
        this.conversation.value = conversation
        messages.value = source
        unreadCount.intValue = conversation?.unreadCount() ?: 0
        revision.intValue++
    }

    fun setRemoteEditing(messageId: String, active: Boolean) {
        val current = remoteEditingIds.value
        remoteEditingIds.value = if (active) current + messageId else current - messageId
    }

    fun updatePinned(pinned: List<Message>) {
        pinnedMessages.value = pinned
        if (pinned.isEmpty()) pinnedBannerVisible.value = false
        else if (!pinnedBannerVisible.value) pinnedBannerVisible.value = true
    }

    fun setInput(text: String) {
        inputText.value = text
    }

    fun getInput(): String = inputText.value
}

/** Actions the screen delegates back to the hosting fragment. */
interface ConversationScreenListener {
    fun onBackPressed()

    fun onSendTextMessage(body: String)

    fun onAttachImage()

    fun onTakePhoto()

    fun onAttachFile()

    fun onCall(video: Boolean)

    fun onOpenDetails()

    fun onOpenMessage(message: Message)

    fun onDownloadMessage(message: Message)

    fun onLoadMoreMessages()

    fun onScrolledToBottom()

    fun onStartRecording()

    fun onPauseRecording()

    fun onCancelRecording()

    fun onSendRecording()

    fun onInputChanged(text: String)

    fun onCommitAttachments()

    fun onSearchMessages()

    fun onInviteContact()

    fun onChooseEncryption()

    fun onMuteConversation()

    fun onUnmuteConversation()

    fun onTogglePinned()

    fun onClearHistory()

    fun onBlockContact()

    fun onArchiveConversation()

    fun onSendReactions(message: Message, reactions: Set<String>)
    fun onAddReaction(message: Message)
    fun onShowReactionDetails(message: Message, emoji: String)
    fun onScrollToMessage(message: Message)
    fun onCopyLink(message: Message)
    fun onCopyUrl(message: Message)
    fun onShareMessage(message: Message)
    fun onSaveFile(message: Message)
    fun onDeleteMessage(message: Message)
    fun onDeleteForEveryone(message: Message)
    fun onDeleteForMyself(message: Message)
    fun onEditingStarted(message: Message)
    fun onEditingStopped(message: Message)
    fun onCancelTransmission(message: Message)
    fun onResendMessage(message: Message)
    fun onPinMessage(message: Message)
    fun onUnpinMessage(message: Message)
}

object ConversationScreenHelper {
    @JvmStatic
    fun setup(
        composeView: ComposeView,
        state: ConversationScreenState,
        listener: ConversationScreenListener,
    ) {
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent { ImpulseExpressiveTheme { ConversationScreen(state, listener) } }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImpulseExpressiveTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme =
        if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/** A chronological list entry: either a message bubble or a date pill. */
private sealed interface ChatItem {
    val key: String

    data class Msg(
        val message: Message,
        val firstOfGroup: Boolean,
        val lastOfGroup: Boolean,
    ) : ChatItem {
        override val key: String
            get() = message.getUuid() ?: message.hashCode().toString()
    }

    data class DatePill(val timestamp: Long) : ChatItem {
        override val key: String
            get() = "date-$timestamp"
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    return UIHelper.sameDay(a, b)
}

private fun sameSender(a: Message, b: Message): Boolean {
    val aReceived = a.status == Message.STATUS_RECEIVED
    val bReceived = b.status == Message.STATUS_RECEIVED
    if (aReceived != bReceived) return false
    if (!aReceived) return true
    val ac = a.counterpart
    val bc = b.counterpart
    return ac != null && bc != null && ac == bc
}

private const val GROUP_WINDOW_MILLIS = 5 * 60 * 1000L

private fun groupable(a: Message, b: Message): Boolean {
    if (a.type == Message.TYPE_STATUS || b.type == Message.TYPE_STATUS) return false
    if (a.type == Message.TYPE_RTP_SESSION || b.type == Message.TYPE_RTP_SESSION) return false
    return sameSender(a, b) &&
        Math.abs(a.timeSent - b.timeSent) < GROUP_WINDOW_MILLIS &&
        sameDay(a.timeSent, b.timeSent)
}

/** Builds the display list, newest first (for the reversed LazyColumn). */
private fun buildChatItems(messages: List<Message>): List<ChatItem> {
    val chronological = ArrayList<ChatItem>(messages.size + 8)
    for (i in messages.indices) {
        val message = messages[i]
        val previous = messages.getOrNull(i - 1)
        val next = messages.getOrNull(i + 1)
        if (previous == null || !sameDay(previous.timeSent, message.timeSent)) {
            chronological.add(ChatItem.DatePill(message.timeSent))
        }
        val firstOfGroup = previous == null || !groupable(previous, message)
        val lastOfGroup =
            next == null || !groupable(message, next) || !sameDay(message.timeSent, next.timeSent)
        chronological.add(ChatItem.Msg(message, firstOfGroup, lastOfGroup))
    }
    return chronological.asReversed()
}

@Composable
fun ConversationScreen(state: ConversationScreenState, listener: ConversationScreenListener) {
    val conversation = state.conversation.value
    var menuTarget by remember { mutableStateOf<Message?>(null) }
    Scaffold(
        topBar = {
            ConversationTopBar(
                conversation = conversation,
                revision = state.revision.intValue,
                listener = listener,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        // No imePadding here: the activity uses adjustResize, so the window itself shrinks for
        // the keyboard. Adding ime insets on top of that doubled the offset.
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val pinned = state.pinnedMessages.value
            val bannerVisible = state.pinnedBannerVisible.value
            if (bannerVisible && pinned.isNotEmpty()) {
                PinnedBanner(
                    pinnedMessages = pinned,
                    onDismiss = { state.pinnedBannerVisible.value = false },
                    onScrollTo = { listener.onScrollToMessage(it) },
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                MessageList(
                    state = state,
                    listener = listener,
                    onLongPress = { menuTarget = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            InputBar(state = state, listener = listener)
        }
    }
    val target = menuTarget
    if (target != null) {
        MessageContextSheet(
            message = target,
            state = state,
            listener = listener,
            onDismiss = { menuTarget = null },
        )
    }
    val deleteTarget = state.deleteTarget.value
    if (deleteTarget != null) {
        DeleteMessageSheet(
            message = deleteTarget,
            onDeleteForEveryone = {
                state.deleteTarget.value = null
                listener.onDeleteForEveryone(deleteTarget)
            },
            onDeleteForMyself = {
                state.deleteTarget.value = null
                listener.onDeleteForMyself(deleteTarget)
            },
            onDismiss = { state.deleteTarget.value = null },
        )
    }
}

/** M3 Expressive floating menu: large rounded container on surfaceContainer. */
@Composable
private fun ExpressiveDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        content = content,
    )
}

@Composable
private fun ExpressiveMenuItem(iconRes: Int, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun PinnedBanner(
    pinnedMessages: List<Message>,
    onDismiss: () -> Unit,
    onScrollTo: (Message) -> Unit,
) {
    var currentIndex by remember(pinnedMessages) { mutableIntStateOf(0) }
    val message = pinnedMessages.getOrNull(currentIndex) ?: return
    val total = pinnedMessages.size

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_push_pin_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f).clickable {
                    onScrollTo(message)
                    currentIndex = (currentIndex + 1) % total
                }
            ) {
                Text(
                    text = if (total > 1)
                        stringResource(R.string.pinned_message) + " ${currentIndex + 1}/$total"
                    else
                        stringResource(R.string.pinned_message),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = MessageUtils.replyPreview(message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_close_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ConversationTopBar(
    conversation: Conversation?,
    revision: Int,
    listener: ConversationScreenListener,
) {
    val context = LocalContext.current
    val isSingle = conversation?.getMode() == Conversational.MODE_SINGLE

    val availability: Presence.Availability? =
        remember(conversation, revision) {
            if (conversation != null && isSingle) {
                try {
                    conversation.getContact().shownStatus
                } catch (_: Exception) {
                    null
                }
            } else null
        }
    val isTyping: Boolean =
        remember(conversation, revision) {
            if (conversation != null && isSingle) {
                try {
                    val s =
                        conversation.getAccount()
                            .xmppConnection
                            ?.getManager(ChatStateManager::class.java)
                            ?.getIncoming(conversation.getAddress())
                    s == Composing::class.java
                } catch (_: Exception) {
                    false
                }
            } else false
        }

    val avatarState = remember(conversation?.getUuid()) { mutableStateOf<ImageBitmap?>(null) }
    val avatarSizePx = with(LocalDensity.current) { 40.dp.toPx() }.toInt()
    LaunchedEffect(conversation, revision) {
        val activity = context as? XmppActivity ?: return@LaunchedEffect
        val c = conversation ?: return@LaunchedEffect
        val bm =
            withContext(Dispatchers.IO) {
                try {
                    activity.avatarService().get(c, avatarSizePx, false)
                } catch (_: Exception) {
                    null
                }
            }
        if (bm != null) avatarState.value = bm.asImageBitmap()
    }

    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = listener::onBackPressed) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back_24dp),
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .clickable { listener.onOpenDetails() }
                        .padding(vertical = 2.dp, horizontal = 2.dp),
            ) {
                val avatar = avatarState.value
                if (avatar != null) {
                    Image(
                        bitmap = avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier.size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = conversation?.getName()?.toString() ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle: String? =
                        when {
                            isTyping -> stringResource(R.string.typing_indicator)
                            availability == Presence.Availability.CHAT ||
                                availability == Presence.Availability.ONLINE ->
                                stringResource(R.string.presence_online)
                            availability == Presence.Availability.AWAY ||
                                availability == Presence.Availability.XA ->
                                stringResource(R.string.presence_away)
                            availability == Presence.Availability.DND ->
                                stringResource(R.string.presence_dnd)
                            else -> null
                        }
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (isTyping) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        actions = {
            if (isSingle) {
                IconButton(onClick = { listener.onCall(false) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_call_24dp),
                        contentDescription = stringResource(R.string.audio_call),
                    )
                }
                IconButton(onClick = { listener.onCall(true) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_videocam_24dp),
                        contentDescription = stringResource(R.string.video_call),
                    )
                }
            }
            // Menu visibility state, refreshed with every conversation update.
            val isMuted =
                remember(conversation, revision) {
                    try {
                        conversation?.isMuted ?: false
                    } catch (_: Exception) {
                        false
                    }
                }
            val isPinned =
                remember(conversation, revision) {
                    conversation?.getBooleanAttribute(
                        Conversation.ATTRIBUTE_PINNED_ON_TOP,
                        false,
                    ) ?: false
                }
            val canInvite =
                remember(conversation, revision) {
                    try {
                        if (conversation == null) false
                        else if (isSingle)
                            !conversation
                                .getAccount()
                                .xmppConnection
                                .getManager(
                                    eu.siacs.conversations.xmpp.manager.MultiUserChatManager::class
                                        .java
                                )
                                .services
                                .isEmpty()
                        else conversation.mucOptions.canInvite()
                    } catch (_: Exception) {
                        false
                    }
                }
            val showEncryption =
                remember(conversation, revision) {
                    try {
                        conversation != null &&
                            !eu.siacs.conversations.crypto.OmemoSetting.isAlways() &&
                            (isSingle || conversation.mucOptions.participating())
                    } catch (_: Exception) {
                        false
                    }
                }
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_horiz_24dp),
                    contentDescription = stringResource(R.string.more_options),
                )
            }
            ExpressiveDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                val dismissThen: (() -> Unit) -> () -> Unit = { action ->
                    {
                        menuOpen = false
                        action()
                    }
                }
                ExpressiveMenuItem(
                    R.drawable.ic_search_24dp,
                    stringResource(R.string.search_messages),
                    dismissThen(listener::onSearchMessages),
                )
                if (canInvite) {
                    ExpressiveMenuItem(
                        R.drawable.ic_person_add_24dp,
                        stringResource(
                            if (isSingle) R.string.start_group_chat else R.string.invite_contact
                        ),
                        dismissThen(listener::onInviteContact),
                    )
                }
                if (showEncryption) {
                    ExpressiveMenuItem(
                        R.drawable.ic_lock_24dp,
                        stringResource(R.string.choose_encryption),
                        dismissThen(listener::onChooseEncryption),
                    )
                }
                if (isMuted) {
                    ExpressiveMenuItem(
                        R.drawable.ic_notifications_24dp,
                        stringResource(R.string.enable_notifications),
                        dismissThen(listener::onUnmuteConversation),
                    )
                } else {
                    ExpressiveMenuItem(
                        R.drawable.ic_notifications_off_24dp,
                        stringResource(R.string.disable_notifications),
                        dismissThen(listener::onMuteConversation),
                    )
                }
                ExpressiveMenuItem(
                    R.drawable.ic_star_24dp,
                    stringResource(
                        if (isPinned) R.string.remove_from_favorites
                        else R.string.add_to_favorites
                    ),
                    dismissThen(listener::onTogglePinned),
                )
                ExpressiveMenuItem(
                    R.drawable.ic_delete_24dp,
                    stringResource(R.string.action_clear_history),
                    dismissThen(listener::onClearHistory),
                )
                if (isSingle) {
                    ExpressiveMenuItem(
                        R.drawable.ic_cancel_24dp,
                        stringResource(R.string.action_block_contact),
                        dismissThen(listener::onBlockContact),
                    )
                }
                ExpressiveMenuItem(
                    R.drawable.ic_archive_24dp,
                    stringResource(R.string.action_archive_chat),
                    dismissThen(listener::onArchiveConversation),
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageList(
    state: ConversationScreenState,
    listener: ConversationScreenListener,
    onLongPress: (Message) -> Unit,
    modifier: Modifier = Modifier,
) {
    val revision = state.revision.intValue
    // Compute items in composable scope, not in derivedStateOf. derivedStateOf suppresses
    // recomposition when the result is structurally equal — same Message instances produce equal
    // ChatItem.Msg wrappers, so in-place status changes (sent→delivered→read) would be invisible.
    // Reading `revision` here makes MessageList recompose on every refresh(), which rebuilds items
    // and passes the new `revision` into every visible MessageRow so footers re-render.
    @Suppress("UNUSED_EXPRESSION") revision
    val items = buildChatItems(state.messages.value)
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val conversation = state.conversation.value
    val isTyping: Boolean =
        remember(conversation, revision) {
            if (conversation != null && conversation.getMode() == Conversational.MODE_SINGLE) {
                try {
                    val s =
                        conversation
                            .getAccount()
                            .xmppConnection
                            ?.getManager(ChatStateManager::class.java)
                            ?.getIncoming(conversation.getAddress())
                    s == Composing::class.java
                } catch (_: Exception) {
                    false
                }
            } else false
        }

    // Request older messages when the user approaches the (chronological) top.
    LaunchedEffect(listState, revision) {
        snapshotFlow {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                last >= info.totalItemsCount - 4 && info.totalItemsCount > 0
            }
            .distinctUntilChanged()
            .collect { nearTop -> if (nearTop) listener.onLoadMoreMessages() }
    }

    // Notify when the newest message becomes visible so read markers can be sent.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 }
            .distinctUntilChanged()
            .collect { atBottom -> if (atBottom) listener.onScrolledToBottom() }
    }

    // Keep pinned to the bottom when a new message arrives while we are (nearly) there.
    val newestKey = items.firstOrNull()?.key
    LaunchedEffect(newestKey) {
        if (listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
            listener.onScrolledToBottom()
        }
    }

    // Scroll to a specific message when requested (e.g. from pinned banner tap).
    val scrollToUuid = state.requestScrollToUuid.value
    LaunchedEffect(scrollToUuid) {
        if (scrollToUuid != null) {
            val idx = items.indexOfFirst { it is ChatItem.Msg && it.message.getUuid() == scrollToUuid }
            if (idx >= 0) listState.animateScrollToItem(idx)
            state.requestScrollToUuid.value = null
        }
    }

    // Resolves the message a reply refers to, by stanza id or uuid.
    val resolveReply: (String) -> Message? =
        remember(revision) {
            { id ->
                state.messages.value.lastOrNull { m ->
                    id == m.serverMsgId || id == m.getUuid() || id == m.remoteMsgId
                }
            }
        }

    var highlightKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(highlightKey) {
        if (highlightKey != null) {
            kotlinx.coroutines.delay(1500)
            highlightKey = null
        }
    }
    val onReplyCardClick: (Message) -> Unit = { original ->
        val key = original.getUuid() ?: ""
        val index = items.indexOfFirst { it.key == key }
        if (index >= 0) {
            highlightKey = key
            scope.launch { listState.animateScrollToItem(index) }
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            if (isTyping) {
                item(key = "typing-indicator") { TypingBubble(modifier = Modifier.animateItem()) }
            }
            itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                // All three animateItem specs are null to prevent intermittent blank bubbles.
                //
                // fadeInSpec/fadeOutSpec = null: animateItem() triggers enter-fade whenever a slot
                // re-enters the composition window (scroll back after leaving lookahead range).
                // That renders items at alpha≈0 for ~300 ms — visually blank.
                //
                // placementSpec = null: the default placement spring animates an item from an
                // off-screen offset to its final position over several frames. For items that
                // contain an AndroidView (LinkifiedMessageText), the embedded TextView is created
                // fresh by the key()-wrapped factory during those same frames but hasn't been
                // through Android's measure/layout pass yet — its RenderNode has no valid
                // dimensions. Whether the RenderThread draws before or after the View layout pass
                // completes is a VSYNC race: the same message can appear blank on one scroll and
                // fully visible on the next. Removing the placement spring eliminates the race
                // window entirely. New messages still appear naturally at the bottom because the
                // LaunchedEffect(newestKey) pins the list there; placement animation is not needed
                // for the normal bottom-pinned conversation flow.
                val itemModifier = Modifier.animateItem(
                    fadeInSpec = null,
                    placementSpec = null,
                    fadeOutSpec = null,
                )
                when (item) {
                    is ChatItem.DatePill ->
                        DatePill(timestamp = item.timestamp, modifier = itemModifier)
                    is ChatItem.Msg ->
                        MessageRow(
                            item = item,
                            isNewest = index == 0,
                            highlighted = item.key == highlightKey,
                            isBeingEdited = state.correcting.value?.getUuid() == item.message.getUuid()
                                    || state.remoteEditingIds.value.contains(item.message.getUuid()),
                            revision = revision,
                            listener = listener,
                            onLongPress = onLongPress,
                            resolveReply = resolveReply,
                            onReplyCardClick = onReplyCardClick,
                            modifier = itemModifier,
                        )
                }
            }
        }

        val showScrollToBottom by
            remember { androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex > 2 } }
        AnimatedVisibility(
            visible = showScrollToBottom,
            enter = scaleIn(),
            exit = scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Box {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_keyboard_double_arrow_down_24dp),
                        contentDescription = null,
                    )
                }
                val unread = state.unreadCount.intValue
                if (unread > 0) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier.align(Alignment.TopEnd)
                                .size(18.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                    ) {
                        Text(
                            text = if (unread > 99) "99+" else unread.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingBubble(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier.fillMaxWidth().padding(start = 12.dp - TAIL_WIDTH, top = 6.dp, bottom = 1.dp)
    ) {
        Surface(
            shape =
                rememberBubbleShape(firstOfGroup = true, lastOfGroup = true, outgoing = false),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            val transition = rememberInfiniteTransition(label = "typing")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.padding(
                        start = 16.dp + TAIL_WIDTH,
                        end = 16.dp,
                        top = 14.dp,
                        bottom = 14.dp,
                    ),
            ) {
                repeat(3) { index ->
                    val alpha by
                        transition.animateFloat(
                            initialValue = 0.25f,
                            targetValue = 1f,
                            animationSpec =
                                infiniteRepeatable(
                                    animation =
                                        tween(
                                            durationMillis = 600,
                                            delayMillis = index * 200,
                                            easing = LinearEasing,
                                        ),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                            label = "dot$index",
                        )
                    Box(
                        modifier =
                            Modifier.padding(horizontal = 2.dp)
                                .size(7.dp)
                                .graphicsLayer { this.alpha = alpha }
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                    CircleShape,
                                )
                    )
                }
            }
        }
    }
}

@Composable
private fun DatePill(timestamp: Long, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text =
                    DateUtils.getRelativeTimeSpanString(
                            timestamp,
                            System.currentTimeMillis(),
                            DateUtils.DAY_IN_MILLIS,
                            DateUtils.FORMAT_SHOW_WEEKDAY,
                        )
                        .toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

private val CORNER_LARGE: Dp = 20.dp
private val CORNER_SMALL: Dp = 5.dp
private val TAIL_WIDTH: Dp = 8.dp
private val TAIL_HEIGHT: Dp = 14.dp

/**
 * Bubble shape. Middle-of-group bubbles are plain rounded rects; the last bubble of a group
 * carries a small curled tail at its bottom corner on the sender's side. The tail occupies
 * [TAIL_WIDTH] inside the layout bounds, so callers compensate row padding and content padding
 * on that side to keep bubble bodies aligned within the group.
 */
@Composable
private fun rememberBubbleShape(
    firstOfGroup: Boolean,
    lastOfGroup: Boolean,
    outgoing: Boolean,
): androidx.compose.ui.graphics.Shape {
    val density = LocalDensity.current
    return remember(firstOfGroup, lastOfGroup, outgoing, density) {
        val top = if (firstOfGroup) CORNER_LARGE else CORNER_SMALL
        if (!lastOfGroup) {
            if (outgoing) {
                RoundedCornerShape(
                    topStart = CORNER_LARGE,
                    topEnd = top,
                    bottomStart = CORNER_LARGE,
                    bottomEnd = CORNER_SMALL,
                )
            } else {
                RoundedCornerShape(
                    topStart = top,
                    topEnd = CORNER_LARGE,
                    bottomStart = CORNER_SMALL,
                    bottomEnd = CORNER_LARGE,
                )
            }
        } else {
            with(density) {
                bubbleTailShape(
                    outgoing = outgoing,
                    groupTopCorner = top.toPx(),
                    largeCorner = CORNER_LARGE.toPx(),
                    tailWidth = TAIL_WIDTH.toPx(),
                    tailHeight = TAIL_HEIGHT.toPx(),
                )
            }
        }
    }
}

private fun bubbleTailShape(
    outgoing: Boolean,
    groupTopCorner: Float,
    largeCorner: Float,
    tailWidth: Float,
    tailHeight: Float,
): androidx.compose.ui.graphics.Shape =
    androidx.compose.foundation.shape.GenericShape { size, layoutDirection ->
        val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
        val tailOnRight = outgoing != rtl
        val tipR = tailWidth * 0.5f
        val h = size.height
        // Draw the entire bubble+tail outline as one continuous clockwise path so there
        // is no subpath junction at the 0-dp corner where the tail meets the bubble body.
        if (tailOnRight) {
            val right = size.width - tailWidth
            moveTo(largeCorner, 0f)
            lineTo(right - groupTopCorner, 0f)
            arcTo(androidx.compose.ui.geometry.Rect(right - 2 * groupTopCorner, 0f, right, 2 * groupTopCorner), 270f, 90f, false)
            lineTo(right, h - tailHeight)
            cubicTo(right, h - tailHeight * 0.3f, size.width, h - tipR * 2f, size.width, h - tipR)
            arcTo(androidx.compose.ui.geometry.Rect(size.width - tipR * 2, h - tipR * 2, size.width, h), 0f, 90f, false)
            lineTo(right, h)
            lineTo(largeCorner, h)
            arcTo(androidx.compose.ui.geometry.Rect(0f, h - 2 * largeCorner, 2 * largeCorner, h), 90f, 90f, false)
            lineTo(0f, largeCorner)
            arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, 2 * largeCorner, 2 * largeCorner), 180f, 90f, false)
        } else {
            val left = tailWidth
            moveTo(left + groupTopCorner, 0f)
            lineTo(size.width - largeCorner, 0f)
            arcTo(androidx.compose.ui.geometry.Rect(size.width - 2 * largeCorner, 0f, size.width, 2 * largeCorner), 270f, 90f, false)
            lineTo(size.width, h - largeCorner)
            arcTo(androidx.compose.ui.geometry.Rect(size.width - 2 * largeCorner, h - 2 * largeCorner, size.width, h), 0f, 90f, false)
            lineTo(left, h)
            lineTo(tipR, h)
            arcTo(androidx.compose.ui.geometry.Rect(0f, h - 2 * tipR, 2 * tipR, h), 90f, 90f, false)
            cubicTo(0f, h - tipR * 2f, left, h - tailHeight * 0.3f, left, h - tailHeight)
            lineTo(left, groupTopCorner)
            arcTo(androidx.compose.ui.geometry.Rect(left, 0f, left + 2 * groupTopCorner, 2 * groupTopCorner), 180f, 90f, false)
        }
        close()
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageRow(
    item: ChatItem.Msg,
    isNewest: Boolean,
    highlighted: Boolean,
    isBeingEdited: Boolean,
    // Message objects mutate internally (status, transfer progress); passing the revision
    // counter down defeats Compose skipping so bubbles re-render on every conversation update.
    revision: Int,
    listener: ConversationScreenListener,
    onLongPress: (Message) -> Unit,
    resolveReply: (String) -> Message?,
    onReplyCardClick: (Message) -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = item.message
    val outgoing = message.status != Message.STATUS_RECEIVED
    val isGroupChat = message.getConversation().getMode() == Conversational.MODE_MULTI
    val showAvatarSlot = !outgoing && isGroupChat

    // Expressive "pop": the newest message springs in.
    val pop = remember(item.key) { Animatable(if (isNewest) 0.8f else 1f) }
    if (isNewest) {
        LaunchedEffect(item.key) {
            pop.animateTo(
                1f,
                animationSpec =
                    androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                    ),
            )
        }
    }

    // Avatar for incoming group-chat messages: load once per sender group (last bubble).
    val context = LocalContext.current
    val avatarBitmap = if (showAvatarSlot && item.lastOfGroup) {
        val avatarState = remember(item.key) { mutableStateOf<ImageBitmap?>(null) }
        val avatarSizePx = with(LocalDensity.current) { 32.dp.toPx() }.toInt()
        LaunchedEffect(item.key) {
            val activity = context as? XmppActivity ?: return@LaunchedEffect
            val bm = withContext(Dispatchers.IO) {
                try { activity.avatarService().get(message, avatarSizePx, false) }
                catch (_: Exception) { null }
            }
            if (bm != null) avatarState.value = bm.asImageBitmap()
        }
        avatarState
    } else null

    // The tail of a group's last bubble pokes into the screen margin so bubble bodies stay
    // aligned with the grouped bubbles above.
    val tailInset = if (item.lastOfGroup) TAIL_WIDTH else 0.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { onLongPress(message) },
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (outgoing) 48.dp else if (showAvatarSlot) 8.dp else 12.dp - tailInset,
                        end = if (outgoing) 12.dp - tailInset else 48.dp,
                        top = if (item.firstOfGroup) 6.dp else 1.dp,
                        bottom = 1.dp,
                    )
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                        transformOrigin =
                            androidx.compose.ui.graphics.TransformOrigin(
                                if (outgoing) 1f else 0f,
                                1f,
                            )
                    },
            horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
        ) {
            if (showAvatarSlot) {
                val bm = avatarBitmap?.value
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bm != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bm,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape),
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            MessageBubble(
                item = item,
                outgoing = outgoing,
                highlighted = highlighted,
                isBeingEdited = isBeingEdited,
                revision = revision,
                listener = listener,
                onLongPress = onLongPress,
                resolveReply = resolveReply,
                onReplyCardClick = onReplyCardClick,
            )
        }
        ReactionChips(
            message = message,
            outgoing = outgoing,
            revision = revision,
            listener = listener,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-4).dp)
                .padding(
                    start = if (outgoing) 48.dp else if (showAvatarSlot) 8.dp + 32.dp + 6.dp else 10.dp,
                    end = if (outgoing) 10.dp else 48.dp,
                ),
        )
    }
}

@Composable
private fun ReactionChips(
    message: Message,
    outgoing: Boolean,
    @Suppress("UNUSED_PARAMETER") revision: Int,
    listener: ConversationScreenListener,
    modifier: Modifier = Modifier,
) {
    val aggregated = message.getAggregatedReactions()
    val canAdd = !outgoing && Restrictions.reactionsPerUserRemaining(message)
    if (aggregated.reactions.isEmpty() && !canAdd) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp, if (outgoing) Alignment.End else Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        aggregated.reactions.forEach { entry ->
            val emoji = entry.key
            val count = entry.value
            val isOurs = emoji in aggregated.ourReactions
            Surface(
                shape = RoundedCornerShape(50),
                color =
                    if (isOurs) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
                modifier =
                    Modifier.combinedClickable(
                        onClick = {
                            val next = aggregated.ourReactions.toMutableSet()
                            if (isOurs) next.remove(emoji) else next.add(emoji)
                            listener.onSendReactions(message, next)
                        },
                        onLongClick = { listener.onShowReactionDetails(message, emoji) },
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(text = emoji, style = MaterialTheme.typography.bodyMedium)
                    if (count > 1) {
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (isOurs) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
        if (canAdd) {
            Surface(
                onClick = { listener.onAddReaction(message) },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_reaction_24dp),
                    contentDescription = stringResource(R.string.add_reaction),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(4.dp).size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    item: ChatItem.Msg,
    outgoing: Boolean,
    highlighted: Boolean,
    isBeingEdited: Boolean,
    revision: Int,
    listener: ConversationScreenListener,
    onLongPress: (Message) -> Unit,
    resolveReply: (String) -> Message?,
    onReplyCardClick: (Message) -> Unit,
) {
    val message = item.message
    val failed = message.status == Message.STATUS_SEND_FAILED
    val baseColor =
        when {
            failed -> MaterialTheme.colorScheme.errorContainer
            outgoing -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val containerColor by
        androidx.compose.animation.animateColorAsState(
            targetValue =
                if (highlighted) MaterialTheme.colorScheme.tertiaryContainer else baseColor,
            label = "bubbleHighlight",
        )
    val contentColor =
        when {
            failed -> MaterialTheme.colorScheme.onErrorContainer
            outgoing -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        }

    val hasTail = item.lastOfGroup
    val blurRadius by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isBeingEdited) 5.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.spring(),
        label = "editingBlur",
    )
    Box {
        Surface(
            shape =
                rememberBubbleShape(
                    firstOfGroup = item.firstOfGroup,
                    lastOfGroup = item.lastOfGroup,
                    outgoing = outgoing,
                ),
            color = containerColor,
            contentColor = contentColor,
            modifier = Modifier
                .widthIn(max = if (hasTail) 320.dp + TAIL_WIDTH else 320.dp)
                .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
        ) {
            Column(
                modifier =
                    Modifier.combinedClickable(
                            onClick = { listener.onOpenMessage(message) },
                            onLongClick = { onLongPress(message) },
                        )
                        .padding(
                            start = if (hasTail && !outgoing) 12.dp + TAIL_WIDTH else 12.dp,
                            end = if (hasTail && outgoing) 12.dp + TAIL_WIDTH else 12.dp,
                            top = 8.dp,
                            bottom = 8.dp,
                        )
            ) {
                val repliedToId = message.getRepliedTo()
                if (repliedToId != null) {
                    val original = remember(repliedToId) { resolveReply(repliedToId) }
                    if (original != null) {
                        ReplyCard(original = original, onClick = { onReplyCardClick(original) })
                    }
                }
                MessageContent(
                    message = message,
                    revision = revision,
                    contentColor = contentColor,
                    onLongPress = { onLongPress(message) },
                    listener = listener,
                )
                if (!isBeingEdited) {
                    MessageFooter(message = message, outgoing = outgoing, revision = revision)
                }
            }
        }
        // Editing indicator: rendered outside the blurred Surface so it stays crisp
        if (isBeingEdited) {
            val infiniteTransition = rememberInfiniteTransition(label = "editingPulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "editingAlpha",
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = stringResource(R.string.editing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
    }
}

@Composable
private fun ReplyCard(original: Message, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        modifier = modifier.fillMaxWidth().padding(bottom = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        ) {
            Box(
                modifier =
                    Modifier.width(3.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
            )
            Column(
                modifier =
                    Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = UIHelper.getMessageDisplayName(original),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = MessageUtils.replyPreview(original),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (original.type == Message.TYPE_IMAGE && !original.isDeleted) {
                val activity = context as? XmppActivity
                val fileBackend = activity?.xmppConnectionService?.fileBackend
                if (fileBackend != null) {
                    val thumb =
                        remember(original.getUuid()) { mutableStateOf<ImageBitmap?>(null) }
                    val sizePx = with(LocalDensity.current) { 40.dp.toPx() }.toInt()
                    LaunchedEffect(original.getUuid()) {
                        val bm =
                            withContext(Dispatchers.IO) {
                                try {
                                    fileBackend.getThumbnail(original, sizePx, false)
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        if (bm != null) thumb.value = bm.asImageBitmap()
                    }
                    val bitmap = thumb.value
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier.padding(end = 6.dp)
                                    .size(40.dp)
                                    // approximates the squircle used by the View reply card
                                    .clip(RoundedCornerShape(28)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageContent(
    message: Message,
    revision: Int,
    contentColor: androidx.compose.ui.graphics.Color,
    onLongPress: () -> Unit,
    listener: ConversationScreenListener,
) {
    val context = LocalContext.current
    val activity = context as? XmppActivity
    val transferable = message.transferable
    // transferable.getProgress() mutates in place; reading `revision` re-triggers this read.
    val transferableProgress = remember(revision) { transferable?.getProgress() }

    when {
        message.isDeleted -> {
            Text(
                text = stringResource(R.string.file_deleted),
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        transferable != null && transferable.getStatus() == Transferable.STATUS_DOWNLOADING -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.receiving_x_file,
                        UIHelper.getFileDescriptionString(context, message),
                        transferableProgress ?: 0),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        transferable != null && transferable.getStatus() == Transferable.STATUS_UPLOADING -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.sending_file, transferableProgress ?: 0),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        message.isFileOrImage &&
            !message.isDeleted &&
            message.mimeType?.startsWith("audio/") == true -> {
            AudioMessageContent(message)
        }
        message.isFileOrImage &&
            message.encryption != Message.ENCRYPTION_PGP &&
            message.encryption != Message.ENCRYPTION_DECRYPTION_FAILED &&
            (MessageUtils.unInitiatedButKnownSize(message) ||
                (transferable != null && transferable.getStatus() != Transferable.STATUS_UPLOADING)) -> {
            val fileDescription = UIHelper.getFileDescriptionString(context, message)
            when {
                MessageUtils.unInitiatedButKnownSize(message) ||
                    transferable?.getStatus() == Transferable.STATUS_OFFER ->
                    FileActionRow(
                        iconRes = R.drawable.ic_download_24dp,
                        label = stringResource(R.string.download_x_file, fileDescription),
                        onClick = { listener.onDownloadMessage(message) },
                    )
                transferable?.getStatus() == Transferable.STATUS_OFFER_CHECK_FILESIZE ->
                    FileActionRow(
                        iconRes = R.drawable.ic_download_24dp,
                        label = stringResource(R.string.check_x_filesize, fileDescription),
                        onClick = { listener.onDownloadMessage(message) },
                    )
                transferable?.getStatus() == Transferable.STATUS_CHECKING ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.checking_x, fileDescription),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                transferable?.getStatus() == Transferable.STATUS_FAILED ->
                    FileActionRow(
                        iconRes = R.drawable.ic_error_24dp,
                        label = stringResource(R.string.file_transmission_failed),
                        tint = MaterialTheme.colorScheme.error,
                        onClick = { listener.onDownloadMessage(message) },
                    )
                transferable?.getStatus() == Transferable.STATUS_CANCELLED ->
                    FileActionRow(
                        iconRes = R.drawable.ic_cancel_24dp,
                        label = stringResource(R.string.file_transmission_cancelled),
                        onClick = { listener.onDownloadMessage(message) },
                    )
                else ->
                    FileActionRow(
                        iconRes = R.drawable.ic_attach_file_24dp,
                        label = fileDescription,
                    )
            }
        }
        message.isFileOrImage &&
            message.encryption != Message.ENCRYPTION_PGP &&
            message.encryption != Message.ENCRYPTION_DECRYPTION_FAILED &&
            message.fileParams.width > 0 && message.fileParams.height > 0 -> {
            val fileBackend = activity?.xmppConnectionService?.fileBackend
            val isVideo = message.mimeType?.startsWith("video/") == true
            if (fileBackend != null) {
                val thumb = remember(message.getUuid()) { mutableStateOf<ImageBitmap?>(null) }
                val sizePx = with(LocalDensity.current) { 280.dp.toPx() }.toInt()
                LaunchedEffect(message.getUuid()) {
                    val bm =
                        withContext(Dispatchers.IO) {
                            try {
                                fileBackend.getThumbnail(message, sizePx, false)
                            } catch (_: Exception) {
                                null
                            }
                        }
                    if (bm != null) thumb.value = bm.asImageBitmap()
                }
                val bitmap = thumb.value
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier.widthIn(max = 280.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = { listener.onOpenMessage(message) },
                                    onLongClick = onLongPress,
                                ),
                    )
                } else {
                    FileActionRow(
                        iconRes = if (isVideo) R.drawable.ic_movie_24dp else R.drawable.ic_image_24dp,
                        label = UIHelper.getFileDescriptionString(context, message),
                        onClick = { listener.onOpenMessage(message) },
                    )
                }
            } else {
                FileActionRow(
                    iconRes = if (isVideo) R.drawable.ic_movie_24dp else R.drawable.ic_image_24dp,
                    label = UIHelper.getFileDescriptionString(context, message),
                    onClick = { listener.onOpenMessage(message) },
                )
            }
        }
        message.isFileOrImage &&
            message.encryption != Message.ENCRYPTION_PGP &&
            message.encryption != Message.ENCRYPTION_DECRYPTION_FAILED -> {
            FileActionRow(
                iconRes = R.drawable.ic_attach_file_24dp,
                label = stringResource(R.string.open_x_file, UIHelper.getFileDescriptionString(context, message)),
                onClick = { listener.onOpenMessage(message) },
            )
        }
        message.isGeoUri -> {
            FileRow(
                iconRes = R.drawable.ic_location_pin_24dp,
                label = stringResource(R.string.location),
            )
        }
        message.treatAsDownloadable() -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { listener.onDownloadMessage(message) },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_download_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = UIHelper.getMessagePreview(context, message).first.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        message.type == Message.TYPE_RTP_SESSION -> {
            FileRow(
                iconRes = R.drawable.ic_call_24dp,
                label = UIHelper.getMessagePreview(context, message).first.toString(),
            )
        }
        message.encryption == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE -> {
            Text(
                text = stringResource(R.string.not_encrypted_for_this_device),
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        message.encryption == Message.ENCRYPTION_AXOLOTL_FAILED -> {
            Text(
                text = stringResource(R.string.omemo_decryption_failed),
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        message.encryption == Message.ENCRYPTION_DECRYPTION_FAILED -> {
            Text(
                text = stringResource(R.string.decryption_failed),
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        message.bodyIsOnlyEmojis() && message.type != Message.TYPE_PRIVATE -> {
            val emojiText = message.body?.trim() ?: ""
            val scale = if (eu.siacs.conversations.utils.Emoticons.isEmoji(emojiText)) 3.0f else 2.0f
            Text(
                text = emojiText,
                fontSize = (16f * scale).sp,
                lineHeight = (16f * scale * 1.2f).sp,
            )
        }
        else -> {
            LinkifiedMessageText(
                message = message,
                revision = revision,
                contentColor = contentColor,
                onLongPress = onLongPress,
            )
        }
    }
}

private fun formatAudioTime(millis: Int): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** Inline audio player: play/pause button, seek bar and time, all inside the bubble. */
@Composable
private fun AudioMessageContent(message: Message) {
    val context = LocalContext.current
    val activity = context as? XmppActivity
    val file =
        remember(message.getUuid()) {
            try {
                activity?.xmppConnectionService?.fileBackend?.getFile(message)
            } catch (_: Exception) {
                null
            }
        }
    if (file == null || !file.exists()) {
        FileRow(
            iconRes = R.drawable.ic_mic_24dp,
            label = UIHelper.getFileDescriptionString(context, message),
        )
        return
    }

    val uuid = message.getUuid() ?: return
    val playing = AudioPlaybackController.activeUuid == uuid && AudioPlaybackController.isPlaying
    var tick by remember(uuid) { mutableIntStateOf(0) }
    val positionMs = remember(uuid, tick) { AudioPlaybackController.positionFor(uuid) }
    val durationMs =
        AudioPlaybackController.durations[uuid] ?: (message.fileParams?.runtime ?: 0)

    LaunchedEffect(uuid) {
        AudioPlaybackController.onRowEnteredComposition(uuid, file)
    }

    androidx.compose.runtime.DisposableEffect(uuid) {
        onDispose { AudioPlaybackController.onRowLeftComposition(uuid) }
    }

    LaunchedEffect(playing) {
        while (playing) {
            tick++
            kotlinx.coroutines.delay(250)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(232.dp)) {
        FilledIconButton(
            onClick = { AudioPlaybackController.toggle(uuid, file) },
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter =
                    painterResource(
                        if (playing) R.drawable.ic_pause_24dp else R.drawable.ic_play_arrow_24dp
                    ),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        androidx.compose.material3.Slider(
            value =
                if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
            onValueChange = { fraction ->
                val target = (fraction * durationMs).toInt()
                AudioPlaybackController.seekTo(uuid, file, target)
                tick++
            },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(
            text =
                formatAudioTime(
                    if (playing || positionMs > 0) positionMs else durationMs
                ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FileRow(iconRes: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Material 3 Expressive pill-shaped row for file/transfer affordances inside a bubble. */
@Composable
private fun FileActionRow(
    iconRes: Int,
    label: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = tint)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.MessageFooter(
    message: Message,
    outgoing: Boolean,
    revision: Int,
) {
    // Message is a mutated-in-place Java entity; reading `revision` here is what
    // makes Compose re-read message.status after an in-place status change.
    val status = remember(revision) { message.status }
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
    ) {
        Text(
            text = DateUtils.formatDateTime(context, message.timeSent, DateUtils.FORMAT_SHOW_TIME),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (message.edited()) {
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_edit_24dp),
                contentDescription = stringResource(R.string.correct_message),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
        if (message.encryption != Message.ENCRYPTION_NONE) {
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_lock_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
        if (outgoing && message.type != Message.TYPE_RTP_SESSION) {
            val statusDrawable = MessageAdapter.getMessageStatusAsDrawable(message, status)
            if (statusDrawable != null) {
                val displayed = status == Message.STATUS_SEND_DISPLAYED
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter =
                        painterResource(
                            if (displayed) R.drawable.ic_done_all_bold_24dp else statusDrawable
                        ),
                    contentDescription = null,
                    tint =
                        if (displayed) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** One entry of the long-press context sheet. */
private class SheetAction(
    val iconRes: Int,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Long-press message menu: an M3 Expressive bottom sheet whose actions are rendered as a
 * grouped list — large outer corners, tight inner corners — matching the bubble language.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MessageContextSheet(
    message: Message,
    state: ConversationScreenState,
    listener: ConversationScreenListener,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val conversation = state.conversation.value
    val deleted = message.isDeleted
    val transferable = message.transferable
    val receiving = message.status == Message.STATUS_RECEIVED
            && (transferable is eu.siacs.conversations.xmpp.jingle.JingleFileTransferConnection
                || transferable is eu.siacs.conversations.http.HttpDownloadConnection)
    val waitingOrOffered = message.status == Message.STATUS_WAITING
            || message.status == Message.STATUS_UNSEND
            || message.status == Message.STATUS_OFFERED
    val cancelable = (transferable != null && !deleted) || (waitingOrOffered && message.needsUploading())
    val fileDescription = UIHelper.getFileDescriptionString(context, message)
    val actions = buildList {
        // Reply
        add(
            SheetAction(R.drawable.ic_reply_24dp, stringResource(R.string.reply)) {
                state.correcting.value = null
                state.replyingTo.value = message
            }
        )
        // Copy text
        val body = message.body
        if (!body.isNullOrBlank() && !message.isFileOrImage && !deleted) {
            add(
                SheetAction(
                    R.drawable.ic_description_24dp,
                    stringResource(android.R.string.copy),
                ) {
                    val clipboard =
                        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("message", body)
                    )
                    android.widget.Toast.makeText(
                            context,
                            R.string.message_copied_to_clipboard,
                            android.widget.Toast.LENGTH_SHORT,
                        )
                        .show()
                }
            )
            // Copy link — first URL found in body
            val firstLink = de.gultsch.common.Linkify.getLinks(body).firstOrNull()
            if (firstLink != null) {
                val copyLinkLabel = when (firstLink.scheme) {
                    "xmpp" -> stringResource(R.string.copy_jabber_id)
                    "http", "https", "gemini" -> stringResource(R.string.copy_link)
                    "geo" -> stringResource(R.string.copy_geo_uri)
                    "tel" -> stringResource(R.string.copy_telephone_number)
                    "mailto" -> stringResource(R.string.copy_email_address)
                    else -> stringResource(R.string.copy_URI)
                }
                add(SheetAction(R.drawable.ic_link_24dp, copyLinkLabel) {
                    listener.onCopyLink(message)
                })
            }
        }
        // Correct/edit — allowed on any sent text message, not just the last one
        if (message.isEditable && !message.isFileOrImage && !deleted) {
            add(
                SheetAction(R.drawable.ic_edit_24dp, stringResource(R.string.correct_message)) {
                    state.replyingTo.value = null
                    state.correcting.value = message
                    state.setInput(message.body ?: "")
                    listener.onEditingStarted(message)
                }
            )
        }
        // Open file
        if (message.isFileOrImage && !deleted) {
            add(
                SheetAction(
                    R.drawable.ic_attach_file_24dp,
                    stringResource(R.string.open_x_file, fileDescription),
                ) {
                    listener.onOpenMessage(message)
                }
            )
        }
        // Download file (deleted locally but still on remote host)
        if (message.isFileOrImage && deleted && message.hasFileOnRemoteHost()) {
            add(
                SheetAction(
                    R.drawable.ic_download_24dp,
                    stringResource(R.string.download_x_file, fileDescription),
                ) {
                    listener.onDownloadMessage(message)
                }
            )
        } else if (!message.isFileOrImage && message.treatAsDownloadable()) {
            add(
                SheetAction(
                    R.drawable.ic_download_24dp,
                    stringResource(R.string.download_x_file, fileDescription),
                ) {
                    listener.onDownloadMessage(message)
                }
            )
        }
        // Copy URL (remote download URL for file attachments)
        if (message.encryption == Message.ENCRYPTION_NONE
            && (message.hasFileOnRemoteHost() || message.treatAsDownloadable())
        ) {
            add(SheetAction(R.drawable.ic_link_24dp, stringResource(R.string.copy_url)) {
                listener.onCopyUrl(message)
            })
        }
        // Save file to shared storage
        if (message.isFileOrImage && !deleted && !cancelable) {
            val path = message.getRelativeFilePath()
            if (path != null && !path.sharedStorage()) {
                add(SheetAction(R.drawable.ic_save_24dp, stringResource(R.string.save_file)) {
                    listener.onSaveFile(message)
                })
            }
        }
        // Share
        val shareable = (message.isFileOrImage && !deleted && !receiving)
                || (!message.isFileOrImage && !message.treatAsDownloadable() && transferable == null && !deleted)
        if (shareable) {
            add(SheetAction(R.drawable.ic_share_24dp, stringResource(R.string.share_with)) {
                listener.onShareMessage(message)
            })
        }
        // Add reaction
        if (message.status != Message.STATUS_SEND_FAILED
            && !deleted
            && Restrictions.reactionsPerUserRemaining(message)
        ) {
            add(
                SheetAction(R.drawable.ic_add_reaction_24dp, stringResource(R.string.add_reaction)) {
                    listener.onAddReaction(message)
                }
            )
        }
        // Send again (failed outgoing message)
        if (message.status == Message.STATUS_SEND_FAILED && !deleted) {
            add(SheetAction(R.drawable.ic_refresh_24dp, stringResource(R.string.send_again)) {
                listener.onResendMessage(message)
            })
        }
        // Cancel in-progress upload/download
        if (cancelable) {
            add(SheetAction(R.drawable.ic_cancel_24dp, stringResource(R.string.cancel_transmission)) {
                listener.onCancelTransmission(message)
            })
        }
        // Pin / Unpin
        if (message.type != Message.TYPE_STATUS && message.type != Message.TYPE_RTP_SESSION && !deleted) {
            if (message.isPinned) {
                add(SheetAction(R.drawable.ic_push_pin_24dp, stringResource(R.string.unpin_message)) {
                    listener.onUnpinMessage(message)
                })
            } else {
                add(SheetAction(R.drawable.ic_push_pin_24dp, stringResource(R.string.pin_message)) {
                    listener.onPinMessage(message)
                })
            }
        }
        // Delete
        val deleteLabel = when {
            deleted -> stringResource(R.string.delete_leftover_message)
            message.isFileOrImage -> stringResource(R.string.delete_x_file, fileDescription)
            else -> stringResource(R.string.delete_message)
        }
        add(SheetAction(R.drawable.ic_delete_24dp, deleteLabel) {
            state.deleteTarget.value = message
        })
    }

    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = screenHeight * 2f / 3f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.message_options),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
            // Show send-failure error detail at the top of the sheet
            val errorMessage = if (message.status == Message.STATUS_SEND_FAILED) {
                message.errorMessage
            } else null
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(CORNER_LARGE),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_error_24dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            actions.forEachIndexed { index, action ->
                val top = if (index == 0) CORNER_LARGE else CORNER_SMALL
                val bottom = if (index == actions.lastIndex) CORNER_LARGE else CORNER_SMALL
                Surface(
                    onClick = {
                        onDismiss()
                        action.onClick()
                    },
                    shape =
                        RoundedCornerShape(
                            topStart = top,
                            topEnd = top,
                            bottomStart = bottom,
                            bottomEnd = bottom,
                        ),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Icon(
                            painter = painterResource(action.iconRes),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(text = action.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DeleteMessageSheet(
    message: Message,
    onDeleteForEveryone: () -> Unit,
    onDeleteForMyself: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canRetract = message.status != Message.STATUS_RECEIVED
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.delete_message_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
            Surface(
                onClick = onDeleteForEveryone,
                enabled = canRetract,
                shape = RoundedCornerShape(topStart = CORNER_LARGE, topEnd = CORNER_LARGE, bottomStart = CORNER_SMALL, bottomEnd = CORNER_SMALL),
                color = if (canRetract) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24dp),
                        contentDescription = null,
                        tint = if (canRetract) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.delete_for_everyone),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (canRetract) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
            }
            Surface(
                onClick = onDeleteForMyself,
                shape = RoundedCornerShape(topStart = CORNER_SMALL, topEnd = CORNER_SMALL, bottomStart = CORNER_LARGE, bottomEnd = CORNER_LARGE),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.delete_for_myself),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ComposerBanner(state: ConversationScreenState, listener: ConversationScreenListener) {
    // Revision read keeps this banner in sync with nextCounterpart changes.
    val revision = state.revision.intValue
    val conversation = state.conversation.value
    val nextCounterpart = remember(conversation, revision) { conversation?.getNextCounterpart() }
    if (nextCounterpart != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lock_open_outline_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text =
                    stringResource(
                        R.string.send_private_message_to,
                        nextCounterpart.resource ?: "",
                    ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    conversation?.setNextCounterpart(null)
                    state.revision.intValue++
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close_24dp),
                    contentDescription = stringResource(R.string.cancel),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
    val replyingTo = state.replyingTo.value
    val correcting = state.correcting.value
    if (replyingTo == null && correcting == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
    ) {
        Icon(
            painter =
                painterResource(
                    if (correcting != null) R.drawable.ic_edit_24dp else R.drawable.ic_reply_24dp
                ),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    if (correcting != null) stringResource(R.string.send_corrected_message)
                    else UIHelper.getMessageDisplayName(replyingTo!!),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = MessageUtils.replyPreview(correcting ?: replyingTo!!),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = {
                val wasEditing = state.correcting.value
                if (wasEditing != null) {
                    state.setInput("")
                    listener.onEditingStopped(wasEditing)
                }
                state.replyingTo.value = null
                state.correcting.value = null
            }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close_24dp),
                contentDescription = stringResource(R.string.cancel),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Staged attachments waiting for the send button, with per-item remove. */
@Composable
private fun AttachmentPreviewStrip(state: ConversationScreenState) {
    val context = LocalContext.current
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        itemsIndexed(
            state.attachments,
            key = { _, attachment -> attachment.uuid.toString() },
        ) { _, attachment ->
            Box {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(72.dp),
                ) {
                    val isImage =
                        attachment.type == eu.siacs.conversations.ui.util.Attachment.Type.IMAGE
                    if (isImage) {
                        val thumb =
                            remember(attachment.uuid) { mutableStateOf<ImageBitmap?>(null) }
                        LaunchedEffect(attachment.uuid) {
                            val bm =
                                withContext(Dispatchers.IO) {
                                    try {
                                        context.contentResolver.loadThumbnail(
                                            attachment.uri,
                                            android.util.Size(144, 144),
                                            null,
                                        )
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            if (bm != null) thumb.value = bm.asImageBitmap()
                        }
                        val bitmap = thumb.value
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (attachment.type ==
                                            eu.siacs.conversations.ui.util.Attachment.Type
                                                .RECORDING
                                        )
                                            R.drawable.ic_mic_24dp
                                        else R.drawable.ic_attach_file_24dp
                                    ),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Surface(
                    onClick = { state.attachments.remove(attachment) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close_24dp),
                        contentDescription = stringResource(R.string.cancel),
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkifiedMessageText(
    message: Message,
    revision: Int,
    contentColor: androidx.compose.ui.graphics.Color,
    onLongPress: () -> Unit,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val xmppBg = MaterialTheme.colorScheme.tertiaryContainer
    val xmppFg = MaterialTheme.colorScheme.onTertiaryContainer
    val context = LocalContext.current
    val msgKey = message.getUuid() ?: System.identityHashCode(message)
    val annotated = remember(msgKey, revision, contentColor.value, linkColor.value, xmppBg.value, xmppFg.value) {
        buildAnnotatedBody(context, message, contentColor, linkColor, xmppBg, xmppFg)
    }
    androidx.compose.foundation.text.BasicText(
        text = annotated,
        style = androidx.compose.ui.text.TextStyle(
            fontSize = 16.sp,
            color = contentColor,
        ),
        modifier = Modifier.pointerInput(onLongPress) {
            detectTapGestures(onLongPress = { onLongPress() })
        },
    )
}

private fun buildAnnotatedBody(
    context: android.content.Context,
    message: Message,
    contentColor: androidx.compose.ui.graphics.Color,
    linkColor: androidx.compose.ui.graphics.Color,
    xmppBg: androidx.compose.ui.graphics.Color,
    xmppFg: androidx.compose.ui.graphics.Color,
): androidx.compose.ui.text.AnnotatedString {
    val rawBody = message.body?.trim() ?: ""
    return try {
        val body = android.text.SpannableStringBuilder()
        val displayName = eu.siacs.conversations.utils.UIHelper.getMessageDisplayName(message)
        if (message.hasMeCommand() && displayName != null) {
            val replaced = displayName + " " + rawBody.removePrefix(eu.siacs.conversations.entities.Message.ME_COMMAND)
            body.append(replaced)
            body.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD_ITALIC),
                0, displayName.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        } else {
            body.append(rawBody)
            if (message.isPrivateMessage()) {
                val prefix = if (message.status <= eu.siacs.conversations.entities.Message.STATUS_RECEIVED) {
                    context.getString(eu.siacs.conversations.R.string.private_message) + " "
                } else {
                    val cp = message.counterpart
                    context.getString(eu.siacs.conversations.R.string.private_message_to, cp?.resource ?: "") + " "
                }
                body.insert(0, prefix)
                body.setSpan(
                    android.text.style.ForegroundColorSpan(eu.siacs.conversations.utils.UIHelper.getColorForName(prefix)),
                    0, prefix.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                body.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0, prefix.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        val emojiMatcher = eu.siacs.conversations.utils.Emoticons.getEmojiPattern(body).matcher(body)
        while (emojiMatcher.find()) {
            if (emojiMatcher.start() < emojiMatcher.end()) {
                body.setSpan(
                    android.text.style.RelativeSizeSpan(1.2f),
                    emojiMatcher.start(), emojiMatcher.end(),
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        if (message.conversation.getMode() == eu.siacs.conversations.entities.Conversational.MODE_MULTI
            && message.status == eu.siacs.conversations.entities.Message.STATUS_RECEIVED
            && message.conversation is eu.siacs.conversations.entities.Conversation
        ) {
            val conv = message.conversation as eu.siacs.conversations.entities.Conversation
            val nick = conv.mucOptions?.getActualNick()
            if (!nick.isNullOrEmpty()) {
                val pattern = eu.siacs.conversations.services.NotificationService
                    .generateNickHighlightPattern(nick)
                val matcher = pattern.matcher(body)
                while (matcher.find()) {
                    body.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        matcher.start(), matcher.end(),
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
        }
        eu.siacs.conversations.utils.StylingHelper.format(body, contentColor.toArgb())
        de.gultsch.common.Linkify.addLinks(body)
        spannableToAnnotated(body, linkColor, xmppBg, xmppFg, context)
    } catch (_: Exception) {
        androidx.compose.ui.text.AnnotatedString(rawBody)
    }
}

private fun spannableToAnnotated(
    spannable: android.text.SpannableStringBuilder,
    linkColor: androidx.compose.ui.graphics.Color,
    xmppBg: androidx.compose.ui.graphics.Color,
    xmppFg: androidx.compose.ui.graphics.Color,
    context: android.content.Context,
): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        append(spannable.toString())
        for (span in spannable.getSpans(0, spannable.length, Any::class.java)) {
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            if (start < 0 || end < 0 || start >= end) continue
            when (span) {
                is android.text.style.StyleSpan -> {
                    val style = when (span.style) {
                        android.graphics.Typeface.BOLD ->
                            androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        android.graphics.Typeface.ITALIC ->
                            androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic)
                        android.graphics.Typeface.BOLD_ITALIC ->
                            androidx.compose.ui.text.SpanStyle(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                            )
                        else -> null
                    }
                    if (style != null) addStyle(style, start, end)
                }
                is android.text.style.StrikethroughSpan ->
                    addStyle(androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough), start, end)
                is android.text.style.TypefaceSpan ->
                    if (span.family == "monospace") addStyle(
                        androidx.compose.ui.text.SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        start, end,
                    )
                is android.text.style.ForegroundColorSpan ->
                    addStyle(androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color(span.foregroundColor)), start, end)
                is android.text.style.RelativeSizeSpan ->
                    addStyle(androidx.compose.ui.text.SpanStyle(fontSize = (16f * span.sizeChange).sp), start, end)
                is android.text.style.URLSpan -> {
                    val url = span.url
                    val isXmpp = url.startsWith("xmpp:")
                    val baseStyle = if (isXmpp) {
                        // XMPP URIs get chip-like styling: tertiary container background
                        // signals "this opens inside the app" vs a browser link.
                        androidx.compose.ui.text.SpanStyle(
                            color = xmppFg,
                            background = xmppBg,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        androidx.compose.ui.text.SpanStyle(
                            color = linkColor,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        )
                    }
                    addStyle(baseStyle, start, end)
                    addLink(
                        androidx.compose.ui.text.LinkAnnotation.Clickable(
                            tag = url,
                            styles = androidx.compose.ui.text.TextLinkStyles(style = baseStyle),
                            linkInteractionListener = { openUrl(context, url) },
                        ),
                        start, end,
                    )
                }
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    val uri = try {
        de.gultsch.common.MiniUri.asMiniUri(url)
    } catch (_: Exception) {
        return
    }
    val asXmpp = when {
        uri is de.gultsch.common.MiniUri.Xmpp -> uri
        uri is de.gultsch.common.MiniUri.Transformable && uri.transform() is de.gultsch.common.MiniUri.Xmpp ->
            uri.transform() as de.gultsch.common.MiniUri.Xmpp
        else -> null
    }
    if (asXmpp != null && asXmpp.isAddress) {
        if (context is ConversationsActivity && context.onXmppUriClicked(asXmpp)) return
        try {
            context.startActivity(
                android.content.Intent(context, eu.siacs.conversations.ui.YuriLauncherActivity::class.java)
                    .apply { data = asXmpp.asUri() }
            )
        } catch (_: android.content.ActivityNotFoundException) {}
        return
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
    if ("web+ap" == uri.scheme) {
        if (intent.resolveActivity(context.packageManager) == null) {
            val https = android.net.Uri.parse(
                "https://${uri.authority}/${com.google.common.base.Joiner.on('/').join(uri.pathSegments)}"
            )
            try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, https)) } catch (_: Exception) {}
            return
        }
    }
    if ("geo" == uri.scheme) intent.setClass(context, eu.siacs.conversations.ui.ShowLocationActivity::class.java)
    else intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT
    try {
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, eu.siacs.conversations.R.string.no_application_found_to_open_link, android.widget.Toast.LENGTH_SHORT).show()
    }
}


@Composable
private fun RecordingBar(
    recording: RecordingUiState.Active,
    listener: ConversationScreenListener,
) {
    val minutes = (recording.elapsedMs / 60000).toInt()
    val seconds = ((recording.elapsedMs % 60000) / 1000).toInt()
    val timerText = String.format("%02d:%02d", minutes, seconds)
    val infiniteTransition = rememberInfiniteTransition(label = "recDot")
    val dotAlpha by if (!recording.paused) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.2f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "recDotAlpha",
        )
    } else {
        androidx.compose.runtime.remember { mutableStateOf(0.2f) }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .height(48.dp),
    ) {
        IconButton(onClick = { listener.onCancelRecording() }) {
            Icon(
                painter = painterResource(R.drawable.ic_delete_24dp),
                contentDescription = stringResource(R.string.cancel),
                tint = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier =
                Modifier.size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = dotAlpha))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = timerText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { listener.onPauseRecording() }) {
            Icon(
                painter =
                    painterResource(
                        if (recording.paused) R.drawable.ic_play_arrow_24dp
                        else R.drawable.ic_pause_24dp
                    ),
                contentDescription = null,
            )
        }
        FilledIconButton(
            onClick = { listener.onSendRecording() },
            shape = RoundedCornerShape(16.dp),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_send_24dp),
                contentDescription = stringResource(R.string.send_message),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InputBar(state: ConversationScreenState, listener: ConversationScreenListener) {
    val context = LocalContext.current
    val conversation = state.conversation.value
    var attachMenuOpen by remember { mutableStateOf(false) }
    val text = state.inputText.value
    val hasText = text.isNotBlank()
    val correcting = state.correcting.value != null
    val hasAttachments = state.attachments.isNotEmpty()
    val recording = state.recordingState.value

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
        ComposerBanner(state, listener)
        if (hasAttachments) {
            AttachmentPreviewStrip(state)
        }
        if (recording is RecordingUiState.Active) {
            RecordingBar(recording, listener)
        } else {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            // Attach button → toolbar container transform.
            // AnimatedContent + SizeTransform give a spring-driven horizontal expand from the
            // button position: the button's container grows rightward into a pill-shaped
            // toolbar with M3 Expressive spring physics, then shrinks back on dismiss.
            AnimatedContent(
                targetState = attachMenuOpen,
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter =
                            expandHorizontally(
                                expandFrom = Alignment.Start,
                                animationSpec =
                                    spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                            ) + fadeIn(),
                        initialContentExit =
                            shrinkHorizontally(
                                shrinkTowards = Alignment.Start,
                                animationSpec =
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                            ) + fadeOut(),
                        sizeTransform = SizeTransform(clip = false),
                    )
                },
                label = "attachTransform",
            ) { isOpen ->
                if (isOpen) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        ) {
                            IconButton(
                                onClick = {
                                    attachMenuOpen = false
                                    listener.onAttachImage()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_image_24dp),
                                    contentDescription =
                                        stringResource(R.string.attachment_choice_gallery),
                                )
                            }
                            IconButton(
                                onClick = {
                                    attachMenuOpen = false
                                    listener.onTakePhoto()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_camera_alt_24dp),
                                    contentDescription =
                                        stringResource(R.string.attachment_choice_camera),
                                )
                            }
                            IconButton(
                                onClick = {
                                    attachMenuOpen = false
                                    listener.onAttachFile()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_attach_file_24dp),
                                    contentDescription =
                                        stringResource(R.string.attachment_choice_file),
                                )
                            }
                            IconButton(onClick = { attachMenuOpen = false }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close_24dp),
                                    contentDescription = stringResource(R.string.cancel),
                                )
                            }
                        }
                    }
                } else {
                    IconButton(onClick = { attachMenuOpen = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_attach_file_24dp),
                            contentDescription = stringResource(R.string.attach_file),
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (text.isEmpty()) {
                        Text(
                            text =
                                if (correcting) stringResource(R.string.send_corrected_message)
                                else
                                    conversation?.let { UIHelper.getMessageHint(context, it) }
                                        ?: stringResource(R.string.send_message),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            state.setInput(it)
                            listener.onInputChanged(it)
                        },
                        textStyle =
                            LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                        cursorBrush =
                            androidx.compose.ui.graphics.SolidColor(
                                MaterialTheme.colorScheme.primary
                            ),
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Morphing send button: rounded square at rest, springs to a pill once there
            // is something to send.
            val corner by
                androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (hasText) 24.dp else 14.dp,
                    animationSpec =
                        androidx.compose.animation.core.spring(
                            dampingRatio =
                                androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                        ),
                    label = "sendCorner",
                )
            val showMic = !hasText && !correcting && !hasAttachments
            FilledIconButton(
                onClick = {
                    when {
                        hasAttachments -> listener.onCommitAttachments()
                        showMic -> listener.onStartRecording()
                        hasText -> listener.onSendTextMessage(text.trim())
                    }
                },
                enabled = showMic || hasText || hasAttachments,
                shape = RoundedCornerShape(corner),
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor =
                            if (showMic) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.primary,
                        contentColor =
                            if (showMic) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onPrimary,
                    ),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter =
                        painterResource(
                            when {
                                showMic -> R.drawable.ic_mic_24dp
                                correcting -> R.drawable.ic_done_24dp
                                conversation != null &&
                                    conversation.nextEncryption != Message.ENCRYPTION_NONE ->
                                    R.drawable.ic_send_encrypted_24dp
                                else -> R.drawable.ic_send_24dp
                            }
                        ),
                    contentDescription =
                        stringResource(
                            if (showMic) R.string.attachment_choice_recording
                            else R.string.send_message
                        ),
                )
            }
        }
        } // end else (not recording)
        }
    }
}
