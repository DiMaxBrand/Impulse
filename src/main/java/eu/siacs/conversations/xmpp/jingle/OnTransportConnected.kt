package eu.siacs.conversations.xmpp.jingle

interface OnTransportConnected {
    fun failed()
    fun established()
}
