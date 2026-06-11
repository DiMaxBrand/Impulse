package eu.siacs.conversations.ui.interfaces

import eu.siacs.conversations.entities.Conversation

fun interface OnConversationSelected {
    fun onConversationSelected(conversation: Conversation)
}
