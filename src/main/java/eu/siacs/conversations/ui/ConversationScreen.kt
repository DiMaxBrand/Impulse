package eu.siacs.conversations.ui

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.entities.Transferable
import eu.siacs.conversations.ui.adapter.MessageAdapter
import eu.siacs.conversations.utils.MessageUtils
import eu.siacs.conversations.utils.UIHelper
import im.conversations.android.xmpp.model.stanza.Presence
import im.conversations.android.xmpp.model.state.Composing
import eu.siacs.conversations.xmpp.manager.ChatStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Observable state for the Compose conversation screen. */
class ConversationScreenState {
    internal val conversation = mutableStateOf<Conversation?>(null)
    internal val messages: SnapshotStateList<Message> = mutableStateListOf()
    internal val revision = mutableIntStateOf(0)
    internal val unreadCount = mutableIntStateOf(0)
    internal val inputText = mutableStateOf("")
    internal val replyingTo = mutableStateOf<Message?>(null)
    internal val correcting = mutableStateOf<Message?>(null)

    fun update(conversation: Conversation?, source: List<Message>) {
        this.conversation.value = conversation
        messages.clear()
        messages.addAll(source)
        unreadCount.intValue = conversation?.unreadCount() ?: 0
        revision.intValue++
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

    fun onAttachFile()

    fun onCall(video: Boolean)

    fun onOpenDetails()

    fun onOpenMessage(message: Message)

    fun onDownloadMessage(message: Message)

    fun onLoadMoreMessages()

    fun onScrolledToBottom()

    fun onRecordVoice()
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
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_horiz_24dp),
                    contentDescription = null,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (isSingle) R.string.action_contact_details
                                else R.string.action_muc_details
                            )
                        )
                    },
                    onClick = {
                        menuOpen = false
                        listener.onOpenDetails()
                    },
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
    val items = remember(revision) { buildChatItems(state.messages) }
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

    // Resolves the message a reply refers to, by stanza id or uuid.
    val resolveReply: (String) -> Message? =
        remember(revision) {
            { id ->
                state.messages.lastOrNull { m ->
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
                val itemModifier = Modifier.animateItem()
                when (item) {
                    is ChatItem.DatePill ->
                        DatePill(timestamp = item.timestamp, modifier = itemModifier)
                    is ChatItem.Msg ->
                        MessageRow(
                            item = item,
                            isNewest = index == 0,
                            highlighted = item.key == highlightKey,
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
        // The tail sits on the sender's side: end for outgoing, start for incoming.
        val tailOnRight = outgoing != rtl
        val left = if (tailOnRight) 0f else tailWidth
        val right = if (tailOnRight) size.width - tailWidth else size.width
        val h = size.height
        val topLeft = if (tailOnRight) largeCorner else groupTopCorner
        val topRight = if (tailOnRight) groupTopCorner else largeCorner
        val bottomLeft = if (tailOnRight) largeCorner else 0f
        val bottomRight = if (tailOnRight) 0f else largeCorner
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                rect = androidx.compose.ui.geometry.Rect(left, 0f, right, h),
                topLeft = androidx.compose.ui.geometry.CornerRadius(topLeft),
                topRight = androidx.compose.ui.geometry.CornerRadius(topRight),
                bottomRight = androidx.compose.ui.geometry.CornerRadius(bottomRight),
                bottomLeft = androidx.compose.ui.geometry.CornerRadius(bottomLeft),
            )
        )
        // Curled tail flowing out of the square bottom corner.
        if (tailOnRight) {
            moveTo(right, h - tailHeight)
            quadraticTo(right, h - tailHeight / 4f, size.width, h)
            lineTo(right - tailWidth, h)
            close()
        } else {
            moveTo(left, h - tailHeight)
            quadraticTo(left, h - tailHeight / 4f, 0f, h)
            lineTo(left + tailWidth, h)
            close()
        }
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageRow(
    item: ChatItem.Msg,
    isNewest: Boolean,
    highlighted: Boolean,
    listener: ConversationScreenListener,
    onLongPress: (Message) -> Unit,
    resolveReply: (String) -> Message?,
    onReplyCardClick: (Message) -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = item.message
    val outgoing = message.status != Message.STATUS_RECEIVED

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

    // The tail of a group's last bubble pokes into the screen margin so bubble bodies stay
    // aligned with the grouped bubbles above.
    val tailInset = if (item.lastOfGroup) TAIL_WIDTH else 0.dp
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = if (outgoing) 48.dp else 12.dp - tailInset,
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
        MessageBubble(
            item = item,
            outgoing = outgoing,
            highlighted = highlighted,
            listener = listener,
            onLongPress = onLongPress,
            resolveReply = resolveReply,
            onReplyCardClick = onReplyCardClick,
        )
    }
}

@Composable
private fun MessageBubble(
    item: ChatItem.Msg,
    outgoing: Boolean,
    highlighted: Boolean,
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
    Surface(
        shape =
            rememberBubbleShape(
                firstOfGroup = item.firstOfGroup,
                lastOfGroup = item.lastOfGroup,
                outgoing = outgoing,
            ),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.widthIn(max = if (hasTail) 320.dp + TAIL_WIDTH else 320.dp),
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
            MessageContent(message = message, listener = listener)
            MessageFooter(message = message, outgoing = outgoing)
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
private fun MessageContent(message: Message, listener: ConversationScreenListener) {
    val context = LocalContext.current
    val activity = context as? XmppActivity
    val transferable = message.transferable

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
                        transferable.getProgress()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        message.isFileOrImage -> {
            val fileBackend = activity?.xmppConnectionService?.fileBackend
            val isImage = message.type == Message.TYPE_IMAGE
            if (isImage && fileBackend != null) {
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
                                .clickable { listener.onOpenMessage(message) },
                    )
                } else {
                    FileRow(
                        iconRes = R.drawable.ic_image_24dp,
                        label = UIHelper.getFileDescriptionString(context, message),
                    )
                }
            } else {
                FileRow(
                    iconRes = R.drawable.ic_attach_file_24dp,
                    label = UIHelper.getFileDescriptionString(context, message),
                )
            }
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
        else -> {
            Text(
                text = message.body?.trim() ?: "",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
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

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.MessageFooter(
    message: Message,
    outgoing: Boolean,
) {
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
            val statusDrawable = MessageAdapter.getMessageStatusAsDrawable(message, message.status)
            if (statusDrawable != null) {
                val displayed = message.status == Message.STATUS_SEND_DISPLAYED
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter =
                        painterResource(
                            if (displayed) R.drawable.ic_done_all_bold_24dp else statusDrawable
                        ),
                    contentDescription = null,
                    tint =
                        if (displayed) MaterialTheme.colorScheme.primary
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
    val actions = buildList {
        add(
            SheetAction(R.drawable.ic_reply_24dp, stringResource(R.string.reply)) {
                state.correcting.value = null
                state.replyingTo.value = message
            }
        )
        val body = message.body
        if (!body.isNullOrBlank() && !message.isFileOrImage) {
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
        }
        val editable = conversation?.getLastEditableMessage()
        if (editable != null && editable.getUuid() == message.getUuid()) {
            add(
                SheetAction(R.drawable.ic_edit_24dp, stringResource(R.string.correct_message)) {
                    state.replyingTo.value = null
                    state.correcting.value = message
                    state.setInput(message.body ?: "")
                }
            )
        }
        val fileDescription = UIHelper.getFileDescriptionString(context, message)
        if (message.isFileOrImage && !message.isDeleted) {
            add(
                SheetAction(
                    R.drawable.ic_attach_file_24dp,
                    stringResource(R.string.open_x_file, fileDescription),
                ) {
                    listener.onOpenMessage(message)
                }
            )
        } else if (message.treatAsDownloadable()) {
            add(
                SheetAction(
                    R.drawable.ic_download_24dp,
                    stringResource(R.string.download_x_file, fileDescription),
                ) {
                    listener.onDownloadMessage(message)
                }
            )
        }
    }

    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.message_options),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
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

@Composable
private fun ComposerBanner(state: ConversationScreenState) {
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
                if (state.correcting.value != null) state.setInput("")
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InputBar(state: ConversationScreenState, listener: ConversationScreenListener) {
    val context = LocalContext.current
    val conversation = state.conversation.value
    var attachMenuOpen by remember { mutableStateOf(false) }
    val text = state.inputText.value
    val hasText = text.isNotBlank()
    val correcting = state.correcting.value != null

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
        ComposerBanner(state)
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Box {
                IconButton(onClick = { attachMenuOpen = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_attach_file_24dp),
                        contentDescription = stringResource(R.string.attach_file),
                    )
                }
                DropdownMenu(
                    expanded = attachMenuOpen,
                    onDismissRequest = { attachMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.attachment_choice_gallery)) },
                        leadingIcon = {
                            Icon(painterResource(R.drawable.ic_image_24dp), null)
                        },
                        onClick = {
                            attachMenuOpen = false
                            listener.onAttachImage()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.attachment_choice_file)) },
                        leadingIcon = {
                            Icon(painterResource(R.drawable.ic_attach_file_24dp), null)
                        },
                        onClick = {
                            attachMenuOpen = false
                            listener.onAttachFile()
                        },
                    )
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
                        onValueChange = { state.setInput(it) },
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
            val showMic = !hasText && !correcting
            FilledIconButton(
                onClick = {
                    if (showMic) {
                        listener.onRecordVoice()
                    } else if (hasText) {
                        listener.onSendTextMessage(text.trim())
                    }
                },
                enabled = showMic || hasText,
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
        }
    }
}
