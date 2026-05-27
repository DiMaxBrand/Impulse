package eu.siacs.conversations.xmpp.jingle

import eu.siacs.conversations.entities.Account
import im.conversations.android.xmpp.model.stanza.Iq

fun interface OnJinglePacketReceived {
    fun onJinglePacketReceived(account: Account, packet: Iq)
}
