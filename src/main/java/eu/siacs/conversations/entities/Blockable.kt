package eu.siacs.conversations.entities

import eu.siacs.conversations.xmpp.Jid

interface Blockable {
    fun isBlocked(): Boolean
    fun isDomainBlocked(): Boolean
    fun getBlockedAddress(): Jid
    fun getAddress(): Jid?
    fun getAccount(): Account
}
