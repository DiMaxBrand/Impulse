package eu.siacs.conversations.crypto.axolotl

fun interface OnMessageCreatedCallback {
    fun run(message: XmppAxolotlMessage)
}
