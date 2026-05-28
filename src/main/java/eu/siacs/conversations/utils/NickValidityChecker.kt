package eu.siacs.conversations.utils

import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.xmpp.Jid

object NickValidityChecker {
    private fun check(conversation: Conversation, nick: String): Boolean {
        val room = conversation.address
        return try {
            val full = Jid.of(room.local, room.domain, nick)
            conversation.hasMessageWithCounterpart(full) || conversation.mucOptions.getUser(full) != null
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    @JvmStatic
    fun check(conversation: Conversation, nicks: List<String>): Boolean {
        for (nick in HashSet(nicks)) {
            if (!check(conversation, nick)) return false
        }
        return true
    }
}
