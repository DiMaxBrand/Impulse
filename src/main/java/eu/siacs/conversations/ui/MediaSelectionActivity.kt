package eu.siacs.conversations.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.services.XmppConnectionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WhatsApp-style square photo grid for hand-picking a subset of a media pool the caller hands it
 * — a grid tile's whole batch (reached via long-press "Select" -> "Select Photos", or "Select to
 * delete"), or whatever's currently loaded when the "+N" overflow cell of an in-progress
 * selection elsewhere in the chat is tapped. Back discards and returns nothing; Continue returns
 * the tapped subset as an activity result — merging it into an ongoing chat selection, or seeding
 * a delete sheet scoped to exactly that subset, is entirely the caller's decision to make from
 * the result, not this screen's.
 */
class MediaSelectionActivity : XmppActivity() {

    companion object {
        private const val EXTRA_CONVERSATION = "conversation"
        private const val EXTRA_MESSAGE_UUIDS = "message_uuids"
        private const val EXTRA_FOR_DELETE = "for_delete"
        private const val EXTRA_PRESELECTED_UUIDS = "preselected_uuids"
        const val EXTRA_RESULT_UUIDS = "result_uuids"
        const val EXTRA_RESULT_FOR_DELETE = "result_for_delete"

        @JvmStatic
        @JvmOverloads
        fun launch(
            context: Context,
            conversationUuid: String,
            messageUuids: List<String>,
            forDelete: Boolean,
            preselectedUuids: List<String> = emptyList(),
        ): Intent {
            return Intent(context, MediaSelectionActivity::class.java).apply {
                putExtra(EXTRA_CONVERSATION, conversationUuid)
                putStringArrayListExtra(EXTRA_MESSAGE_UUIDS, ArrayList(messageUuids))
                putExtra(EXTRA_FOR_DELETE, forDelete)
                putStringArrayListExtra(EXTRA_PRESELECTED_UUIDS, ArrayList(preselectedUuids))
            }
        }
    }

    private val messagesState = mutableStateOf<List<Message>?>(null)
    private var forDelete = false
    private var preselected: List<String> = emptyList()
    private var failed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forDelete = intent.getBooleanExtra(EXTRA_FOR_DELETE, false)
        preselected = intent.getStringArrayListExtra(EXTRA_PRESELECTED_UUIDS) ?: emptyList()
        setContent {
            ImpulseExpressiveTheme {
                val messages = messagesState.value
                if (messages != null) {
                    MediaSelectionScreen(
                        service = xmppConnectionService,
                        messages = messages,
                        initiallySelected = preselected,
                        onCancel = { finish() },
                        onContinue = { uuids ->
                            setResult(
                                RESULT_OK,
                                Intent()
                                    .putStringArrayListExtra(EXTRA_RESULT_UUIDS, ArrayList(uuids))
                                    .putExtra(EXTRA_RESULT_FOR_DELETE, forDelete),
                            )
                            finish()
                        },
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (!failed) CircularProgressIndicator()
                    }
                }
            }
        }
    }

    override fun refreshUiReal() {
        // No legacy (non-Compose) UI to refresh here.
    }

    override fun onBackendConnected() {
        val conversationUuid = intent.getStringExtra(EXTRA_CONVERSATION)
        val uuids = intent.getStringArrayListExtra(EXTRA_MESSAGE_UUIDS)
        val conversation = conversationUuid?.let { xmppConnectionService.findConversationByUuid(it) }
        if (conversation == null || uuids == null) {
            failed = true
            finish()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val messages = uuids.mapNotNull { xmppConnectionService.databaseBackend.getMessage(conversation, it) }
            withContext(Dispatchers.Main) {
                if (messages.isNotEmpty()) {
                    messagesState.value = messages
                } else {
                    failed = true
                    finish()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MediaSelectionScreen(
    service: XmppConnectionService,
    messages: List<Message>,
    initiallySelected: List<String>,
    onCancel: () -> Unit,
    onContinue: (List<String>) -> Unit,
) {
    val selected = remember { mutableStateListOf<String>().apply { addAll(initiallySelected) } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selected.isEmpty()) stringResource(R.string.select_photos)
                        else stringResource(R.string.messages_selected_count, selected.size)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { onContinue(selected.toList()) }, enabled = selected.isNotEmpty()) {
                        Text(stringResource(R.string.continue_btn))
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(messages, key = { it.getUuid() ?: it.hashCode().toString() }) { message ->
                val uuid = message.getUuid()
                SelectionGridCell(
                    service = service,
                    message = message,
                    selected = uuid != null && uuid in selected,
                    onClick = { uuid?.let { if (!selected.remove(it)) selected.add(it) } },
                )
            }
        }
    }
}

@Composable
private fun SelectionGridCell(
    service: XmppConnectionService,
    message: Message,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val uuid = message.getUuid()
    val isVideo = message.mimeType?.startsWith("video/") == true
    if (ThumbnailCache.get(uuid) == null) {
        LaunchedEffect(uuid) {
            val bm = withContext(Dispatchers.IO) {
                try { service.fileBackend.getThumbnail(message, 300, false, false) } catch (_: Exception) { null }
            }
            if (bm != null) ThumbnailCache.put(uuid, bm.asImageBitmap())
        }
    }
    val bitmap = ThumbnailCache.get(uuid)
    Box(
        modifier = Modifier.aspectRatio(1f).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest))
        }
        if (isVideo) {
            Icon(
                painter = painterResource(R.drawable.ic_play_circle_24dp),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        if (selected) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }
        Box(modifier = Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.TopEnd) {
            SelectionCheckCircle(selected = selected)
        }
    }
}
