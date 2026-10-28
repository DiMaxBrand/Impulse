package eu.siacs.conversations.ui

import android.Manifest
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Intents
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import eu.siacs.conversations.R

/**
 * "Add to the address book": a short note first (the phone's own contacts screen opens next: pick the
 * person from the list if they are already there, or make a new contact), then that screen, with
 * what is known already filled in: the name, the Impulse address and, for a new contact only, the
 * phone number. The contacts app splits "First Last" into the two name fields itself.
 */
class AddToAddressBookDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val name = requireArguments().getString(ARG_NAME)
        val address = requireArguments().getString(ARG_ADDRESS)
        val phone = requireArguments().getString(ARG_PHONE)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ImpulseExpressiveTheme {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        modifier = Modifier.widthIn(max = rememberMaxElevatedCardWidth()),
                    ) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_contacts_24dp), null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp),
                            )
                            Text(stringResource(R.string.save_to_contact), style = MaterialTheme.typography.headlineSmall)
                            Text(
                                stringResource(R.string.add_to_address_book_note),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { dismiss() }) { Text(stringResource(R.string.cancel)) }
                                Button(onClick = {
                                    open(requireActivity(), name, address, phone)
                                    dismiss()
                                }) { Text(stringResource(R.string.add_to_address_book_open)) }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "AddToAddressBook"
        private const val ARG_NAME = "name"
        private const val ARG_ADDRESS = "address"
        private const val ARG_PHONE = "phone"

        /** The note first, then the phone's contacts screen. */
        @JvmStatic
        fun show(activity: FragmentActivity, name: String?, address: String?, phone: String?) {
            val manager = activity.supportFragmentManager
            if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return
            AddToAddressBookDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_NAME, name)
                    putString(ARG_ADDRESS, address)
                    putString(ARG_PHONE, phone)
                }
            }.show(manager, TAG)
        }

        /** The contacts screen that lets the person pick an existing contact or create a new one. */
        private fun open(activity: androidx.fragment.app.FragmentActivity, name: String?, address: String?, phone: String?) {
            val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                if (!name.isNullOrBlank() && name != address) putExtra(Intents.Insert.NAME, name.trim())
                if (!address.isNullOrBlank()) {
                    putExtra(Intents.Insert.IM_HANDLE, address)
                    // Legacy value: there is no "XMPP" kind to ask for.
                    putExtra(Intents.Insert.IM_PROTOCOL, CommonDataKinds.Im.PROTOCOL_JABBER)
                }
                // The number goes in only when the address book does not have it yet: for a contact that is
                // already there it would be a second copy of the same number.
                if (!phone.isNullOrBlank() && !inAddressBook(activity, phone)) putExtra(Intents.Insert.PHONE, phone)
                putExtra("finishActivityOnSaveCompleted", true)
            }
            try {
                activity.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(activity, R.string.no_application_found_to_view_contact, Toast.LENGTH_SHORT).show()
            }
        }

        /** Whether some contact already has this number (false when the app may not look). */
        private fun inAddressBook(activity: FragmentActivity, phone: String): Boolean {
            if (activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false
            return try {
                val uri = android.net.Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(phone),
                )
                activity.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                    ?.use { it.count > 0 } ?: false
            } catch (_: Exception) {
                false
            }
        }
    }
}
