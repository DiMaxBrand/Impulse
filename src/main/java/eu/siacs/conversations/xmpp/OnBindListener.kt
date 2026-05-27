package eu.siacs.conversations.xmpp

import eu.siacs.conversations.entities.Account

fun interface OnBindListener {
    fun onBind(account: Account)
}
