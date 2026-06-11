package eu.siacs.conversations.utils

import android.os.Bundle

fun interface OnPhoneContactsLoadedListener {
    fun onPhoneContactsLoaded(phoneContacts: List<Bundle>)
}
