package eu.siacs.conversations.ui.interfaces

import eu.siacs.conversations.entities.Message

fun interface OnSearchResultsAvailable {

    fun onSearchResultsAvailable(term: List<String>, messages: @JvmSuppressWildcards List<Message>)
}
