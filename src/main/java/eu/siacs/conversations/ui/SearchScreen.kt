package eu.siacs.conversations.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import kotlinx.coroutines.launch

/** One conversation the search can be limited to. */
class SearchScope(val uuid: String, val name: String)

/** Everything the search screen shows; the activity owns and fills it. */
class SearchUiState {
    val query = TextFieldState()
    var scopeUuid by mutableStateOf<String?>(null)
    var results by mutableStateOf<List<Message>>(emptyList())
    var highlight by mutableStateOf<List<String>>(emptyList())
    var loading by mutableStateOf(false)
    /** True once a search has come back for the current query (distinguishes "nothing found"). */
    var searched by mutableStateOf(false)
}

private val springBounds = BoundsTransform { _, _ -> spring(stiffness = 380f, dampingRatio = 0.8f) }
private val quickFade = spring<Float>(stiffness = 1600f, dampingRatio = 1.0f)

private fun displayName(c: Conversational): String =
    (c as? Conversation)?.getName()?.toString() ?: c.getContact().getDisplayName()

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
internal fun SearchScreen(
    state: SearchUiState,
    scopes: () -> List<SearchScope>,
    focusOnStart: Boolean,
    onBack: () -> Unit,
    onShowInChat: (Message) -> Unit,
    onReply: (Message) -> Unit,
    onCopy: (Message) -> Unit,
    onShare: (Message) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<Message?>(null) }
    val scrim by animateFloatAsState(
        targetValue = if (pickerOpen) 0.32f else 0f,
        animationSpec = spring(stiffness = 1600f, dampingRatio = 1.0f),
        label = "search_scrim",
    )
    androidx.activity.compose.BackHandler(enabled = pickerOpen) { pickerOpen = false }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (focusOnStart) runCatching { focus.requestFocus() } }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back_24dp), contentDescription = null)
                }
                TextField(
                    state = state.query,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    placeholder = { Text(stringResource(R.string.search_messages)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    trailingIcon = {
                        if (state.query.text.isNotEmpty()) {
                            IconButton(onClick = { state.query.clearText() }) {
                                Icon(painterResource(R.drawable.ic_close_24dp), contentDescription = null)
                            }
                        }
                    },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        SharedTransitionLayout(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    ScopeChip(state, scopes, pickerOpen) { pickerOpen = true }
                    if (state.loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        Spacer(Modifier.height(4.dp))
                    }
                    ResultList(state, onOpen = onShowInChat, onLongPress = { sheetFor = it })
                }
                if (scrim > 0f) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))
                }
                ScopePickerCard(state, scopes, pickerOpen, onDismiss = { pickerOpen = false })
            }
        }
    }

    sheetFor?.let { message ->
        SearchResultSheet(
            message = message,
            onDismiss = { sheetFor = null },
            onReply = { onReply(message) },
            onCopy = { onCopy(message) },
            onShare = { onShare(message) },
            onShow = { onShowInChat(message) },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.ScopeChip(
    state: SearchUiState,
    scopes: () -> List<SearchScope>,
    hidden: Boolean,
    onClick: () -> Unit,
) {
    val label = state.scopeUuid?.let { id -> scopes().firstOrNull { it.uuid == id }?.name }
        ?: stringResource(R.string.search_scope_all)
    AnimatedVisibility(visible = !hidden, enter = EnterTransition.None, exit = ExitTransition.None) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .sharedBounds(
                    rememberSharedContentState("search_scope"),
                    animatedVisibilityScope = this@AnimatedVisibility,
                    enter = fadeIn(quickFade),
                    exit = fadeOut(quickFade),
                    boundsTransform = springBounds,
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                )
                .clickable(onClick = onClick),
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.search_scope_title) + ": ", style = MaterialTheme.typography.labelLarge)
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                Icon(painterResource(R.drawable.ic_expand_more_24dp), contentDescription = null,
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.ScopePickerCard(
    state: SearchUiState,
    scopes: () -> List<SearchScope>,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = ExitTransition.None) {
        Box(
            modifier = Modifier.fillMaxSize().clickable(
                indication = null, interactionSource = remember { MutableInteractionSource() },
            ) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 48.dp)
                    .fillMaxWidth()
                    .sharedBounds(
                        rememberSharedContentState("search_scope"),
                        animatedVisibilityScope = this@AnimatedVisibility,
                        enter = fadeIn(quickFade),
                        exit = fadeOut(quickFade),
                        boundsTransform = springBounds,
                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    )
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            ) {
                val list = remember(visible) { if (visible) scopes() else emptyList() }
                Column(Modifier.padding(vertical = 16.dp)) {
                    Text(
                        stringResource(R.string.search_scope_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        ScopeRow(stringResource(R.string.search_scope_all), state.scopeUuid == null) {
                            state.scopeUuid = null; onDismiss()
                        }
                        list.forEach { scope ->
                            ScopeRow(scope.name, state.scopeUuid == scope.uuid) {
                                state.scopeUuid = scope.uuid; onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeRow(name: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

// ─── Results ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultList(
    state: SearchUiState,
    onOpen: (Message) -> Unit,
    onLongPress: (Message) -> Unit,
) {
    val context = LocalContext.current
    val queryEmpty = state.query.text.isBlank()
    if (state.results.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                stringResource(
                    if (queryEmpty || !state.searched) R.string.search_start_hint
                    else R.string.search_no_results
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    // Newest first, with a date label above each day.
    val days = remember(state.results) {
        state.results.groupBy { java.time.Instant.ofEpochMilli(it.timeSent)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate() }.toList()
    }
    val listState = rememberLazyListState()
    LaunchedEffect(state.results) { listState.scrollToItem(0) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        days.forEach { (_, messages) ->
            item(key = "day-" + messages.first().timeSent) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            formatDatePill(context, messages.first().timeSent),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            items(messages, key = { it.getUuid() ?: it.hashCode().toString() }) { message ->
                SearchResultItem(
                    message = message,
                    terms = state.highlight,
                    showChatName = state.scopeUuid == null,
                    onOpen = { onOpen(message) },
                    onLongPress = { onLongPress(message) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultItem(
    message: Message,
    terms: List<String>,
    showChatName: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val outgoing = message.status != Message.STATUS_RECEIVED
    val atEnd = outgoing && !AppearanceState.alignStart
    val bubbleColor = if (outgoing) MaterialTheme.colorScheme.primaryContainer else incomingBubbleColor()
    val contentColor = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    val time = remember(message.timeSent) {
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(message.timeSent))
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = if (atEnd) Alignment.End else Alignment.Start,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showChatName) {
                Text(
                    displayName(message.conversation),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Text(time, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            shape = rememberBubbleShape(firstOfGroup = true, lastOfGroup = true, outgoing = atEnd),
            color = bubbleColor,
            contentColor = contentColor,
            modifier = Modifier
                .padding(start = if (atEnd) 48.dp else 0.dp, end = if (atEnd) 0.dp else 48.dp)
                .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        ) {
            Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                LinkifiedMessageText(
                    message = message,
                    revision = 0,
                    contentColor = contentColor,
                    onLongPress = onLongPress,
                    highlightTerms = terms,
                )
            }
        }
        TextButton(onClick = onOpen) {
            Text(stringResource(R.string.show_in_chat))
        }
    }
}

// ─── Long-press sheet (same look as the chat's message options) ─────────────

private class SheetItem(val icon: Int, val label: String, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchResultSheet(
    message: Message,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onShow: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismissAnimated: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
    }
    val items = buildList {
        add(SheetItem(R.drawable.ic_reply_24dp, stringResource(R.string.reply), onReply))
        if (!message.isGeoUri) {
            add(SheetItem(R.drawable.ic_description_24dp, stringResource(android.R.string.copy), onCopy))
        }
        add(SheetItem(R.drawable.ic_share_24dp, stringResource(R.string.share), onShare))
        add(SheetItem(R.drawable.ic_forum_24dp, stringResource(R.string.show_in_chat), onShow))
    }
    ModalBottomSheet(onDismissRequest = dismissAnimated, sheetState = sheetState) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                stringResource(R.string.message_options),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
            items.forEachIndexed { index, item ->
                val top = if (index == 0) 20.dp else 5.dp
                val bottom = if (index == items.lastIndex) 20.dp else 5.dp
                Surface(
                    onClick = { dismissAnimated(); item.onClick() },
                    shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Icon(painterResource(item.icon), contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(item.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
