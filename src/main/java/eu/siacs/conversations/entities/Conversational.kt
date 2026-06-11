package eu.siacs.conversations.entities

import eu.siacs.conversations.xmpp.Jid

interface Conversational {

    companion object {
        const val MODE_MULTI = 1
        const val MODE_SINGLE = 0
    }

    fun getAccount(): Account

    fun getContact(): Contact

    fun getAddress(): Jid

    fun getMode(): Int

    fun getUuid(): String
}
