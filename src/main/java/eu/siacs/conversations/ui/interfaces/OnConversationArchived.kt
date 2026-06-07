package eu.siacs.conversations.ui.interfaces

import eu.siacs.conversations.entities.Conversation

fun interface OnConversationArchived {
    fun onConversationArchived(conversation: Conversation)
}
