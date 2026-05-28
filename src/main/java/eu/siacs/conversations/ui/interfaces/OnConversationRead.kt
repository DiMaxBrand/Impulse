package eu.siacs.conversations.ui.interfaces

import eu.siacs.conversations.entities.Conversation

fun interface OnConversationRead {
    fun onConversationRead(conversation: Conversation, upToUuid: String)
}
