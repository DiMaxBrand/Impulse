package eu.siacs.conversations.ui

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.ui.adapter.MediaAdapter
import eu.siacs.conversations.ui.adapter.MessageAdapter
import eu.siacs.conversations.ui.util.Attachment
import eu.siacs.conversations.ui.util.AvatarWorkerTask
import eu.siacs.conversations.utils.IrregularUnicodeDetector
import eu.siacs.conversations.utils.UIHelper
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.manager.JingleManager

/** Observable list of conversations that Compose tracks for recomposition. */
class ConversationListState {
    internal val list: SnapshotStateList<Conversation> = mutableStateListOf()
    var isRefreshing: Boolean by mutableStateOf(false)

    fun update(source: List<Conversation>) {
        list.clear()
        list.addAll(source)
    }

    fun remove(conversation: Conversation) { list.remove(conversation) }

    fun insert(conversation: Conversation, position: Int) { list.add(position, conversation) }

    fun contains(conversation: Conversation): Boolean = list.contains(conversation)
}

fun interface ConversationClickListener {
    fun onClick(conversation: Conversation)
}

fun interface ConversationSwipeListener {
    fun onSwiped(conversation: Conversation, position: Int)
}

object ConversationListHelper {
    @JvmStatic
    fun setup(
        composeView: ComposeView,
        state: ConversationListState,
        onConversationClick: ConversationClickListener,
        onConversationSwiped: ConversationSwipeListener,
        onRefresh: Runnable,
        fab: ExtendedFloatingActionButton,
    ) {
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            ImpulseTheme {
                ConversationList(
                    conversations = state.list,
                    isRefreshing = state.isRefreshing,
                    onRefresh = {
                        state.isRefreshing = true
                        composeView.post {
                            onRefresh.run()
                            state.isRefreshing = false
                        }
                    },
                    onConversationClick = { onConversationClick.onClick(it) },
                    onConversationSwiped = { c, pos -> onConversationSwiped.onSwiped(c, pos) },
                    onFirstVisibleIndexChanged = { index ->
                        composeView.post { if (index > 0) fab.shrink() else fab.extend() }
                    },
                )
            }
        }
    }
}

@Composable
private fun ImpulseTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme =
        if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun ConversationList(
    conversations: List<Conversation>,
    onConversationClick: (Conversation) -> Unit,
    onConversationSwiped: (Conversation, Int) -> Unit,
    onFirstVisibleIndexChanged: (Int) -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        if (conversations.isEmpty()) {
            LaunchedEffect(Unit) { onFirstVisibleIndexChanged(0) }
            EmptyConversationsHint(modifier = Modifier.fillMaxSize())
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(listState.firstVisibleItemIndex) {
                onFirstVisibleIndexChanged(listState.firstVisibleItemIndex)
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(
                    conversations,
                    key = { _, c -> c.getUuid() ?: c.hashCode().toString() },
                ) { index, conversation ->
                    val shape = conversationItemShape(index, conversations.size)
                    val swipeState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value != SwipeToDismissBoxValue.Settled) {
                                onConversationSwiped(conversation, index)
                                true
                            } else {
                                false
                            }
                        },
                    )
                    SwipeToDismissBox(
                        state = swipeState,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        backgroundContent = {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.secondaryFixedDim),
                            )
                        },
                    ) {
                        ConversationItem(
                            conversation = conversation,
                            onClick = { onConversationClick(conversation) },
                            shape = shape,
                        )
                    }
                }
            }
        }
    }
}

private fun conversationItemShape(index: Int, total: Int): RoundedCornerShape {
    val large = 16.dp
    val small = 5.dp
    return when {
        total == 1 -> RoundedCornerShape(large)
        index == 0 -> RoundedCornerShape(topStart = large, topEnd = large, bottomStart = small, bottomEnd = small)
        index == total - 1 -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = large, bottomEnd = large)
        else -> RoundedCornerShape(small)
    }
}

@Composable
private fun EmptyConversationsHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_forum_thin_48dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primaryFixed,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.empty_conversations_list_hint),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = 48.dp, end = 48.dp),
        )
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val message = conversation.latestMessage
    val status = message.status
    val unreadCount = conversation.unreadCount()
    val isRead = conversation.isRead
    val isPinned = conversation.getBooleanAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, false)
    val draft: Conversation.Draft? = if (isRead) conversation.getDraft() else null

    val ongoingCall = remember(conversation) {
        if (conversation.getMode() == Conversational.MODE_MULTI) {
            com.google.common.base.Optional.absent<Any>()
        } else {
            try {
                val mgr = conversation.getAccount().xmppConnection.getManager(JingleManager::class.java)
                mgr.getOngoingRtpConnection(conversation.getContact())
            } catch (_: Exception) {
                com.google.common.base.Optional.absent()
            }
        }
    }

    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConversationAvatar(conversation = conversation)
            Spacer(modifier = Modifier.width(dimensionResource(R.dimen.avatar_item_distance)))
            Column(modifier = Modifier.weight(1f)) {
                // Name + timestamp
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val name = conversation.name
                    Text(
                        text = when (name) {
                            is Jid -> IrregularUnicodeDetector.style(context, name).toString()
                            else -> name.toString()
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isRead) FontWeight.Normal else FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                    )
                    val timestamp = draft?.timestamp ?: message.timeSent
                    Text(
                        text = UIHelper.readableTimeDifference(context, timestamp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Preview row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left: sender name + message/icon
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (draft != null) {
                            Text(
                                text = stringResource(R.string.draft),
                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                maxLines = 1,
                                modifier = Modifier.padding(end = 5.dp),
                            )
                            Text(
                                text = draft.message,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            val senderText: String? = when {
                                status == Message.STATUS_RECEIVED && conversation.getMode() == Conversational.MODE_MULTI -> {
                                    val dn = UIHelper.getMessageDisplayName(message)
                                    "${dn.substringBefore(" ")}:"
                                }
                                status == Message.STATUS_RECEIVED -> null
                                message.type != Message.TYPE_STATUS -> "${context.getString(R.string.me)}:"
                                else -> null
                            }
                            if (senderText != null) {
                                Text(
                                    text = senderText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isRead) FontWeight.Normal else FontWeight.Bold,
                                    ),
                                    maxLines = 1,
                                    modifier = Modifier.padding(end = 5.dp),
                                )
                            }
                            val fileAvailable = !message.isDeleted
                            if (fileAvailable && (message.isFileOrImage || message.treatAsDownloadable() || message.isGeoUri)) {
                                Icon(
                                    painter = painterResource(MediaAdapter.getImageDrawable(Attachment.of(message))),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp).padding(end = 5.dp),
                                )
                            } else {
                                val textColor = LocalContentColor.current.toArgb()
                                val preview = UIHelper.getMessagePreview(context, message, textColor)
                                Text(
                                    text = UIHelper.shorten(preview.first).toString(),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (!isRead) FontWeight.Bold else FontWeight.Normal,
                                        fontStyle = if (preview.second) FontStyle.Italic else FontStyle.Normal,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    // Right: status, notification, pin, badge
                    val iconMod = Modifier.padding(start = 4.dp).size(18.dp)

                    if (message.type != Message.TYPE_RTP_SESSION) {
                        val statusDrawable = MessageAdapter.getMessageStatusAsDrawable(message, status)
                        if (statusDrawable != null) {
                            val displayed = status == Message.STATUS_SEND_DISPLAYED
                            Icon(
                                painter = painterResource(
                                    if (displayed) R.drawable.ic_done_all_bold_24dp else statusDrawable
                                ),
                                contentDescription = null,
                                tint = if (displayed) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = iconMod,
                            )
                        }
                    }

                    val mutedTill = conversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL, 0)
                    val notifRes: Int? = when {
                        ongoingCall.isPresent -> R.drawable.ic_phone_in_talk_24dp
                        mutedTill == Long.MAX_VALUE -> R.drawable.ic_notifications_off_24dp
                        mutedTill >= System.currentTimeMillis() -> R.drawable.ic_notifications_paused_24dp
                        !conversation.alwaysNotify() -> R.drawable.ic_notifications_none_24dp
                        else -> null
                    }
                    if (notifRes != null) {
                        Icon(
                            painter = painterResource(notifRes),
                            contentDescription = null,
                            modifier = iconMod,
                        )
                    }

                    if (isPinned) {
                        Icon(
                            painter = painterResource(R.drawable.ic_star_24dp),
                            contentDescription = null,
                            modifier = iconMod,
                        )
                    }

                    if (unreadCount > 0) {
                        UnreadBadge(
                            count = unreadCount,
                            modifier = Modifier.padding(start = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationAvatar(conversation: Conversation, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            ShapeableImageView(context).apply {
                val cornerPx = 8f * context.resources.displayMetrics.density
                shapeAppearanceModel = ShapeAppearanceModel.builder()
                    .setAllCornerSizes(cornerPx)
                    .build()
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        },
        update = { imageView ->
            AvatarWorkerTask.loadAvatar(
                conversation,
                imageView,
                R.dimen.avatar_on_conversation_overview,
            )
        },
        onReset = { imageView -> imageView.setImageDrawable(null) },
        modifier = modifier.size(56.dp),
    )
}

@Composable
private fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(18.dp)
            .background(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(50),
            ),
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConversationListEmptyPreview() {
    ImpulseTheme {
        ConversationList(
            conversations = emptyList(),
            onConversationClick = {},
            onConversationSwiped = { _, _ -> },
            onFirstVisibleIndexChanged = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
