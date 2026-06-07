package eu.siacs.conversations.entities

import eu.siacs.conversations.android.AbstractPhoneContact
import eu.siacs.conversations.xmpp.Jid

interface Roster {
    fun getContacts(): List<Contact>
    fun getWithSystemAccounts(clazz: Class<out AbstractPhoneContact>): List<Contact>
    fun getContact(jid: Jid): Contact
    fun getContactFromContactList(jid: Jid): Contact
}
