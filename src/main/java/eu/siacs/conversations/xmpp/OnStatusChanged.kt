package eu.siacs.conversations.xmpp

import eu.siacs.conversations.entities.Account

fun interface OnStatusChanged {
    fun onStatusChanged(account: Account)
}
