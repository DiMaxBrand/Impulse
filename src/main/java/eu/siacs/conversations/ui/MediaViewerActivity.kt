package eu.siacs.conversations.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.ui.util.ShareUtil
import eu.siacs.conversations.ui.util.ViewUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

/**
 * Full-screen in-app photo/video viewer — replaces the external "Open with" gallery hand-off.
 * Pages across every photo/video in the conversation (not just the tapped grid tile), loading
 * further messages dynamically as the user approaches either edge of the currently-loaded window.
 */
class MediaViewerActivity : XmppActivity() {

    companion object {
        private const val EXTRA_CONVERSATION = "conversation"
        private const val EXTRA_MESSAGE_UUID = "message_uuid"
        private const val EXTRA_BATCH_UUIDS = "batch_uuids"
        const val EXTRA_RESULT_SHOW_UUID = "show_in_chat_uuid"

        @JvmStatic
        @JvmOverloads
        fun launch(context: Context, message: Message, batchUuids: List<String> = emptyList()): Intent {
            val conversation = message.conversation as? Conversation
            return Intent(context, MediaViewerActivity::class.java).apply {
                putExtra(EXTRA_CONVERSATION, conversation?.getUuid())
                putExtra(EXTRA_MESSAGE_UUID, message.getUuid())
                putStringArrayListExtra(EXTRA_BATCH_UUIDS, ArrayList(batchUuids))
            }
        }
    }

    private val conversationState = mutableStateOf<Conversation?>(null)
    private val startMessageState = mutableStateOf<Message?>(null)
    private var batchUuids: Set<String> = emptySet()
    private var failed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        batchUuids = intent.getStringArrayListExtra(EXTRA_BATCH_UUIDS)?.toSet() ?: emptySet()
        setContent {
            ImpulseExpressiveTheme {
                val conversation = conversationState.value
                val start = startMessageState.value
                if (conversation != null && start != null) {
                    MediaViewerScreen(
                        service = xmppConnectionService,
                        conversation = conversation,
                        startMessage = start,
                        batchUuids = batchUuids,
                        onClose = { finish() },
                        onShare = { message -> ShareUtil.share(this@MediaViewerActivity, message) },
                        onSave = { message -> saveToDevice(message) },
                        onShowInChat = { message ->
                            setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_SHOW_UUID, message.getUuid()))
                            finish()
                        },
                        onOpenExternally = { message ->
                            val file = xmppConnectionService.fileBackend.getFile(message)
                            if (file.exists()) ViewUtil.view(this@MediaViewerActivity, file, message.getUuid() ?: "")
                        },
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        if (!failed) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
            }
        }
    }

    private fun saveToDevice(message: Message) {
        val storageLocation = message.getRelativeFilePath() ?: return
        if (storageLocation.sharedStorage()) return
        val future = xmppConnectionService.fileBackend.saveInternalToExternal(storageLocation)
        Futures.addCallback(
            future,
            object : FutureCallback<Void?> {
                override fun onSuccess(result: Void?) {
                    Toast.makeText(
                        this@MediaViewerActivity,
                        resources.getQuantityString(R.plurals.attachments_saved, 1, 1),
                        Toast.LENGTH_LONG,
                    ).show()
                }

                override fun onFailure(t: Throwable) {
                    Toast.makeText(this@MediaViewerActivity, R.string.could_not_save_files, Toast.LENGTH_LONG).show()
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    override fun refreshUiReal() {
        // No legacy (non-Compose) UI to refresh here.
    }

    override fun onBackendConnected() {
        val conversationUuid = intent.getStringExtra(EXTRA_CONVERSATION)
        val messageUuid = intent.getStringExtra(EXTRA_MESSAGE_UUID)
        val conversation = conversationUuid?.let { xmppConnectionService.findConversationByUuid(it) }
        if (conversation == null || messageUuid == null) {
            failed = true
            finish()
            return
        }
        conversationState.value = conversation
        lifecycleScope.launch(Dispatchers.IO) {
            val message = xmppConnectionService.databaseBackend.getMessage(conversation, messageUuid)
            withContext(Dispatchers.Main) {
                if (message != null) {
                    startMessageState.value = message
                } else {
                    failed = true
                    finish()
                }
            }
        }
    }
}

private const val WINDOW_BATCH = 12
private const val VIEWER_IMAGE_SIZE_PX = 1600

/** Older-first collection of up to [needed] media messages strictly before [beforeTimestamp],
 * expanding the search further back until either enough are found or history is exhausted. */
private suspend fun collectMediaOlder(
    service: XmppConnectionService,
    conversation: Conversation,
    beforeTimestamp: Long,
    needed: Int,
): List<Message> {
    val result = ArrayList<Message>()
    var cursor = beforeTimestamp
    while (result.size < needed) {
        val batch = service.databaseBackend.getMessages(conversation, 100, cursor)
        if (batch.isEmpty()) break
        cursor = batch.first().timeSent
        result.addAll(0, batch.filter { isMediaCell(it) })
        if (batch.size < 100) break
    }
    return if (result.size > needed) result.subList(result.size - needed, result.size) else result
}

/** Newer-first collection of up to [needed] media messages strictly after [afterTimestamp]. */
private suspend fun collectMediaNewer(
    service: XmppConnectionService,
    conversation: Conversation,
    afterTimestamp: Long,
    needed: Int,
): List<Message> {
    val result = ArrayList<Message>()
    var cursor = afterTimestamp
    while (result.size < needed) {
        val batch = service.databaseBackend.getMessagesAfter(conversation, 100, cursor)
        if (batch.isEmpty()) break
        cursor = batch.last().timeSent
        result.addAll(batch.filter { isMediaCell(it) })
        if (batch.size < 100) break
    }
    return if (result.size > needed) result.subList(0, needed) else result
}

@Composable
private fun MediaViewerScreen(
    service: XmppConnectionService,
    conversation: Conversation,
    startMessage: Message,
    batchUuids: Set<String>,
    onClose: () -> Unit,
    onShare: (Message) -> Unit,
    onSave: (Message) -> Unit,
    onShowInChat: (Message) -> Unit,
    onOpenExternally: (Message) -> Unit,
) {
    val items = remember { mutableStateListOf<Message>() }
    var initialIndex by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var loadingOlder by remember { mutableStateOf(false) }
    var loadingNewer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(startMessage.getUuid()) {
        val older = withContext(Dispatchers.IO) {
            collectMediaOlder(service, conversation, startMessage.timeSent, WINDOW_BATCH)
        }
        val newer = withContext(Dispatchers.IO) {
            collectMediaNewer(service, conversation, startMessage.timeSent, WINDOW_BATCH)
        }
        items.clear()
        items.addAll(older)
        items.add(startMessage)
        items.addAll(newer)
        initialIndex = older.size
        ready = true
    }

    if (!ready || items.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = initialIndex) { items.size }

    LaunchedEffect(pagerState.currentPage, items.size) {
        if (pagerState.currentPage <= 2 && !loadingOlder) {
            val oldest = items.firstOrNull()
            if (oldest != null) {
                loadingOlder = true
                val more = withContext(Dispatchers.IO) {
                    collectMediaOlder(service, conversation, oldest.timeSent, WINDOW_BATCH)
                }
                loadingOlder = false
                if (more.isNotEmpty()) {
                    val anchorUuid = items.getOrNull(pagerState.currentPage)?.getUuid()
                    items.addAll(0, more)
                    val newIndex = items.indexOfFirst { it.getUuid() == anchorUuid }
                    if (newIndex >= 0 && newIndex != pagerState.currentPage) {
                        pagerState.scrollToPage(newIndex)
                    }
                }
            }
        }
        if (pagerState.currentPage >= items.size - 3 && !loadingNewer) {
            val newest = items.lastOrNull()
            if (newest != null) {
                loadingNewer = true
                val more = withContext(Dispatchers.IO) {
                    collectMediaNewer(service, conversation, newest.timeSent, WINDOW_BATCH)
                }
                loadingNewer = false
                if (more.isNotEmpty()) items.addAll(more)
            }
        }
    }

    val currentMessage = items.getOrNull(pagerState.currentPage) ?: startMessage
    // A "batch" of 1 (a lone bubble, not a grid tile) never counts as being "in the batch" —
    // there's nothing distinct from whole-history to show for a single photo.
    val inBatch = batchUuids.size > 1 && currentMessage.getUuid() in batchUuids
    var chromeVisible by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            MediaViewerPage(
                service = service,
                message = items[page],
                onOpenExternally = onOpenExternally,
                onTap = { chromeVisible = !chromeVisible },
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            MediaViewerTopBar(
                message = currentMessage,
                conversation = conversation,
                onBack = onClose,
                onShare = { onShare(currentMessage) },
                onSave = { onSave(currentMessage) },
                onShowInChat = { onShowInChat(currentMessage) },
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            MediaViewerFilmstrip(
                service = service,
                items = items,
                currentIndex = pagerState.currentPage,
                batchUuids = batchUuids,
                inBatch = inBatch,
                onSelect = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } },
            )
        }
    }
}

@Composable
private fun MediaViewerTopBar(
    message: Message,
    conversation: Conversation,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onShowInChat: () -> Unit,
) {
    val context = LocalContext.current
    // Same "Today / Yesterday / weekday-if-within-7-days / full date" convention as the chat's
    // own date pills — a bare weekday name (e.g. "Sunday") is only unambiguous for a recent
    // message; anything older needs the actual date, which formatDatePill already handles.
    val day = formatDatePill(context, message.timeSent)
    val time = remember(message.getUuid()) {
        android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(message.timeSent))
    }
    val dateText = "$day · $time"
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.4f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
    ) {
        ViewerChipButton(iconRes = R.drawable.ic_arrow_back_24dp, contentDescription = stringResource(R.string.back), onClick = onBack)
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.layout.Column {
            Text(
                text = conversation.getName()?.toString() ?: "",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(text = dateText, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ViewerChipButton(iconRes = R.drawable.ic_share_24dp, contentDescription = stringResource(R.string.share), onClick = onShare)
            ViewerChipButton(iconRes = R.drawable.ic_download_24dp, contentDescription = stringResource(R.string.save), onClick = onSave)
            Box {
                ViewerChipButton(
                    iconRes = R.drawable.ic_more_horiz_24dp,
                    contentDescription = stringResource(R.string.more_options),
                    onClick = { menuExpanded = true },
                )
                ExpressiveDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    ExpressiveMenuItem(
                        iconRes = R.drawable.ic_visibility_24dp,
                        label = stringResource(R.string.show_in_chat),
                        onClick = {
                            menuExpanded = false
                            onShowInChat()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerChipButton(iconRes: Int, contentDescription: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun MediaViewerPage(
    service: XmppConnectionService,
    message: Message,
    onOpenExternally: (Message) -> Unit,
    onTap: () -> Unit,
) {
    val uuid = message.getUuid()
    val isVideo = message.mimeType?.startsWith("video/") == true
    val bitmapState = remember(uuid) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uuid) {
        val bm = withContext(Dispatchers.IO) {
            try {
                service.fileBackend.getThumbnail(message, VIEWER_IMAGE_SIZE_PX, false, false)
            } catch (_: Exception) {
                null
            }
        }
        if (bm != null) bitmapState.value = bm.asImageBitmap()
    }
    val bitmap = bitmapState.value
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 5f))
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().zoomable(zoomableState, onClick = { onTap() }),
            )
            if (isVideo) {
                // Inline playback isn't built for the full-screen viewer yet — tapping the play
                // affordance falls back to the same external handoff other file types still use.
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { onOpenExternally(message) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_arrow_24dp),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

@Composable
private fun MediaViewerFilmstrip(
    service: XmppConnectionService,
    items: List<Message>,
    currentIndex: Int,
    batchUuids: Set<String>,
    inBatch: Boolean,
    onSelect: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .background(Color.Black.copy(alpha = 0.46f)),
    ) {
        AnimatedContent(
            targetState = inBatch,
            transitionSpec = {
                (scaleIn(tween(220), initialScale = 0.7f) + fadeIn(tween(220)))
                    .togetherWith(scaleOut(tween(160), targetScale = 0.7f) + fadeOut(tween(160)))
            },
            label = "filmstripScope",
        ) { showBatchOnly ->
            val strip = if (showBatchOnly) {
                items.filter { it.getUuid() in batchUuids }
            } else {
                val from = (currentIndex - 6).coerceAtLeast(0)
                val to = (currentIndex + 7).coerceAtMost(items.size)
                items.subList(from, to)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                strip.forEach { message ->
                    val index = items.indexOf(message)
                    val isCurrent = index == currentIndex
                    FilmstripThumb(
                        service = service,
                        message = message,
                        current = isCurrent,
                        onClick = { if (index >= 0) onSelect(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilmstripThumb(service: XmppConnectionService, message: Message, current: Boolean, onClick: () -> Unit) {
    val uuid = message.getUuid()
    val size = if (current) 44.dp else 40.dp
    val shape = RoundedCornerShape(if (current) 15.dp else 12.dp)
    // Most filmstrip entries were never rendered as a grid tile or bubble, so ThumbnailCache
    // won't have them yet — decode on demand, same as MediaGridCell does for the in-chat grid.
    if (ThumbnailCache.get(uuid) == null) {
        LaunchedEffect(uuid) {
            val bm = withContext(Dispatchers.IO) {
                try { service.fileBackend.getThumbnail(message, 120, false, false) } catch (_: Exception) { null }
            }
            if (bm != null) ThumbnailCache.put(uuid, bm.asImageBitmap())
        }
    }
    val bitmap = ThumbnailCache.get(uuid)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .then(
                if (current) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.15f)))
        }
    }
}
