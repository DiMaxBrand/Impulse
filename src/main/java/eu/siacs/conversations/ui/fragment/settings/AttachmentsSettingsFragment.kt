package eu.siacs.conversations.ui.fragment.settings

import android.os.Bundle
import androidx.preference.ListPreference
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.R
import eu.siacs.conversations.utils.UIHelper

class AttachmentsSettingsFragment : XmppPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_attachments, rootKey)
        val autoAcceptFileSize = findPreference<ListPreference>(AppSettings.AUTO_ACCEPT_FILE_SIZE)
            ?: throw IllegalStateException("The preference resource file is missing preferences")
        setValues(
            autoAcceptFileSize,
            R.array.file_size_values
        ) { value ->
            if (value <= 0) {
                getString(R.string.never)
            } else {
                UIHelper.filesizeToString(value.toLong())
            }
        }
    }

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(R.string.pref_attachments)
    }
}
