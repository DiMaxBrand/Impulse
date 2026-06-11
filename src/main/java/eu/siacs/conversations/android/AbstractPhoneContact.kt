package eu.siacs.conversations.android

import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.text.TextUtils

abstract class AbstractPhoneContact protected constructor(cursor: Cursor) {
    val lookupUri: Uri
    val displayName: String?
    val photoUri: String?

    init {
        @Suppress("DEPRECATION")
        val phoneId = cursor.getLong(cursor.getColumnIndex(ContactsContract.Data._ID))
        @Suppress("DEPRECATION")
        val lookupKey = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.LOOKUP_KEY))
        lookupUri = ContactsContract.Contacts.getLookupUri(phoneId, lookupKey)
        @Suppress("DEPRECATION")
        displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME))
        @Suppress("DEPRECATION")
        photoUri = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.PHOTO_URI))
    }

    fun rating(): Int =
        (if (TextUtils.isEmpty(displayName)) 0 else 2) + (if (TextUtils.isEmpty(photoUri)) 0 else 1)
}
