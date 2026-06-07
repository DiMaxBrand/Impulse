package eu.siacs.conversations.xmpp

import im.conversations.android.xmpp.model.stanza.Message

fun interface OnMessagePacketReceived {
    fun onMessagePacketReceived(packet: Message)
}
