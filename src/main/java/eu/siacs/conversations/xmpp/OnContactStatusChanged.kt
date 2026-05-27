package eu.siacs.conversations.xmpp

import eu.siacs.conversations.entities.Contact

fun interface OnContactStatusChanged {
    fun onContactStatusChanged(contact: Contact, online: Boolean)
}
