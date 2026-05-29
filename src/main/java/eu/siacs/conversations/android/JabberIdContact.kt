package eu.siacs.conversations.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.provider.ContactsContract
import android.util.Log
import eu.siacs.conversations.Config
import eu.siacs.conversations.services.QuickConversationsService
import eu.siacs.conversations.xmpp.Jid

class JabberIdContact private constructor(cursor: Cursor) : AbstractPhoneContact(cursor) {

    val jid: Jid

    init {
        try {
            jid = Jid.of(
                cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Im.DATA)
                )
            )
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(e)
        } catch (e: NullPointerException) {
            throw IllegalArgumentException(e)
        }
    }

    companion object {
        private val PROJECTION = arrayOf(
            ContactsContract.Data._ID,
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.Data.PHOTO_URI,
            ContactsContract.Data.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Im.DATA
        )

        private val SELECTION =
            ContactsContract.Data.MIMETYPE +
                "=? AND (" +
                ContactsContract.CommonDataKinds.Im.PROTOCOL +
                "=? or (" +
                ContactsContract.CommonDataKinds.Im.PROTOCOL +
                "=? and lower(" +
                ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL +
                ")=?))"

        private val SELECTION_ARGS = arrayOf(
            ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER.toString(),
            ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM.toString(),
            "xmpp"
        )

        @JvmStatic
        fun load(context: Context): Map<Jid, JabberIdContact> {
            if (!QuickConversationsService.isContactListIntegration(context) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            ) {
                return emptyMap()
            }
            return try {
                context.contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    PROJECTION,
                    SELECTION,
                    SELECTION_ARGS,
                    null
                ).use { cursor ->
                    if (cursor == null) return emptyMap()
                    val contacts = HashMap<Jid, JabberIdContact>()
                    while (cursor.moveToNext()) {
                        try {
                            val contact = JabberIdContact(cursor)
                            val preexisting = contacts[contact.jid]
                            if (preexisting == null || preexisting.rating() < contact.rating()) {
                                contacts[contact.jid] = contact
                            }
                        } catch (e: IllegalArgumentException) {
                            Log.d(Config.LOGTAG, "unable to create jabber id contact")
                        }
                    }
                    contacts
                }
            } catch (e: Exception) {
                Log.d(Config.LOGTAG, "unable to query", e)
                emptyMap()
            }
        }
    }
}
