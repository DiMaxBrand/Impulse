package eu.siacs.conversations.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.services.MessageSearchTask
import eu.siacs.conversations.ui.interfaces.OnSearchResultsAvailable
import eu.siacs.conversations.ui.util.ShareUtil
import eu.siacs.conversations.utils.FtsUtils

/**
 * Message search. The search itself (full-text query, background task, result cursor) is the one
 * the old screen used; this activity only hosts the Compose UI around it.
 */
class SearchActivity : XmppActivity(), OnSearchResultsAvailable {

    private val state = SearchUiState()
    private var pendingTerm: String? = null
    private var lastTerm: List<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parameters = savedInstanceState ?: intent?.extras
        state.scopeUuid = parameters?.getString(EXTRA_CONVERSATION_UUID)
        val initial = parameters?.getString(EXTRA_SEARCH_TERM)
        if (!initial.isNullOrEmpty()) {
            state.query.edit { append(initial) }
            pendingTerm = initial
        }
        setContent {
            ImpulseExpressiveTheme {
                // Re-run the search whenever the text or the chosen scope changes.
                LaunchedEffect(Unit) {
                    snapshotFlow { state.query.text.toString() to state.scopeUuid }
                        .collect { (text, _) -> startSearch(text) }
                }
                SearchScreen(
                    state = state,
                    scopes = ::scopes,
                    focusOnStart = initial.isNullOrEmpty(),
                    onBack = { finish() },
                    onShowInChat = { openMessage(it, reply = false) },
                    onReply = { openMessage(it, reply = true) },
                    onCopy = { ShareUtil.copyToClipboard(this, it) },
                    onShare = { ShareUtil.share(this, it) },
                )
            }
        }
    }

    private fun scopes(): List<SearchScope> {
        val service = xmppConnectionService ?: return emptyList()
        val list = ArrayList<Conversation>()
        service.populateWithOrderedConversations(list)
        return list.map { SearchScope(it.getUuid(), it.getName().toString()) }
    }

    private fun startSearch(raw: String) {
        val term = FtsUtils.parse(raw.trim())
        lastTerm = term
        if (term.isEmpty()) {
            MessageSearchTask.cancelRunningTasks()
            state.results = emptyList()
            state.highlight = emptyList()
            state.loading = false
            state.searched = false
            return
        }
        val service = xmppConnectionService
        if (service == null) {
            pendingTerm = raw
            return
        }
        state.loading = true
        service.search(term, state.scopeUuid, this)
    }

    override fun onSearchResultsAvailable(term: List<String>, messages: List<Message>) {
        runOnUiThread {
            if (term != lastTerm) return@runOnUiThread // a newer query is already on its way
            // The database hands them oldest first; the list reads newest first.
            state.results = messages.asReversed()
            state.highlight = term
            state.loading = false
            state.searched = true
        }
    }

    override fun onBackendConnected() {
        pendingTerm?.let {
            pendingTerm = null
            startSearch(it)
        }
    }

    override fun refreshUiReal() {}

    override fun onSaveInstanceState(outState: Bundle) {
        val text = state.query.text.toString()
        if (text.isNotBlank()) outState.putString(EXTRA_SEARCH_TERM, text)
        state.scopeUuid?.let { outState.putString(EXTRA_CONVERSATION_UUID, it) }
        super.onSaveInstanceState(outState)
    }

    private fun openMessage(message: Message, reply: Boolean) {
        val conversation = wrap(message.conversation) ?: return
        switchToConversationAtMessage(conversation, message, reply)
    }

    private fun wrap(conversational: Conversational): Conversation? {
        if (conversational is Conversation) return conversational
        val service = xmppConnectionService ?: return null
        return service.findOrCreateConversation(
            conversational.getAccount(),
            conversational.getAddress(),
            conversational.getMode() == Conversational.MODE_MULTI,
            true,
            true,
        )
    }

    companion object {
        const val EXTRA_SEARCH_TERM = "search-term"
        const val EXTRA_CONVERSATION_UUID = "uuid"
    }
}
