package eu.siacs.conversations.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
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
import eu.siacs.conversations.utils.BirthdayStore
import eu.siacs.conversations.utils.IrregularUnicodeDetector
import eu.siacs.conversations.utils.UIHelper
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.manager.ChatStateManager
import eu.siacs.conversations.xmpp.manager.JingleManager
import im.conversations.android.xmpp.model.stanza.Presence
import im.conversations.android.xmpp.model.state.Composing
import androidx.core.graphics.get
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** How long one turn of the gradient (in the dot, the label and the row) takes. */
private const val BIRTHDAY_GRADIENT_MS = 6000
/** How long the heart takes to turn once while it is somebody's birthday. */
private const val HEART_TURN_MS = 5000

/**
 * Small piece of state shared by the list and the conversation fragment. It deliberately carries
 * only an id and a direction: notification/deep-link chats never create an entry here, so they
 * retain the ordinary entrance with no invented source row.
 */
internal object ChatNavigationMotion {
    enum class Direction { OPENING, RETURNING }
    enum class ReturnMode { AUTOMATIC, GESTURE, CANCELLED, COMMITTED }
    data class Ticket(val conversationUuid: String, val direction: Direction)

    var ticket: Ticket? by mutableStateOf(null)
    var returnMode: ReturnMode by mutableStateOf(ReturnMode.AUTOMATIC)
    var returnProgress: Float by mutableStateOf(0f)
    var returnTouchY: Float by mutableStateOf(Float.NaN)
    var returnFromLeftEdge: Boolean by mutableStateOf(true)
    var returnOverlayInList: Boolean by mutableStateOf(false)
    var openingProgress: Float by mutableStateOf(0f)
    var sourceBounds: androidx.compose.ui.geometry.Rect? by mutableStateOf(null)
    var sourceAvatarBounds: androidx.compose.ui.geometry.Rect? by mutableStateOf(null)
    var sourceNameBounds: androidx.compose.ui.geometry.Rect? by mutableStateOf(null)
    var sourceStatusBounds: androidx.compose.ui.geometry.Rect? by mutableStateOf(null)
    var sourceRootOrigin: Offset by mutableStateOf(Offset.Zero)
    var sourceShape: RoundedCornerShape? by mutableStateOf(null)
    var targetAvatarBounds: androidx.compose.ui.geometry.Rect? by mutableStateOf(null)
    var targetNameBounds: androidx.compose.ui.geometry.Rect? by mutableStateOf(null)
    var targetStatusBounds: androidx.compose.ui.geometry.Rect? by mutableStateOf(null)
    private var lastListOriginUuid: String? = null

    fun beginOpen(
        conversation: Conversation,
        source: androidx.compose.ui.geometry.Rect,
        avatar: androidx.compose.ui.geometry.Rect?,
        name: androidx.compose.ui.geometry.Rect?,
        status: androidx.compose.ui.geometry.Rect?,
        shape: RoundedCornerShape,
        rootOrigin: Offset,
    ) {
        val uuid = conversation.getUuid()
        lastListOriginUuid = uuid
        sourceBounds = source
        sourceAvatarBounds = avatar
        sourceNameBounds = name
        sourceStatusBounds = status
        sourceShape = shape
        sourceRootOrigin = rootOrigin
        targetAvatarBounds = null
        targetNameBounds = null
        targetStatusBounds = null
        openingProgress = 0f
        ticket = Ticket(uuid, Direction.OPENING)
    }

    fun updateOpeningProgress(progress: Float) {
        if (ticket?.direction == Direction.OPENING) openingProgress = progress.coerceIn(0f, 1f)
    }

    fun updateTargetAvatar(bounds: androidx.compose.ui.geometry.Rect) { targetAvatarBounds = bounds }
    fun updateTargetName(bounds: androidx.compose.ui.geometry.Rect) { targetNameBounds = bounds }
    fun updateTargetStatus(bounds: androidx.compose.ui.geometry.Rect?) { targetStatusBounds = bounds }

    fun destinationIdentityAlpha(): Float {
        val active = ticket ?: return 1f
        val p = if (active.direction == Direction.OPENING) openingProgress else 1f - returnProgress
        val headerProgress = (p / 0.70f).coerceIn(0f, 1f)
        val arrival = ((headerProgress - 0.88f) / 0.12f).coerceIn(0f, 1f)
        return when {
            active.direction == Direction.OPENING -> arrival
            returnMode == ReturnMode.CANCELLED -> arrival
            else -> 0f
        }
    }

    fun beginReturn(
        conversation: Conversation,
        gesture: Boolean = false,
        fromLeftEdge: Boolean = true,
    ): Boolean {
        val uuid = conversation.getUuid()
        if (lastListOriginUuid != uuid) return false
        ticket = Ticket(uuid, Direction.RETURNING)
        returnOverlayInList = false
        returnMode = if (gesture) ReturnMode.GESTURE else ReturnMode.AUTOMATIC
        returnProgress = 0f
        returnTouchY = Float.NaN
        returnFromLeftEdge = fromLeftEdge
        return true
    }

    fun updateReturnProgress(progress: Float, touchY: Float) {
        if (ticket?.direction == Direction.RETURNING) {
            returnProgress = progress.coerceIn(0f, 1f)
            if (!touchY.isNaN()) returnTouchY = touchY
        }
    }

    fun cancelReturn() {
        if (ticket?.direction == Direction.RETURNING && returnMode == ReturnMode.GESTURE) {
            returnMode = ReturnMode.CANCELLED
        }
    }

    fun commitReturn() {
        if (ticket?.direction == Direction.RETURNING && returnMode == ReturnMode.GESTURE) {
            returnMode = ReturnMode.COMMITTED
        }
    }

    fun showReturnOverlayInList() { returnOverlayInList = true }

    fun clear(ticket: Ticket) {
        if (this.ticket == ticket) {
            this.ticket = null
            returnMode = ReturnMode.AUTOMATIC
            returnProgress = 0f
            returnTouchY = Float.NaN
            returnFromLeftEdge = true
            returnOverlayInList = false
            openingProgress = 0f
            sourceBounds = null
            sourceAvatarBounds = null
            sourceNameBounds = null
            sourceStatusBounds = null
            sourceRootOrigin = Offset.Zero
            sourceShape = null
            targetAvatarBounds = null
            targetNameBounds = null
            targetStatusBounds = null
        }
    }
}

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

// "Away"/"Extended away" are gendered past-tense verbs in Russian ("Отошёл"/"Отошла") — the rest
// of the statuses aren't (nouns/adjectives with no gender agreement), so isFeminine only matters
// for those two cases.
private fun presenceStringRes(availability: Presence.Availability?, isFeminine: Boolean): Int? =
    when (availability) {
        Presence.Availability.CHAT, Presence.Availability.ONLINE -> R.string.presence_online
        Presence.Availability.AWAY ->
            if (isFeminine) R.string.presence_away_feminine else R.string.presence_away
        Presence.Availability.XA ->
            if (isFeminine) R.string.presence_xa_feminine else R.string.presence_xa
        Presence.Availability.DND -> R.string.presence_dnd
        Presence.Availability.OFFLINE -> R.string.presence_offline
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
        searchHost: SearchHostState,
        searchActions: SearchHostActions,
        listActions: ConversationListActions,
        onDownloadComplete: () -> Unit = {},
    ) {
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            ImpulseTheme {
                val demoContext = LocalContext.current
                val demoPrefs = remember(demoContext) { eu.siacs.conversations.utils.FeatureFlagPreferences(demoContext) }
                var demoEnabled by remember { mutableStateOf(demoPrefs.isEnabled(eu.siacs.conversations.FeatureFlag.DEMO_MODE)) }
                androidx.compose.runtime.DisposableEffect(demoContext) {
                    val prefs = demoContext.getSharedPreferences("feature_flag_prefs", android.content.Context.MODE_PRIVATE)
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        if (key == null || key == eu.siacs.conversations.FeatureFlag.DEMO_MODE.key)
                            demoEnabled = demoPrefs.isEnabled(eu.siacs.conversations.FeatureFlag.DEMO_MODE)
                    }
                    prefs.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
                }
                // refresh() advances this when the service binds, including after a theme change.
                val backendRevision = state.revision.intValue
                val account = (demoContext as? XmppActivity)?.xmppConnectionService?.accounts
                    ?.let { accounts -> accounts.firstOrNull { it.isEnabled } ?: accounts.firstOrNull() }
                val language = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
                val demoSession = remember(demoEnabled, account, language, backendRevision) {
                    if (demoEnabled) DemoMemory.get(account, language) else { DemoMemory.clear(); null }
                }
                if (demoEnabled && demoSession == null) {
                    // Never expose the real list while demo mode is waiting for the backend.
                    Box(Modifier.fillMaxSize()) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.align(Alignment.Center))
                        androidx.compose.material3.TextButton(
                            onClick = { demoPrefs.setOverride(eu.siacs.conversations.FeatureFlag.DEMO_MODE, false) },
                            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                        ) { Text(stringResource(R.string.demo_mode_exit)) }
                    }
                    LaunchedEffect(Unit) { fab.hide() }
                    return@ImpulseTheme
                }
                var demoChat by remember(demoSession) { mutableStateOf<DemoConversation?>(null) }
                LaunchedEffect(searchHost.expanded, demoEnabled, demoChat) {
                    if (searchHost.expanded || demoEnabled) fab.hide() else fab.show()
                }
                if (demoSession != null && demoChat != null) {
                    DemoChatHost(demoSession, demoChat!!) { demoChat = null }
                    return@ImpulseTheme
                }
                // Liquid glass experiment: the search row floats over the list, so the list starts
                // below it instead of the (then absent) search bar pushing it down.
                val glass = rememberLiquidGlassEnabled()
                // The list runs under the gesture bar in glass mode; the button must clear it.
                val navInset = if (glass) eu.siacs.conversations.ui.glass.rememberGlassNavInset() else 0.dp
                if (glass) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    LaunchedEffect(navInset) {
                        (fab.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let {
                            it.bottomMargin = with(density) { (16.dp + navInset).roundToPx() }
                            fab.requestLayout()
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    val demoSearch = remember(searchActions) { SearchHostActions({ emptyList() }, {}, {}, searchActions.onScanQr, searchActions.onShowQr, searchActions.onEasyInvite, searchActions.onManageAccount) }
                    ConversationsSearchHost(host = searchHost, actions = if (demoSession == null) searchActions else demoSearch) {
                        Column {
                            if (demoSession == null) ConnectingStrip(isConnecting = state.isConnecting.value)
                            // The glass top row floats over the list, so the list's first row (and the
                            // weak-connection banner) start below it; the banner takes room from the list
                            // instead of covering its first chats.
                            val base = if (glass) LiquidTopRowHeight +
                                androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                            else 0.dp
                            var bannerPx by remember { mutableIntStateOf(0) }
                            val bannerDp = with(LocalDensity.current) { bannerPx.toDp() }
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                ConversationList(
                                    conversations = demoSession?.chats ?: state.list,
                                    revision = state.revision.intValue,
                                    onConversationClick = { if (demoSession != null) demoChat = it as? DemoConversation else onConversationClick.onClick(it) },
                                    actions = if (demoSession == null) listActions else NoConversationListActions,
                                    contentPadding = PaddingValues(top = base + bannerDp, bottom = if (glass) navInset else 0.dp),
                                    onFirstVisibleIndexChanged = { index ->
                                        if (demoSession == null) composeView.post { if (index > 0) fab.shrink() else fab.extend() }
                                    },
                                )
                                if (demoSession == null) WeakConnectionBanner(Modifier.padding(top = base).onSizeChanged { bannerPx = it.height })
                            }
                        }
                    }
                    if (demoSession == null) DownloadProgressBar(
                        onComplete = onDownloadComplete,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    )
                    if (demoSession != null) {
                        androidx.compose.material3.TextButton(
                            onClick = { demoPrefs.setOverride(eu.siacs.conversations.FeatureFlag.DEMO_MODE, false) },
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 16.dp + navInset),
                        ) { Text(stringResource(R.string.demo_mode_exit), style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImpulseTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    // Same Samsung fix as the chat screen: without it the search bar (surfaceContainerHigh)
    // came out as a dark grey slab on Samsung phones.
    val colorScheme = dynamicOrStaticColorScheme(context, isDark).withSamsungSurfaceFix(isDark)
    MaterialTheme(colorScheme = colorScheme, content = content)
}

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
    actions: ConversationListActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    if (conversations.isEmpty()) {
        LaunchedEffect(Unit) { onFirstVisibleIndexChanged(0) }
        EmptyConversationsHint(actions = actions, modifier = modifier.fillMaxSize())
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            onFirstVisibleIndexChanged(index)
        }
    }

    val context = LocalContext.current
    // Only changes at the next midnight, so the birthday rows start and stop on time even if the
    // list is left open all night.
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(today) {
        val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay()
        delay((Duration.between(LocalDateTime.now(), nextMidnight).toMillis() + 500).milliseconds)
        today = LocalDate.now()
    }
    // Somebody's birthday goes above everything, pinned chats included, and keeps its usual order
    // among the other birthdays. The rest keep their order.
    val (celebrating, others) = remember(conversations, revision, today) {
        val store = BirthdayStore(context)
        conversations.partition { birthdayToday(store, it, today) }
    }
    val ordered = celebrating + others
    val onlyNews = ordered.all {
        it.getMode() == Conversational.MODE_MULTI &&
            it.getAddress().asBareJid() == eu.siacs.conversations.Config.NEWS_CHANNEL.asBareJid()
    }
    // The chat a long-press opened the sheet for; null when no sheet is up.
    var sheetFor by remember { mutableStateOf<Conversation?>(null) }
    val scope = rememberCoroutineScope()
    val rowBounds = remember { mutableMapOf<String, androidx.compose.ui.geometry.Rect>() }
    val rowAvatarBounds = remember { mutableMapOf<String, androidx.compose.ui.geometry.Rect>() }
    val rowNameBounds = remember { mutableMapOf<String, androidx.compose.ui.geometry.Rect>() }
    val rowStatusBounds = remember { mutableMapOf<String, androidx.compose.ui.geometry.Rect>() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val navigationTicket = ChatNavigationMotion.ticket
    val transitionConversation = navigationTicket?.let { ticket ->
        ordered.firstOrNull { it.getUuid() == ticket.conversationUuid }
    }
    val transitionIndex = transitionConversation?.let(ordered::indexOf) ?: -1
    val transitionShape = if (transitionIndex >= 0) conversationItemShape(transitionIndex, ordered.size)
        else RoundedCornerShape(16.dp)
    val transitionBounds = navigationTicket?.let { rowBounds[it.conversationUuid] }
    val listFade by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (navigationTicket?.direction == ChatNavigationMotion.Direction.OPENING) 0f else 1f,
        animationSpec = tween(150),
        label = "chatListTransitionFade",
    )
    LaunchedEffect(navigationTicket) {
        if (navigationTicket?.direction == ChatNavigationMotion.Direction.OPENING) {
            withTimeoutOrNull(1_000L) {
                snapshotFlow {
                    ChatNavigationMotion.targetAvatarBounds != null && ChatNavigationMotion.targetNameBounds != null
                }.first { it }
            }
            val opening = Animatable(0f)
            opening.animateTo(1f, tween(310, easing = androidx.compose.animation.core.FastOutSlowInEasing)) {
                ChatNavigationMotion.updateOpeningProgress(value)
            }
            delay(50)
            ChatNavigationMotion.ticket?.let(ChatNavigationMotion::clear)
        }
    }

    Box(
        modifier = modifier.fillMaxSize().onGloballyPositioned {
            rootOrigin = it.boundsInWindow().topLeft
            rootSize = it.size
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = listFade },
            contentPadding = contentPadding,
        ) {
            itemsIndexed(
                ordered,
                key = { _, c -> c.getUuid() },
            ) { index, conversation ->
                val shape = conversationItemShape(index, ordered.size)
                ConversationItem(
                    conversation = conversation,
                    revision = revision,
                    onClick = {
                        // A list-originated chat gets an expanding source card. The short delay
                        // leaves that card on screen while the fragment transaction is prepared.
                        if (ChatNavigationMotion.ticket == null) {
                            val source = rowBounds[conversation.getUuid()] ?: return@ConversationItem
                            ChatNavigationMotion.beginOpen(
                                conversation,
                                source,
                                rowAvatarBounds[conversation.getUuid()],
                                rowNameBounds[conversation.getUuid()],
                                rowStatusBounds[conversation.getUuid()],
                                shape,
                                rootOrigin,
                            )
                            scope.launch {
                                delay(50)
                                onConversationClick(conversation)
                            }
                        }
                    },
                    onLongClick = { sheetFor = conversation },
                    shape = shape,
                    isBirthday = index < celebrating.size,
                    identityAlpha = when {
                        navigationTicket?.conversationUuid != conversation.getUuid() -> 1f
                        navigationTicket.direction == ChatNavigationMotion.Direction.OPENING -> 0f
                        else -> ((ChatNavigationMotion.returnProgress - 0.88f) / 0.12f).coerceIn(0f, 1f)
                    },
                    onAvatarBounds = { rowAvatarBounds[conversation.getUuid()] = it },
                    onNameBounds = { rowNameBounds[conversation.getUuid()] = it },
                    onStatusBounds = { bounds ->
                        if (bounds == null) rowStatusBounds.remove(conversation.getUuid())
                        else rowStatusBounds[conversation.getUuid()] = bounds
                    },
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .onGloballyPositioned { coordinates ->
                            rowBounds[conversation.getUuid()] = coordinates.boundsInWindow()
                        },
                )
            }
            if (onlyNews) {
                item(key = "first-contact-hint") {
                    EmptyConversationsHint(actions = actions, modifier = Modifier.fillParentMaxWidth().fillParentMaxHeight(0.75f))
                }
            }
        }

        if (navigationTicket?.direction == ChatNavigationMotion.Direction.RETURNING && ChatNavigationMotion.returnOverlayInList && transitionConversation != null && transitionBounds != null && rootSize.width > 0) {
            ChatNavigationOverlay(
                ticket = navigationTicket,
                conversation = transitionConversation,
                source = transitionBounds,
                sourceAvatar = ChatNavigationMotion.sourceAvatarBounds,
                sourceName = ChatNavigationMotion.sourceNameBounds,
                sourceStatus = rowStatusBounds[transitionConversation.getUuid()],
                sourceShape = transitionShape,
                rootOrigin = rootOrigin,
                rootSize = rootSize,
                drawCard = navigationTicket.direction == ChatNavigationMotion.Direction.RETURNING,
                progressOverride = 1f - ChatNavigationMotion.returnProgress,
            )
        }
    }

    sheetFor?.let { conversation ->
        ConversationActionSheet(
            conversation = conversation,
            actions = actions,
            onDismiss = { sheetFor = null },
        )
    }
}

/** Visual bridge across the list and chat fragment roots. */
@Composable
internal fun ChatNavigationOverlay(
    ticket: ChatNavigationMotion.Ticket,
    conversation: Conversation,
    source: androidx.compose.ui.geometry.Rect,
    sourceAvatar: androidx.compose.ui.geometry.Rect?,
    sourceName: androidx.compose.ui.geometry.Rect?,
    sourceStatus: androidx.compose.ui.geometry.Rect?,
    sourceShape: RoundedCornerShape,
    rootOrigin: Offset,
    rootSize: IntSize,
    drawCard: Boolean = true,
    progressOverride: Float? = null,
) {
    val density = LocalDensity.current
    val progress = remember(ticket, source, rootSize) {
        Animatable(if (ticket.direction == ChatNavigationMotion.Direction.RETURNING) 1f else 0f)
    }
    val returnMode = ChatNavigationMotion.returnMode
    val returnProgress = ChatNavigationMotion.returnProgress
    val returnTouchY = ChatNavigationMotion.returnTouchY
    val returnFromLeftEdge = ChatNavigationMotion.returnFromLeftEdge
    LaunchedEffect(ticket, source, rootSize) {
        if (progressOverride == null && ticket.direction == ChatNavigationMotion.Direction.OPENING) {
            progress.animateTo(1f, tween(310, easing = androidx.compose.animation.core.FastOutSlowInEasing)) {
                ChatNavigationMotion.updateOpeningProgress(value)
            }
        }
    }

    val p = progressOverride ?: ChatNavigationMotion.openingProgress
    fun localRect(rect: androidx.compose.ui.geometry.Rect) = androidx.compose.ui.geometry.Rect(
        rect.left - rootOrigin.x, rect.top - rootOrigin.y,
        rect.right - rootOrigin.x, rect.bottom - rootOrigin.y,
    )
    val sourceLocal = localRect(source)
    val left = sourceLocal.left
    val top = sourceLocal.top
    val width = source.width.coerceAtLeast(1f)
    val height = source.height.coerceAtLeast(1f)
    val scaleX = 1f + (rootSize.width / width - 1f) * p
    val scaleY = 1f + (rootSize.height / height - 1f) * p
    val headerP = (p / 0.70f).coerceIn(0f, 1f)
    val sourceOffset = IntOffset(left.toInt(), top.toInt())
    val sourceAvatarRect = sourceAvatar?.let(::localRect) ?: androidx.compose.ui.geometry.Rect(
        left + with(density) { 8.dp.toPx() },
        top + (height - with(density) { 56.dp.toPx() }) / 2f,
        left + with(density) { 64.dp.toPx() },
        top + (height + with(density) { 56.dp.toPx() }) / 2f,
    )
    val sourceNameRect = sourceName?.let(::localRect) ?: androidx.compose.ui.geometry.Rect(
        left + with(density) { 76.dp.toPx() }, top + with(density) { 13.dp.toPx() },
        left + with(density) { 256.dp.toPx() }, top + with(density) { 38.dp.toPx() },
    )
    val targetAvatarRect = ChatNavigationMotion.targetAvatarBounds?.let(::localRect)
        ?: androidx.compose.ui.geometry.Rect(with(density) { 56.dp.toPx() }, with(density) { 12.dp.toPx() }, with(density) { 96.dp.toPx() }, with(density) { 52.dp.toPx() })
    val targetNameRect = ChatNavigationMotion.targetNameBounds?.let(::localRect)
        ?: androidx.compose.ui.geometry.Rect(with(density) { 108.dp.toPx() }, with(density) { 16.dp.toPx() }, with(density) { 300.dp.toPx() }, with(density) { 40.dp.toPx() })
    val targetStatusRect = ChatNavigationMotion.targetStatusBounds?.let(::localRect)
        ?: androidx.compose.ui.geometry.Rect(targetNameRect.left, targetNameRect.bottom, targetNameRect.right, targetNameRect.bottom + with(density) { 20.dp.toPx() })
    fun lerp(start: Float, end: Float) = start + (end - start) * headerP
    val destinationIdentityAlpha = ((headerP - 0.88f) / 0.12f).coerceIn(0f, 1f)
    val sourceIdentityAlpha = ((1f - headerP - 0.88f) / 0.12f).coerceIn(0f, 1f)
    val identityAlpha = when {
        ticket.direction == ChatNavigationMotion.Direction.OPENING -> 1f - destinationIdentityAlpha
        returnMode == ChatNavigationMotion.ReturnMode.CANCELLED -> 1f - destinationIdentityAlpha
        else -> 1f - sourceIdentityAlpha
    }
    // Predictive back supplies the finger's vertical coordinate too. Let it bend the card's
    // path slightly in the middle of the gesture, then settle exactly into the source row at
    // either end; that makes up/down movement visible without disturbing message layout.
    val verticalGestureBias = if (returnTouchY.isNaN()) 0f else {
        val relativeY = ((returnTouchY - rootOrigin.y) / rootSize.height - 0.5f).coerceIn(-0.5f, 0.5f)
        with(density) { 24.dp.toPx() } * relativeY * 2f * returnProgress * (1f - returnProgress)
    }
    val edgeDirection = if (returnFromLeftEdge) 1f else -1f
    val horizontalGestureBias = if (returnMode == ChatNavigationMotion.ReturnMode.GESTURE) {
        with(density) { 28.dp.toPx() } * edgeDirection * returnProgress * (1f - returnProgress)
    } else 0f

    val context = LocalContext.current
    val isGroup = conversation.getMode() == Conversational.MODE_MULTI
    val availability = remember(conversation) {
        if (isGroup) null else runCatching { conversation.getContact().getShownStatus() }.getOrNull()
    }
    val hasOngoingCall = remember(conversation) {
        if (isGroup) false else runCatching {
            conversation.getAccount().xmppConnection.getManager(JingleManager::class.java)
                .getOngoingRtpConnection(conversation.getContact()).isPresent
        }.getOrDefault(false)
    }
    val isTyping = remember(conversation) {
        if (isGroup) false else runCatching {
            conversation.getAccount().xmppConnection?.getManager(ChatStateManager::class.java)
                ?.getIncoming(conversation.getAddress()) == Composing::class.java
        }.getOrDefault(false)
    }
    val isBirthday = remember(conversation) {
        birthdayToday(BirthdayStore(context), conversation, LocalDate.now())
    }
    val statusRes = if (isTyping) R.string.typing_indicator else presenceStringRes(availability, false)
    val statusText = statusRes?.let { stringResource(it) }.let { text ->
        if (text != null && !isTyping) "● $text" else text
    }
    val statusStartX = sourceStatus?.let { localRect(it).left }
        ?: (sourceNameRect.left + with(density) { 120.dp.toPx() })
    val statusStartY = sourceStatus?.let { localRect(it).top } ?: sourceNameRect.top

    Box(Modifier.fillMaxSize()) {
        if (drawCard) Surface(
            shape = sourceShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .offset { sourceOffset }
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                    translationX = -left * p + horizontalGestureBias
                    translationY = -top * p + verticalGestureBias
                }
                .size(with(density) { width.toDp() }, with(density) { height.toDp() }),
        ) {}
        ConversationAvatar(
            conversation = conversation,
            availability = availability,
            hasOngoingCall = hasOngoingCall,
            isTyping = isTyping,
            isBirthday = isBirthday,
            cardHeightPx = height.toInt(),
            circleMorphProgress = headerP,
            modifier = Modifier
                .offset { IntOffset((lerp(sourceAvatarRect.left, targetAvatarRect.left) + horizontalGestureBias).toInt(), (lerp(sourceAvatarRect.top, targetAvatarRect.top) + verticalGestureBias).toInt()) }
                .size(
                    with(density) { lerp(sourceAvatarRect.width, targetAvatarRect.width).coerceAtLeast(1f).toDp() },
                    with(density) { lerp(sourceAvatarRect.height, targetAvatarRect.height).coerceAtLeast(1f).toDp() },
                )
                .graphicsLayer {
                    alpha = identityAlpha
                    this.transformOrigin = TransformOrigin.Center
                },
        )
        Text(
            text = when (val name = conversation.name) {
                is Jid -> IrregularUnicodeDetector.style(context, name).toString()
                else -> name?.toString().orEmpty()
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (conversation.isRead) FontWeight.Normal else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset { IntOffset((lerp(sourceNameRect.left, targetNameRect.left) + horizontalGestureBias).toInt(), (lerp(sourceNameRect.top, targetNameRect.top) + verticalGestureBias).toInt()) }
                .width(with(density) { lerp(sourceNameRect.width, targetNameRect.width).coerceAtLeast(1f).toDp() })
                .graphicsLayer { alpha = identityAlpha },
        )
        if (statusText != null) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = if (isTyping) MaterialTheme.colorScheme.primary else presenceColor(availability),
                maxLines = 1,
                modifier = Modifier
                    .offset { IntOffset((lerp(sourceStatus?.let { localRect(it).left } ?: statusStartX, targetStatusRect.left) + horizontalGestureBias).toInt(), (lerp(sourceStatus?.let { localRect(it).top } ?: statusStartY, targetStatusRect.top) + verticalGestureBias).toInt()) }
                    .graphicsLayer { alpha = identityAlpha },
            )
        }
    }
}

/** A one-to-one chat whose contact has a birthday on [today], as far as the phone knows it. */
private fun birthdayToday(store: BirthdayStore, conversation: Conversation, today: LocalDate): Boolean {
    if (conversation.getMode() != Conversational.MODE_SINGLE) return false
    val accountUuid = conversation.getAccount().getUuid() ?: return false
    return try {
        store.get(accountUuid, conversation.getAddress().asBareJid().toString())?.isOn(today) == true
    } catch (_: Exception) {
        false
    }
}

/**
 * Drives the birthday gradient: a value from 0 to 1 that restarts every [BIRTHDAY_GRADIENT_MS].
 * Only runs while [enabled], so ordinary rows do not animate at all.
 */
@Composable
private fun rememberBirthdayPhase(enabled: Boolean): Animatable<Float, AnimationVector1D> {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            phase.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            phase.animateTo(1f, tween(durationMillis = BIRTHDAY_GRADIENT_MS, easing = LinearEasing))
            phase.snapTo(0f)
        }
    }
    return phase
}

internal fun conversationItemShape(index: Int, total: Int): RoundedCornerShape {
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
private fun EmptyConversationsHint(actions: ConversationListActions, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
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
        // The first thing to do with an empty list: add someone. One button that opens into the two ways.
        androidx.compose.material3.FilledTonalButton(
            onClick = { open = !open },
            modifier = Modifier.padding(top = 20.dp),
        ) {
            Text(stringResource(R.string.empty_list_add_first_contact))
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(spring(stiffness = 380f, dampingRatio = 0.8f)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = 380f, dampingRatio = 0.8f)) + fadeOut(),
        ) {
            Row(
                Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                androidx.compose.material3.OutlinedButton(onClick = { actions.scanQrCode() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.empty_list_qr_code), maxLines = 1)
                }
                androidx.compose.material3.OutlinedButton(onClick = { actions.addContactByAddress() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.empty_list_address), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    revision: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    shape: RoundedCornerShape,
    isBirthday: Boolean,
    identityAlpha: Float,
    onAvatarBounds: (androidx.compose.ui.geometry.Rect) -> Unit,
    onNameBounds: (androidx.compose.ui.geometry.Rect) -> Unit,
    onStatusBounds: (androidx.compose.ui.geometry.Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The gradient runs on birthday rows only; everything else stays exactly as it was.
    val birthdayPhase = rememberBirthdayPhase(isBirthday)
    val birthdayColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primary,
    )
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
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

    val isFeminine: Boolean = remember(conversation, revision) {
        conversation.getMode() == Conversational.MODE_SINGLE && try {
            UIHelper.resolveGender(context, conversation.getContact()) ==
                eu.siacs.conversations.utils.NameGenderGuesser.Gender.FEMININE
        } catch (_: Exception) { false }
    }

    val isTyping: Boolean = remember(conversation, revision) {
        conversation.getMode() == Conversational.MODE_SINGLE && try {
            val state = conversation.getAccount().xmppConnection
                ?.getManager(ChatStateManager::class.java)
                ?.getIncoming(conversation.getAddress())
            state == Composing::class.java
        } catch (_: Exception) { false }
    }

    val avatarItemDistance = dimensionResource(R.dimen.avatar_item_distance)
    var cardHeightPx by remember { mutableIntStateOf(0) }

    // Avatar is outside the Card so it can draw above the card's top edge without being
    // clipped by Material3's Surface, which always clips children to the card shape.
    Box(modifier = modifier.fillMaxWidth()) {
        // Not Card(onClick): a plain card has no long-press, so the click and the long-press go on
        // the modifier, clipped to the same shape the card is drawn with.
        Card(
            shape = shape,
            // A birthday row paints its own background (the gradient below), so the card's
            // solid colour is dropped for it.
            colors = CardDefaults.cardColors(
                containerColor = if (isBirthday) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .onSizeChanged { cardHeightPx = it.height }.then(
                if (isBirthday) Modifier.clip(shape).drawBehind {
                    drawRect(surfaceContainer)
                    // The same colours, faint, sliding across the row.
                    val span = size.width
                    val shift = birthdayPhase.value * span
                    drawRect(
                        Brush.linearGradient(
                            colors = birthdayColors.map { it.copy(alpha = 0.22f) },
                            start = Offset(-shift, 0f),
                            end = Offset(span - shift, 0f),
                            tileMode = TileMode.Repeated,
                        ),
                    )
                } else Modifier
            ),
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
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                            .onGloballyPositioned { onNameBounds(it.boundsInWindow()) }
                            .graphicsLayer { alpha = identityAlpha },
                    )
                    val presenceText = when {
                        isTyping -> stringResource(R.string.typing_indicator)
                        else -> presenceStringRes(availability, isFeminine)?.let { "● ${stringResource(it)}" }
                    }
                    if (isBirthday) {
                        BirthdayStatusLabel(birthdayPhase, Modifier.padding(end = 6.dp))
                    } else if (presenceText != null) {
                        Text(
                            text = presenceText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isTyping) MaterialTheme.colorScheme.primary else presenceColor(availability),
                            maxLines = 1,
                            modifier = Modifier.padding(end = 6.dp).onGloballyPositioned {
                                onStatusBounds(it.boundsInWindow())
                            }.graphicsLayer { alpha = identityAlpha },
                        )
                    }
                    SideEffect {
                        if (presenceText == null || isBirthday) onStatusBounds(null)
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
                                message.type != Message.TYPE_STATUS -> "${stringResource(R.string.me)}:"
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
            isBirthday = isBirthday,
            cardHeightPx = cardHeightPx,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .onGloballyPositioned { onAvatarBounds(it.boundsInWindow()) }
                .graphicsLayer { alpha = identityAlpha },
        )
    }
}

@Composable
private fun ConversationAvatar(
    conversation: Conversation,
    availability: Presence.Availability?,
    hasOngoingCall: Boolean,
    isTyping: Boolean,
    isBirthday: Boolean,
    cardHeightPx: Int,
    circleMorphProgress: Float? = null,
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

    val hasRealPhoto = try {
        if (isGroup) true
        else {
            val contact = conversation.getContact()
            contact.getAvatar() != null || contact.profilePhoto != null
        }
    } catch (_: Exception) { false }
    // What identifies the picture: a new avatar is a new key, so its cut-out is made again.
    val cutoutKey = if (!isGroup && hasRealPhoto) {
        try { AvatarSegmenter.key(conversation.getContact()) } catch (_: Exception) { null }
    } else null

    val avatarState = remember(conversation.getUuid()) { mutableStateOf<ImageBitmap?>(null) }
    val segmentedState = remember(conversation.getUuid()) { mutableStateOf<ImageBitmap?>(null) }
    val personBoundsState = remember(conversation.getUuid()) { mutableStateOf<PersonBounds?>(null) }
    // False while the photo's cut-out is being found or made: a loading indicator is shown then.
    val readyState = remember(conversation.getUuid()) { mutableStateOf(false) }
    val avatarBitmap by avatarState
    val segmentedBitmap by segmentedState
    val personBounds by personBoundsState
    val ready by readyState

    LaunchedEffect(conversation, cutoutKey) {
        val activity = context as? XmppActivity ?: return@LaunchedEffect
        if (cutoutKey != null && !AvatarSegmenter.isCached(context, cutoutKey)) {
            // A new picture (or never seen): the old one must not stay on show.
            avatarState.value = null
            segmentedState.value = null
            personBoundsState.value = null
            readyState.value = false
        }
        val bm = withContext(Dispatchers.IO) {
            activity.avatarService().get(conversation, avatarLoadSizePx, false)
        } ?: return@LaunchedEffect
        if (cutoutKey == null) {
            avatarState.value = bm.asImageBitmap()
            segmentedState.value = null
            personBoundsState.value = null
            readyState.value = true
            return@LaunchedEffect
        }
        val cut = AvatarSegmenter.cutout(context, cutoutKey, bm)
        avatarState.value = bm.asImageBitmap()
        segmentedState.value = cut?.bitmap?.asImageBitmap()
        personBoundsState.value = cut?.let { PersonBounds(it.top, it.bottom, it.centerX) }
        readyState.value = true
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

    // On somebody's birthday the outline is a heart for the whole day; otherwise the usual shape.
    val heart = remember { MATERIAL_SHAPE_CATALOG.first { it.first == "Heart" }.second }
    val circle = remember { MATERIAL_SHAPE_CATALOG.first { it.first == "Circle" }.second }
    val targetShape = remember(isGroup, availability, hasOngoingCall, isTyping, isBirthday) {
        if (isBirthday) heart else presenceShape(isGroup, availability, hasOngoingCall, isTyping)
    }
    // Turns the heart while it is the birthday. When the day ends it finishes the turn in progress,
    // stands upright again, and the outline morphs back to the usual shape (the morph above).
    val spin = remember { Animatable(0f) }
    LaunchedEffect(isBirthday) {
        if (isBirthday) {
            while (true) {
                spin.animateTo(spin.value + 360f, tween(durationMillis = HEART_TURN_MS, easing = LinearEasing))
            }
        } else if (spin.value != 0f) {
            spin.animateTo(ceil(spin.value / 360f) * 360f, tween(durationMillis = 900))
            spin.snapTo(0f)
        }
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
    val circleMorph = remember(targetShape, circle) { Morph(targetShape, circle) }
    val activeMorph = if (circleMorphProgress == null) morph else circleMorph
    val progress = circleMorphProgress ?: morphProgress.value

    val reusedPath = remember { androidx.compose.ui.graphics.Path() }
    val reusedMatrix = remember { android.graphics.Matrix() }
    val fallbackColor = MaterialTheme.colorScheme.primaryContainer

    // Computed here so both the layout modifier and DrawScope share the same value.
    val headOverflowPx = maxOverflowPx * overflowAnim.value

    // Canvas is expanded upward by headOverflowPx so the overflow zone sits at positive y.
    // The morph shape is translated down by headOverflowPx to stay at the original card position.
    // Reporting canvasSizePx as the layout height keeps sibling elements (card padding) stable.
    Box(modifier = modifier.width(56.dp), contentAlignment = Alignment.Center) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier
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
        // Only the outline turns; the photo inside it stays upright.
        reusedMatrix.postRotate(spin.value, size.width / 2f, headOverflowPx + size.width / 2f)
        activeMorph.toPath(progress, reusedPath)
        reusedPath.asAndroidPath().transform(reusedMatrix)

        val bm = avatarBitmap
        if (bm == null || !ready) {
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
    // The photo's cut-out is being found or made (it is kept after that): morphing shapes show it.
    if (cutoutKey != null && !ready) {
        androidx.compose.material3.LoadingIndicator(
            modifier = Modifier.size(34.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    }
}

/**
 * "Celebrating birthday" in the status place: a dot that turns through the theme colours, and a
 * label whose gradient slides along the letters. Both follow the same [phase] as the row.
 */
@Composable
private fun BirthdayStatusLabel(phase: Animatable<Float, AnimationVector1D>, modifier: Modifier = Modifier) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primary,
    )
    // One repeat of the colours spans this many pixels; a full cycle moves exactly one repeat.
    val span = with(LocalDensity.current) { 160.dp.toPx() }
    val shift = phase.value * span
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(7.dp).drawBehind {
                rotate(phase.value * 360f) { drawCircle(Brush.sweepGradient(colors)) }
            },
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.birthday_celebrating_status),
            style = MaterialTheme.typography.labelSmall.copy(
                brush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(-shift, 0f),
                    end = Offset(span - shift, 0f),
                    tileMode = TileMode.Repeated,
                ),
            ),
            maxLines = 1,
        )
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
            actions = NoConversationListActions,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
