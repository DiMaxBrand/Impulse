package eu.siacs.conversations.xmpp

import eu.siacs.conversations.entities.Account

fun interface OnMessageAcknowledged {
    fun onMessageAcknowledged(account: Account, to: Jid, id: String): Boolean
}
