package eu.siacs.conversations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberContainedSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import im.conversations.android.model.SearchSuggestion
import kotlinx.coroutines.launch

/** What the search bar's two menus offer right now (mirrors the old View menus). */
class SearchHostState {
    val menuRevision = mutableIntStateOf(0)
    var showScanQr by mutableStateOf(false)
    var showShowQr by mutableStateOf(false)
    var showEasyInvite by mutableStateOf(false)
    /** The account avatar shown at the end of the bar (null: a generic person icon). */
    var avatar by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    /** True while the search is open; the chat list hides its floating button then. */
    var expanded by mutableStateOf(false)
}

class SearchHostActions(
    val suggest: (String) -> List<SearchSuggestion>,
    val onSearchMessages: (String) -> Unit,
    val onSuggestion: (SearchSuggestion) -> Unit,
    val onScanQr: () -> Unit,
    val onShowQr: () -> Unit,
    val onEasyInvite: () -> Unit,
    val onManageAccount: () -> Unit,
)

/**
 * The chat list's top bar: Material 3 Expressive "contained" search. Collapsed it is the search
 * bar with the QR and overflow menus; expanded, suggestions (message search first, then people and
 * group chats) appear as rows inside the same container.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConversationsSearchHost(
    host: SearchHostState,
    actions: SearchHostActions,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberContainedSearchBarState()
    val scope = rememberCoroutineScope()
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    val appBarColors = SearchBarDefaults.appBarWithSearchColors(
        searchBarColors = SearchBarDefaults.containedColors(state = searchBarState),
    )
    androidx.compose.runtime.LaunchedEffect(searchBarState.currentValue) {
        host.expanded = searchBarState.currentValue == SearchBarValue.Expanded
    }
    val collapse: () -> Unit = {
        scope.launch { searchBarState.animateToCollapsed() }
    }
    val submit: (String) -> Unit = { query ->
        val q = query.trim()
        if (q.isNotEmpty()) {
            actions.onSearchMessages(q)
            collapse()
        }
    }
    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            colors = appBarColors.searchBarColors.inputFieldColors,
            onSearch = submit,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = {
                if (searchBarState.currentValue == SearchBarValue.Expanded) {
                    IconButton(onClick = {
                        textFieldState.clearText()
                        collapse()
                    }) {
                        Icon(painterResource(R.drawable.ic_arrow_back_24dp), contentDescription = null)
                    }
                } else {
                    Icon(painterResource(R.drawable.ic_search_24dp), contentDescription = null)
                }
            },
            trailingIcon = {
                if (searchBarState.currentValue == SearchBarValue.Expanded &&
                    textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = { textFieldState.clearText() }) {
                        Icon(painterResource(R.drawable.ic_close_24dp), contentDescription = null)
                    }
                }
            },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                AppBarWithSearch(
                    state = searchBarState,
                    inputField = inputField,
                    scrollBehavior = scrollBehavior,
                    colors = appBarColors,
                    windowInsets = WindowInsets(0),
                    actions = { SearchBarMenus(host, actions) },
                )
            },
            contentWindowInsets = WindowInsets(0),
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        ) { padding ->
            Box(Modifier.padding(padding)) { content() }
        }
        ExpandedFullScreenContainedSearchBar(
            state = searchBarState,
            inputField = inputField,
            colors = SearchBarDefaults.containedColors(state = searchBarState),
            windowInsets = { WindowInsets(0) },
        ) {
            val query = textFieldState.text.toString().trim()
            val suggestions = remember(query, host.menuRevision.intValue) {
                if (query.isEmpty()) emptyList() else actions.suggest(query)
            }
            SuggestionList(
                suggestions = suggestions,
                onClick = {
                    actions.onSuggestion(it)
                    textFieldState.clearText()
                    collapse()
                },
            )
        }
    }
}

@Composable
private fun SearchBarMenus(host: SearchHostState, actions: SearchHostActions) {
    host.menuRevision.intValue // re-read when the fragment refreshes
    var qrOpen by remember { mutableStateOf(false) }
    val anyQr = host.showScanQr || host.showShowQr || host.showEasyInvite
    if (anyQr) {
        Box {
            IconButton(onClick = { qrOpen = true }) {
                Icon(painterResource(R.drawable.ic_qr_code_scanner_24dp),
                    contentDescription = stringResource(R.string.qr_code_actions))
            }
            ExpressiveDropdownMenu(expanded = qrOpen, onDismissRequest = { qrOpen = false }) {
                if (host.showScanQr) ExpressiveMenuItem(R.drawable.ic_qr_code_scanner_24dp,
                    stringResource(R.string.scan_qr_code)) { qrOpen = false; actions.onScanQr() }
                if (host.showShowQr) ExpressiveMenuItem(R.drawable.ic_qr_code_24dp,
                    stringResource(R.string.show_qr_code)) { qrOpen = false; actions.onShowQr() }
                if (host.showEasyInvite) ExpressiveMenuItem(R.drawable.ic_join_24dp,
                    stringResource(R.string.server_invite)) { qrOpen = false; actions.onEasyInvite() }
            }
        }
    }
    // Your avatar, like in Google's own apps: opens Accounts & settings.
    val avatar = host.avatar
    IconButton(onClick = actions.onManageAccount) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (avatar != null) {
                androidx.compose.foundation.Image(
                    bitmap = avatar, contentDescription = stringResource(R.string.hub_title),
                    modifier = Modifier.fillMaxSize())
            } else {
                Icon(painterResource(R.drawable.ic_person_24dp),
                    contentDescription = stringResource(R.string.hub_title),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SuggestionList(suggestions: List<SearchSuggestion>, onClick: (SearchSuggestion) -> Unit) {
    if (suggestions.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                stringResource(R.string.search_type_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(suggestions, key = { i, s -> if (s is SearchSuggestion.Text) "text" else "$i-$s" }) { index, s ->
            val shape = conversationItemShape(index, suggestions.size)
            val (icon, title, subtitle) = when (s) {
                is SearchSuggestion.Text -> Triple(R.drawable.ic_manage_search_24dp,
                    stringResource(R.string.search_messages_for_x, s.text()), null)
                is SearchSuggestion.Contact -> Triple(R.drawable.ic_person_24dp, s.name(), s.address().toString())
                is SearchSuggestion.Bookmark -> Triple(R.drawable.ic_group_24dp, s.name(), s.address().toString())
                is SearchSuggestion.Uri -> Triple(R.drawable.ic_link_24dp, s.uri().asJid().toString(), null)
            }
            val highlight = s is SearchSuggestion.Text
            Card(
                onClick = { onClick(s) },
                shape = shape,
                colors = CardDefaults.cardColors(
                    containerColor = if (highlight) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (highlight) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(icon), null,
                                tint = if (highlight) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
