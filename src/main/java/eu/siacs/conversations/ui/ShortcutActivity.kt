package eu.siacs.conversations.ui

import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.ActionBar
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.entities.Contact
import java.util.Collections

class ShortcutActivity : AbstractSearchableListItemActivity() {

    companion object {
        private val BLACKLISTED_ACTIVITIES: List<String> =
            listOf("com.teslacoilsw.launcher.ChooseActionIntentActivity")
    }

    override fun refreshUiReal() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listView.setOnItemClickListener { _, _, position, _ ->
            val callingActivity = getCallingActivity()

            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(
                searchEditText?.windowToken,
                InputMethodManager.HIDE_IMPLICIT_ONLY
            )

            val listItem = getListItems()[position]
            val legacy = BLACKLISTED_ACTIVITIES.contains(
                if (callingActivity == null) null else callingActivity.className
            )
            val shortcut = xmppConnectionService
                .shortcutService
                .createShortcut(listItem as Contact, legacy)
            setResult(RESULT_OK, shortcut)
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        val bar: ActionBar? = supportActionBar
        bar?.setTitle(R.string.create_shortcut)
    }

    override fun filterContacts(needle: String?) {
        getListItems().clear()
        if (xmppConnectionService == null) {
            listItemAdapter.notifyDataSetChanged()
            return
        }
        for (account in xmppConnectionService.getAccounts()) {
            if (account.isEnabled) {
                for (contact in account.getRoster().getContacts()) {
                    if (contact.showInContactList() && contact.match(needle)) {
                        getListItems().add(contact)
                    }
                }
            }
        }
        Collections.sort(getListItems())
        listItemAdapter.notifyDataSetChanged()
    }
}
