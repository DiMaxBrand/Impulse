package eu.siacs.conversations.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.ui.adapter.MediaAdapter
import eu.siacs.conversations.ui.adapter.MessageAdapter
import eu.siacs.conversations.ui.util.Attachment
import eu.siacs.conversations.utils.IrregularUnicodeDetector
import eu.siacs.conversations.utils.UIHelper
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.manager.ChatStateManager
import eu.siacs.conversations.xmpp.manager.JingleManager
import im.conversations.android.xmpp.model.stanza.Presence
import im.conversations.android.xmpp.model.state.Composing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Observable list of conversations that Compose tracks for recomposition. */
class ConversationListState {
    internal val list: SnapshotStateList<Conversation> = mutableStateListOf()
    internal val revision = mutableIntStateOf(0)
    val isConnecting: MutableState<Boolean> = mutableStateOf(false)

    fun update(source: List<Conversation>) {
        list.clear()
        list.addAll(source)
        revision.intValue++
    }

    fun remove(conversation: Conversation) { list.remove(conversation) }

    fun insert(conversation: Conversation, position: Int) { list.add(position, conversation) }

    fun contains(conversation: Conversation): Boolean = list.contains(conversation)
}

fun interface ConversationClickListener {
    fun onClick(conversation: Conversation)
}

/** Person bounding box within a segmented avatar bitmap, as fractions of its dimensions. */
private data class PersonBounds(val top: Float, val bottom: Float, val centerX: Float)

private fun presenceShape(
    isGroup: Boolean,
    availability: Presence.Availability?,
    hasOngoingCall: Boolean,
    isTyping: Boolean,
): RoundedPolygon = when {
    isTyping -> MaterialShapeHelpers.arrow()
    hasOngoingCall -> MaterialShapeHelpers.softBurst()
    isGroup -> MaterialShapeHelpers.slanted()
    availability == Presence.Availability.CHAT || availability == Presence.Availability.ONLINE -> MaterialShapeHelpers.pill()
    availability == Presence.Availability.AWAY -> MaterialShapeHelpers.semiCircle()
    availability == Presence.Availability.XA -> MaterialShapeHelpers.diamond()
    availability == Presence.Availability.DND -> MaterialShapeHelpers.gem()
    availability == Presence.Availability.OFFLINE -> MaterialShapeHelpers.ghostish()
    else -> MaterialShapeHelpers.slanted()
}

private fun presenceStringRes(availability: Presence.Availability?): Int? = when (availability) {
    Presence.Availability.CHAT, Presence.Availability.ONLINE -> R.string.presence_online
    Presence.Availability.AWAY, Presence.Availability.XA -> R.string.presence_away
    Presence.Availability.DND -> R.string.presence_dnd
    else -> null
}

private fun presenceColor(availability: Presence.Availability?): Color = when (availability) {
    Presence.Availability.CHAT, Presence.Availability.ONLINE -> Color(0xFF4CAF50)
    Presence.Availability.AWAY, Presence.Availability.XA -> Color(0xFFFFA000)
    Presence.Availability.DND -> Color(0xFFE53935)
    else -> Color.Gray
}


object ConversationListHelper {
    @JvmStatic
    fun setup(
        composeView: ComposeView,
        state: ConversationListState,
        onConversationClick: ConversationClickListener,
        fab: ExtendedFloatingActionButton,
        onDownloadComplete: () -> Unit = {},
    ) {
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            ImpulseTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column {
                        ConnectingStrip(isConnecting = state.isConnecting.value)
                        ConversationList(
                            conversations = state.list,
                            revision = state.revision.intValue,
                            onConversationClick = { onConversationClick.onClick(it) },
                            onFirstVisibleIndexChanged = { index ->
                                composeView.post { if (index > 0) fab.shrink() else fab.extend() }
                            },
                        )
                    }
                    DownloadProgressBar(
                        onComplete = onDownloadComplete,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    )
                }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectingStrip(isConnecting: Boolean) {
    AnimatedVisibility(
        visible = isConnecting,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ConversationList(
    conversations: List<Conversation>,
    revision: Int,
    onConversationClick: (Conversation) -> Unit,
    onFirstVisibleIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (conversations.isEmpty()) {
        LaunchedEffect(Unit) { onFirstVisibleIndexChanged(0) }
        EmptyConversationsHint(modifier = modifier.fillMaxSize())
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(listState.firstVisibleItemIndex) {
        onFirstVisibleIndexChanged(listState.firstVisibleItemIndex)
    }

    LazyColumn(state = listState, modifier = modifier) {
        itemsIndexed(
            conversations,
            key = { _, c -> c.getUuid() ?: c.hashCode().toString() },
        ) { index, conversation ->
            val shape = conversationItemShape(index, conversations.size)
            ConversationItem(
                conversation = conversation,
                revision = revision,
                onClick = { onConversationClick(conversation) },
                shape = shape,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
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
    revision: Int,
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

    val ongoingCall = remember(conversation, revision) {
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

    val availability: Presence.Availability? = remember(conversation, revision) {
        if (conversation.getMode() == Conversational.MODE_SINGLE)
            try { conversation.getContact().getShownStatus() } catch (_: Exception) { null }
        else null
    }

    val isTyping: Boolean = remember(conversation, revision) {
        if (conversation.getMode() == Conversational.MODE_SINGLE)
            try {
                val state = conversation.getAccount().getXmppConnection()
                    ?.getManager(ChatStateManager::class.java)
                    ?.getIncoming(conversation.getAddress())
                state == Composing::class.java
            } catch (_: Exception) { false }
        else false
    }


    val avatarItemDistance = dimensionResource(R.dimen.avatar_item_distance)
    var cardHeightPx by remember { mutableIntStateOf(0) }

    // Avatar is outside the Card so it can draw above the card's top edge without being
    // clipped by Material3's Surface, which always clips children to the card shape.
    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            onClick = onClick,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth().onSizeChanged { cardHeightPx = it.height },
        ) {
            Row(
                modifier = Modifier.padding(
                    start = 8.dp + 56.dp + avatarItemDistance,
                    end = 8.dp,
                    top = 8.dp,
                    bottom = 8.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Column(modifier = Modifier.weight(1f)) {
                // Name + presence status + timestamp
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
                    val presenceText = when {
                        isTyping -> stringResource(R.string.typing_indicator)
                        else -> presenceStringRes(availability)?.let { "● ${stringResource(it)}" }
                    }
                    if (presenceText != null) {
                        Text(
                            text = presenceText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isTyping) MaterialTheme.colorScheme.primary else presenceColor(availability),
                            maxLines = 1,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
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
        ConversationAvatar(
            conversation = conversation,
            availability = availability,
            hasOngoingCall = ongoingCall.isPresent,
            isTyping = isTyping,
            cardHeightPx = cardHeightPx,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp),
        )
    }
}

@Composable
private fun ConversationAvatar(
    conversation: Conversation,
    availability: Presence.Availability?,
    hasOngoingCall: Boolean,
    isTyping: Boolean,
    cardHeightPx: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val canvasSizePx = with(density) { 56.dp.toPx() }
    // Center offset: how far the canvas top sits below the card top.
    // Once the card has been measured (cardHeightPx > 0) this is exact; before that it's 0.
    val topMarginPx = ((cardHeightPx - canvasSizePx) / 2f).coerceAtLeast(0f)
    val maxOverflowPx = topMarginPx + with(density) { 3.dp.toPx() }

    val isGroup = conversation.getMode() == Conversational.MODE_MULTI

    // Load at full resolution so the 3D zoom effect stays crisp at any overflow depth.
    val avatarLoadSizePx = 1440

    val hasRealPhoto = remember(conversation) {
        try {
            if (isGroup) true
            else {
                val contact = conversation.getContact()
                contact.getAvatar() != null || contact.getProfilePhoto() != null
            }
        } catch (_: Exception) { false }
    }

    val avatarState = remember(conversation.getUuid()) { mutableStateOf<ImageBitmap?>(null) }
    val segmentedState = remember(conversation.getUuid()) { mutableStateOf<ImageBitmap?>(null) }
    val personBoundsState = remember(conversation.getUuid()) { mutableStateOf<PersonBounds?>(null) }
    val avatarBitmap by avatarState
    val segmentedBitmap by segmentedState
    val personBounds by personBoundsState

    LaunchedEffect(conversation) {
        val activity = context as? XmppActivity ?: return@LaunchedEffect
        val bm = withContext(Dispatchers.IO) {
            activity.avatarService().get(conversation, avatarLoadSizePx, false)
        } ?: return@LaunchedEffect
        avatarState.value = bm.asImageBitmap()
        val key = conversation.getUuid() ?: return@LaunchedEffect
        AvatarSegmenter.segment(bm, key, shouldSegment = !isGroup && hasRealPhoto) { segBm ->
            segmentedState.value = segBm?.asImageBitmap()
        }
    }

    // Scan segmented bitmap to find the person's bounding box (off main thread)
    LaunchedEffect(segmentedBitmap) {
        val segBm = segmentedBitmap
        if (segBm == null) { personBoundsState.value = null; return@LaunchedEffect }
        val bounds = withContext(Dispatchers.Default) {
            val bmp = segBm.asAndroidBitmap().let {
                if (it.config == android.graphics.Bitmap.Config.HARDWARE)
                    it.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                else it
            }
            var top = -1; var bottom = -1; var left = -1; var right = -1
            for (row in 0 until bmp.height) {
                for (col in 0 until bmp.width) {
                    if (android.graphics.Color.alpha(bmp.getPixel(col, row)) > 32) {
                        if (top == -1) top = row
                        bottom = row
                        if (left == -1 || col < left) left = col
                        if (right == -1 || col > right) right = col
                    }
                }
            }
            if (top >= 0 && bottom > top && right >= left) {
                PersonBounds(
                    top = top.toFloat() / bmp.height,
                    bottom = bottom.toFloat() / bmp.height,
                    centerX = (left + right) / 2f / bmp.width,
                )
            } else null
        }
        personBoundsState.value = bounds
    }

    // Spring-animate overflow in once person bounds are known, reset when they're gone
    val overflowAnim = remember { Animatable(0f) }
    LaunchedEffect(personBounds) {
        if (personBounds != null) {
            overflowAnim.snapTo(0f)
            overflowAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        } else {
            overflowAnim.snapTo(0f)
        }
    }

    val targetShape = remember(isGroup, availability, hasOngoingCall, isTyping) {
        presenceShape(isGroup, availability, hasOngoingCall, isTyping)
    }
    val morphProgress = remember { Animatable(1f) }
    val fromShape = remember { mutableStateOf(targetShape) }
    val toShape = remember { mutableStateOf(targetShape) }

    LaunchedEffect(targetShape) {
        if (toShape.value === targetShape) return@LaunchedEffect
        fromShape.value = toShape.value
        toShape.value = targetShape
        morphProgress.snapTo(0f)
        morphProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        )
    }

    val morph = remember(fromShape.value, toShape.value) {
        Morph(fromShape.value, toShape.value)
    }
    val progress = morphProgress.value

    val reusedPath = remember { androidx.compose.ui.graphics.Path() }
    val reusedMatrix = remember { android.graphics.Matrix() }
    val fallbackColor = MaterialTheme.colorScheme.primaryContainer

    // Computed here so both the layout modifier and DrawScope share the same value.
    val headOverflowPx = maxOverflowPx * overflowAnim.value

    // Canvas is expanded upward by headOverflowPx so the overflow zone sits at positive y.
    // The morph shape is translated down by headOverflowPx to stay at the original card position.
    // Reporting canvasSizePx as the layout height keeps sibling elements (card padding) stable.
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .width(56.dp)
            .layout { measurable, constraints ->
                val totalH = (canvasSizePx + headOverflowPx).toInt().coerceAtLeast(canvasSizePx.toInt())
                val placeable = measurable.measure(constraints.copy(minHeight = totalH, maxHeight = totalH))
                layout(placeable.width, canvasSizePx.toInt()) {
                    placeable.place(0, -headOverflowPx.toInt())
                }
            }
    ) {
        val canvasW = size.width.toInt()
        val canvasH = size.height.toInt()

        // Shape starts at y = headOverflowPx so the area above it is the overflow zone.
        reusedMatrix.reset()
        reusedMatrix.postScale(size.width, size.width)
        reusedMatrix.postTranslate(0f, headOverflowPx)
        morph.toPath(progress, reusedPath)
        reusedPath.asAndroidPath().transform(reusedMatrix)

        val bm = avatarBitmap
        if (bm == null) {
            clipPath(reusedPath) { drawRect(fallbackColor) }
            return@Canvas
        }

        val pb = personBounds
        val srcOffset: IntOffset
        val srcSize: IntSize

        if (!isGroup && pb != null) {
            val personTopPx = (pb.top * bm.height).toInt().coerceAtLeast(0)
            val personBottomPx = (pb.bottom * bm.height).toInt().coerceAtMost(bm.height)
            val personH = (personBottomPx - personTopPx).coerceAtLeast(1)
            val personIsFullBody = pb.bottom > 0.75f
            val cropH = if (personIsFullBody) personH else (personH * 0.65f).toInt().coerceAtLeast(1)
            val cropW = cropH.coerceAtMost(bm.width)
            // Center the crop window on the person's actual horizontal position instead of the
            // bitmap's center, so off-center subjects aren't cropped out by the zoom.
            val personCenterXPx = (pb.centerX * bm.width).toInt()
            val cropX = (personCenterXPx - cropW / 2).coerceIn(0, bm.width - cropW)
            srcOffset = IntOffset(cropX, personTopPx)
            srcSize = IntSize(cropW, cropH)
        } else {
            srcOffset = IntOffset(0, 0)
            srcSize = IntSize(bm.width, bm.height)
        }

        // All passes share the same src→dst transform — no ghost / double image.
        // When person bounds are known, zoom uniformly (dst square = canvasH × canvasH) so the
        // photo is NOT stretched as the overflow zone grows — it zooms in and shifts up instead.
        // The extra horizontal width is centered and clipped by the shape/clipRect above.
        // For groups / no bounds: canvasH == canvasW (no overflow), so behavior is identical.
        val dstW = if (!isGroup && pb != null) canvasH else canvasW
        val dstOffset = IntOffset((canvasW - dstW) / 2, 0)
        val dstSize = IntSize(dstW, canvasH)

        // Pass 1: photo clipped to morph shape
        clipPath(reusedPath) {
            drawImage(bm, srcOffset, srcSize, dstOffset, dstSize)
        }

        val segBm = segmentedBitmap
        if (!isGroup && segBm != null && headOverflowPx > 0f) {
            // Pass 2: segmented person from overflow zone into the shape top.
            // 50% covers the worst-case shapes: pill (ONLINE, needs ~42%), diamond (XA, needs ~26%),
            // arrow (typing, needs ~41%). Transparent background handles side boundaries naturally.
            clipRect(0f, 0f, size.width, headOverflowPx + size.width * 0.50f) {
                drawImage(segBm, srcOffset, srcSize, dstOffset, dstSize)
            }
        }
    }
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
            revision = 0,
            onConversationClick = {},
            onFirstVisibleIndexChanged = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
