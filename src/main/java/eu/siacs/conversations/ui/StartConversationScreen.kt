package eu.siacs.conversations.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Contact
import eu.siacs.conversations.entities.ListItem
import eu.siacs.conversations.utils.IrregularUnicodeDetector
import eu.siacs.conversations.utils.XEP0392Helper
import eu.siacs.conversations.xmpp.manager.BlockingManager
import im.conversations.android.model.Bookmark
import im.conversations.android.model.DynamicTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Observable lists Java pushes into as accounts/filtering/backend events happen. */
class StartConversationListState {
    internal val contacts: SnapshotStateList<ListItem> = mutableStateListOf()
    internal val conferences: SnapshotStateList<ListItem> = mutableStateListOf()
    internal val revision = mutableIntStateOf(0)
    val currentTab = mutableIntStateOf(0)
    // Not named isRefreshing: Kotlin generates a prefix-less Java accessor for "isXxx"-named
    // properties, which is easy to get wrong from Java call sites — avoid the ambiguity.
    val refreshing = mutableStateOf(false)
    val fabExpanded = mutableStateOf(false)

    fun updateContacts(source: List<ListItem>) {
        contacts.clear()
        contacts.addAll(source)
        revision.intValue++
    }

    fun updateConferences(source: List<ListItem>) {
        conferences.clear()
        conferences.addAll(source)
        revision.intValue++
    }
}

interface StartConversationScreenListener {
    fun onContactClick(contact: Contact)
    fun onConferenceClick(bookmark: Bookmark)
    fun onContactDetails(contact: Contact)
    fun onContactShowQr(contact: Contact)
    fun onContactToggleBlock(contact: Contact)
    fun onContactDelete(contact: Contact)
    fun onConferenceShare(bookmark: Bookmark)
    fun onConferenceDelete(bookmark: Bookmark)
    fun onTagClicked(tag: DynamicTag)
    fun onFabDiscoverChannels()
    fun onFabCreatePublicChannel()
    fun onFabJoinPublicChannel()
    fun onFabCreatePrivateGroupChat()
    fun onFabCreateContact()
    fun onQuicksyRefresh()
}

object StartConversationListHelper {
    @JvmStatic
    fun setup(
        composeView: ComposeView,
        state: StartConversationListState,
        listener: StartConversationScreenListener,
        showChannelDiscovery: Boolean,
        isQuicksy: Boolean,
    ) {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            ImpulseExpressiveTheme {
                StartConversationScreen(state, listener, showChannelDiscovery, isQuicksy)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun StartConversationScreen(
    state: StartConversationListState,
    listener: StartConversationScreenListener,
    showChannelDiscovery: Boolean,
    isQuicksy: Boolean,
) {
    val revision = state.revision.intValue
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        state.currentTab.intValue = pagerState.currentPage
    }

    var fabExpanded by state.fabExpanded

    BackHandler(enabled = fabExpanded) { fabExpanded = false }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            val tabTitles = listOf(
                stringResource(R.string.contacts),
                stringResource(R.string.group_chats),
            )
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) },
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                if (page == 0) {
                    ContactsTab(
                        items = state.contacts,
                        revision = revision,
                        isRefreshing = state.refreshing.value,
                        refreshEnabled = isQuicksy,
                        onRefresh = { listener.onQuicksyRefresh() },
                        listener = listener,
                    )
                } else {
                    ConferencesTab(items = state.conferences, revision = revision, listener = listener)
                }
            }
        }

        FloatingActionButtonMenu(
            expanded = fabExpanded,
            button = {
                ToggleFloatingActionButton(
                    checked = fabExpanded,
                    onCheckedChange = { fabExpanded = it },
                ) {
                    // A "+" rotated 45° reads as an "×" — same trick the old speed-dial FAB used,
                    // so the plus-turns-into-a-cross motion is preserved without a second icon.
                    // animateIcon is a member extension on ToggleFloatingActionButtonDefaults, so
                    // it needs that object as an implicit receiver via `with(...)` to resolve.
                    with(ToggleFloatingActionButtonDefaults) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add_24dp),
                            contentDescription =
                                stringResource(R.string.add_contact_or_create_or_join_group_chat),
                            modifier = Modifier.animateIcon({ checkedProgress })
                                .graphicsLayer { rotationZ = 45f * checkedProgress },
                        )
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            if (showChannelDiscovery) {
                FloatingActionButtonMenuItem(
                    onClick = { fabExpanded = false; listener.onFabDiscoverChannels() },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_travel_explore_24dp),
                            contentDescription = null,
                        )
                    },
                    text = { Text(stringResource(R.string.discover_channels)) },
                )
            }
            FloatingActionButtonMenuItem(
                onClick = { fabExpanded = false; listener.onFabCreatePublicChannel() },
                icon = { Icon(painter = painterResource(R.drawable.ic_public_24dp), contentDescription = null) },
                text = { Text(stringResource(R.string.create_public_channel)) },
            )
            FloatingActionButtonMenuItem(
                onClick = { fabExpanded = false; listener.onFabJoinPublicChannel() },
                icon = { Icon(painter = painterResource(R.drawable.ic_login_24dp), contentDescription = null) },
                text = { Text(stringResource(R.string.join_public_channel)) },
            )
            FloatingActionButtonMenuItem(
                onClick = { fabExpanded = false; listener.onFabCreatePrivateGroupChat() },
                icon = { Icon(painter = painterResource(R.drawable.ic_group_24dp), contentDescription = null) },
                text = { Text(stringResource(R.string.create_private_group_chat)) },
            )
            FloatingActionButtonMenuItem(
                onClick = { fabExpanded = false; listener.onFabCreateContact() },
                icon = { Icon(painter = painterResource(R.drawable.ic_person_24dp), contentDescription = null) },
                text = { Text(stringResource(R.string.add_contact)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactsTab(
    items: List<ListItem>,
    revision: Int,
    isRefreshing: Boolean,
    refreshEnabled: Boolean,
    onRefresh: () -> Unit,
    listener: StartConversationScreenListener,
) {
    var longPressed by remember { mutableStateOf<Contact?>(null) }
    val list: @Composable () -> Unit = {
        ItemList(items = items, revision = revision) { item, shape ->
            val contact = item as Contact
            ListItemRow(
                item = item,
                shape = shape,
                onClick = { listener.onContactClick(contact) },
                onLongPress = { longPressed = contact },
                onTagClick = { listener.onTagClicked(it) },
            )
        }
    }
    if (refreshEnabled) {
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize(),
        ) {
            list()
        }
    } else {
        list()
    }
    longPressed?.let { contact ->
        ContactContextSheet(contact = contact, listener = listener, onDismiss = { longPressed = null })
    }
}

@Composable
private fun ConferencesTab(
    items: List<ListItem>,
    revision: Int,
    listener: StartConversationScreenListener,
) {
    var longPressed by remember { mutableStateOf<Bookmark?>(null) }
    ItemList(items = items, revision = revision) { item, shape ->
        val bookmark = item as Bookmark
        ListItemRow(
            item = item,
            shape = shape,
            onClick = { listener.onConferenceClick(bookmark) },
            onLongPress = { longPressed = bookmark },
            onTagClick = { listener.onTagClicked(it) },
        )
    }
    longPressed?.let { bookmark ->
        ConferenceContextSheet(bookmark = bookmark, listener = listener, onDismiss = { longPressed = null })
    }
}

@Composable
private fun ItemList(
    items: List<ListItem>,
    revision: Int,
    row: @Composable (ListItem, RoundedCornerShape) -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(items, key = { _, item -> listItemKey(item) }) { index, item ->
            row(item, listItemShape(index, items.size))
        }
        item { Spacer(Modifier.height(88.dp)) } // clears the FAB
    }
}

// The same JID can be bookmarked or rostered under more than one enabled account, which would
// otherwise produce two ListItems with an identical getAddress() — qualify by owning account so
// LazyColumn's key stays unique (IllegalArgumentException: "Key ... was already used").
private fun listItemKey(item: ListItem): String {
    val accountJid = when (item) {
        is Contact -> item.getAccount().getJid()
        is Bookmark -> item.getAccount().getJid()
        else -> null
    }
    return "${item.getAddress()}#$accountJid"
}

private fun listItemShape(index: Int, total: Int): RoundedCornerShape {
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
private fun ListItemRow(
    item: ListItem,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onTagClick: (DynamicTag) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val avatarState = remember(item) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item) {
        val activity = context as? XmppActivity ?: return@LaunchedEffect
        val sizePx = with(density) { 48.dp.toPx() }.toInt()
        val bm = withContext(Dispatchers.IO) {
            try {
                activity.avatarService().get(item, sizePx, false)
            } catch (_: Exception) {
                null
            }
        }
        if (bm != null) avatarState.value = bm.asImageBitmap()
    }

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            val avatar = avatarState.value
            if (avatar != null) {
                Image(
                    bitmap = avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)),
                )
            } else {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
            }
            Spacer(Modifier.width(dimensionResource(R.dimen.avatar_item_distance)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.getDisplayName(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = IrregularUnicodeDetector.style(context, item.getAddress()).toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val tags = rememberVisibleTags(item)
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        tags.forEach { tag -> TagChip(tag, onClick = { onTagClick(tag) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberVisibleTags(item: ListItem): List<DynamicTag> {
    val context = LocalContext.current
    return remember(item) {
        val tags = item.getTags()
        val isBlockNoteworthy = context !is BlocklistActivity
        val showDynamicTags = android.preference.PreferenceManager
            .getDefaultSharedPreferences(context)
            .getBoolean(eu.siacs.conversations.AppSettings.SHOW_DYNAMIC_TAGS, false)
        val noteworthy = tags.any { it is DynamicTag.Blocked }
        if ((isBlockNoteworthy && noteworthy) || showDynamicTags) tags else emptyList()
    }
}

@Composable
private fun TagChip(tag: DynamicTag, onClick: () -> Unit) {
    val (label, containerColor, contentColor) = tagAppearance(tag)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun tagAppearance(tag: DynamicTag): Triple<String, Color, Color> {
    val inverseSurface = MaterialTheme.colorScheme.inverseSurface
    val onInverseSurface = MaterialTheme.colorScheme.inverseOnSurface
    return when (tag) {
        is DynamicTag.Blocked -> Triple(stringResource(R.string.blocked), inverseSurface, onInverseSurface)
        is DynamicTag.RosterGroup -> {
            val color = Color(XEP0392Helper.rgbFromNick(tag.name()))
            Triple(tag.name(), color, Color.White)
        }
        is DynamicTag.Hat -> Triple(
            tag.title() ?: "",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        is DynamicTag.Status -> {
            val availability = tag.availability()
            if (availability == null) {
                Triple(stringResource(R.string.presence_offline), inverseSurface, onInverseSurface)
            } else {
                val res = when (availability) {
                    im.conversations.android.xmpp.model.stanza.Presence.Availability.CHAT,
                    im.conversations.android.xmpp.model.stanza.Presence.Availability.ONLINE -> R.string.presence_online
                    im.conversations.android.xmpp.model.stanza.Presence.Availability.AWAY -> R.string.presence_away
                    im.conversations.android.xmpp.model.stanza.Presence.Availability.XA -> R.string.presence_xa
                    im.conversations.android.xmpp.model.stanza.Presence.Availability.DND -> R.string.presence_dnd
                    else -> R.string.presence_offline
                }
                Triple(
                    stringResource(res),
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        is DynamicTag.Attributes -> Triple(
            tag.role()?.toString().orEmpty(),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactContextSheet(
    contact: Contact,
    listener: StartConversationScreenListener,
    onDismiss: () -> Unit,
) {
    val xmppConnection = contact.getAccount()?.getXmppConnection()
    val canBlock = xmppConnection != null &&
        xmppConnection.getManager(BlockingManager::class.java).hasFeature() &&
        !contact.isSelf()
    val canDelete = contact.showInRoster() && !contact.getOption(Contact.Options.SYNCED_VIA_OTHER)
    val actions = buildList {
        if (!contact.isSelf()) {
            add(Triple(R.drawable.ic_person_24dp, stringResource(R.string.view_contact_details)) {
                listener.onContactDetails(contact)
            })
        }
        add(Triple(R.drawable.ic_qr_code_24dp, stringResource(R.string.show_qr_code)) {
            listener.onContactShowQr(contact)
        })
        if (canBlock) {
            val label = if (contact.isBlocked())
                stringResource(R.string.unblock_contact)
            else stringResource(R.string.block_contact)
            add(Triple(R.drawable.ic_do_not_disturb_on_24dp, label) { listener.onContactToggleBlock(contact) })
        }
        if (canDelete) {
            add(Triple(R.drawable.ic_delete_24dp, stringResource(R.string.delete_contact)) {
                listener.onContactDelete(contact)
            })
        }
    }
    ContextSheet(title = contact.getDisplayName(), actions = actions, onDismiss = onDismiss)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConferenceContextSheet(
    bookmark: Bookmark,
    listener: StartConversationScreenListener,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? XmppActivity
    val conversation = activity?.xmppConnectionService?.find(bookmark)
    val canShare = conversation == null || !conversation.isPrivateAndNonAnonymous()
    val deleteLabel = if (conversation != null)
        stringResource(R.string.delete_and_close)
    else stringResource(R.string.delete_bookmark)
    val actions = buildList {
        if (canShare) {
            add(Triple(R.drawable.ic_share_24dp, stringResource(R.string.share_uri_with)) {
                listener.onConferenceShare(bookmark)
            })
        }
        add(Triple(R.drawable.ic_delete_24dp, deleteLabel) { listener.onConferenceDelete(bookmark) })
    }
    ContextSheet(title = bookmark.getDisplayName(), actions = actions, onDismiss = onDismiss)
}

private val SHEET_CORNER_LARGE = 20.dp
private val SHEET_CORNER_SMALL = 5.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextSheet(
    title: String,
    actions: List<Triple<Int, String, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
            actions.forEachIndexed { index, (iconRes, label, onClick) ->
                val top = if (index == 0) SHEET_CORNER_LARGE else SHEET_CORNER_SMALL
                val bottom = if (index == actions.lastIndex) SHEET_CORNER_LARGE else SHEET_CORNER_SMALL
                Surface(
                    onClick = { onDismiss(); onClick() },
                    shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
