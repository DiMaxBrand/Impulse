package eu.siacs.conversations.xmpp

import eu.siacs.conversations.entities.Account

fun interface OnAdvancedStreamFeaturesLoaded {
    fun onAdvancedStreamFeaturesAvailable(account: Account)
}
