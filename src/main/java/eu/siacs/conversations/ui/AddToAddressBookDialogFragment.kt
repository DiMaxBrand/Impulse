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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

    // 0: waiting for the permission question, 1: the note, 2: the person is already in the address book,
    // 3: the offer (a roster contact who may not be in the address book: Add / Ignore).
    private var stage by mutableIntStateOf(0)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { evaluate() }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val offer = requireArguments().getBoolean(ARG_OFFER)
        if (offer && savedInstanceState == null) {
            // The offer comes first, with or without the permission: the question is asked once "Add" is tapped.
            stage = 3
        } else if (AddressBook.hasPermission(requireContext())) {
            evaluate()
        } else if (savedInstanceState == null) {
            // Looking in the address book needs the contacts permission: it is asked for here, at the moment it matters.
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        } else {
            evaluate()
        }
    }

    /** With or without the permission: is the person in the address book already, or is it time for the note. */
    private fun evaluate() {
        val address = requireArguments().getString(ARG_ADDRESS)
        val phone = requireArguments().getString(ARG_PHONE)
        stage = if (AddressBook.isPresent(requireContext(), address, phone)) 2 else 1
    }

    /** "Add" on the offer: ask for the permission if it is missing, then see whether the person is there already. */
    private fun continueAfterOffer() {
        if (AddressBook.hasPermission(requireContext())) evaluate() else permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val name = requireArguments().getString(ARG_NAME)
        val address = requireArguments().getString(ARG_ADDRESS)
        val phone = requireArguments().getString(ARG_PHONE)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ImpulseExpressiveTheme {
                    if (stage != 0) Surface(
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
                            if (stage == 3) {
                                Text(
                                    stringResource(R.string.add_to_address_book_offer, name?.takeIf { it.isNotBlank() } ?: address.orEmpty()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    // "Ignore" is remembered, so the offer does not come back for this person.
                                    TextButton(onClick = {
                                        if (address != null) AddressBook.markHandled(requireContext(), address)
                                        dismiss()
                                    }) { Text(stringResource(R.string.add_to_address_book_ignore)) }
                                    Button(onClick = {
                                        if (address != null) AddressBook.markHandled(requireContext(), address)
                                        continueAfterOffer()
                                    }) { Text(stringResource(R.string.add_to_address_book_add)) }
                                }
                            } else if (stage == 2) {
                                Text(
                                    stringResource(R.string.add_to_address_book_already, name?.takeIf { it.isNotBlank() } ?: address.orEmpty()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Button(onClick = { dismiss() }) { Text(stringResource(android.R.string.ok)) }
                                }
                            } else {
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
    }

    companion object {
        private const val TAG = "AddToAddressBook"
        private const val ARG_NAME = "name"
        private const val ARG_ADDRESS = "address"
        private const val ARG_PHONE = "phone"
        private const val ARG_OFFER = "offer"

        /** The note first, then the phone's contacts screen. */
        @JvmStatic
        fun show(activity: FragmentActivity, name: String?, address: String?, phone: String?) {
            show(activity, name, address, phone, offer = false)
        }

        /**
         * [offer]: somebody already in the roster who is not in the address book yet: the same note, with Add and Ignore
         * (Ignore is remembered).
         */
        @JvmStatic
        fun show(activity: FragmentActivity, name: String?, address: String?, phone: String?, offer: Boolean): Boolean {
            val manager = activity.supportFragmentManager
            if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return false
            AddToAddressBookDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_NAME, name)
                    putString(ARG_ADDRESS, address)
                    putString(ARG_PHONE, phone)
                    putBoolean(ARG_OFFER, offer)
                }
            }.show(manager, TAG)
            return true
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
                if (!phone.isNullOrBlank() && !AddressBook.hasNumber(activity, phone)) putExtra(Intents.Insert.PHONE, phone)
                putExtra("finishActivityOnSaveCompleted", true)
            }
            try {
                activity.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(activity, R.string.no_application_found_to_view_contact, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** Looking in the phone's address book (needs the contacts permission; without it nothing is found). */
object AddressBook {
    private const val PREFS = "address_book_offers"

    fun hasPermission(context: android.content.Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Some contact already has this phone number. */
    fun hasNumber(context: android.content.Context, phone: String): Boolean {
        if (!hasPermission(context)) return false
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(phone),
            )
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                ?.use { it.count > 0 } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /** Some contact already has this Impulse address as a chat (IM) entry. */
    fun hasAddress(context: android.content.Context, address: String): Boolean {
        if (!hasPermission(context)) return false
        return try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID),
                ContactsContract.Data.MIMETYPE + "=? AND lower(" + CommonDataKinds.Im.DATA + ")=?",
                arrayOf(CommonDataKinds.Im.CONTENT_ITEM_TYPE, address.lowercase()),
                null,
            )?.use { it.count > 0 } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun isPresent(context: android.content.Context, address: String?, phone: String?): Boolean =
        (!address.isNullOrBlank() && hasAddress(context, address)) || (!phone.isNullOrBlank() && hasNumber(context, phone))

    /** Added or ignored: not offered again for this person. */
    fun markHandled(context: android.content.Context, address: String) {
        val p = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        p.edit().putStringSet("handled", (p.getStringSet("handled", emptySet()) ?: emptySet()) + address.lowercase()).apply()
    }

    fun isHandled(context: android.content.Context, address: String): Boolean =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getStringSet("handled", emptySet())?.contains(address.lowercase()) == true
}
