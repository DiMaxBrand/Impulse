package eu.siacs.conversations.ui.fragment.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.R
import eu.siacs.conversations.utils.UIHelper

class AttachmentsSettingsFragment : XmppPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_attachments, rootKey)
        listOf(AppSettings.AUTO_DOWNLOAD_WIFI_FILE_SIZE, AppSettings.AUTO_DOWNLOAD_MOBILE_FILE_SIZE).forEach { key ->
            val preference = findPreference<ListPreference>(key)
                ?: throw IllegalStateException("Missing automatic download size preference: $key")
            setValues(preference, R.array.file_size_values) { value -> UIHelper.filesizeToString(value.toLong()) }
        }
        listOf(
            AppSettings.AUTO_DOWNLOAD_WIFI to AppSettings.AUTO_DOWNLOAD_WIFI_FILE_SIZE,
            AppSettings.AUTO_DOWNLOAD_MOBILE to AppSettings.AUTO_DOWNLOAD_MOBILE_FILE_SIZE,
        ).forEach { (toggleKey, sizeKey) ->
            val toggle = findPreference<SwitchPreferenceCompat>(toggleKey) ?: return@forEach
            val size = findPreference<ListPreference>(sizeKey) ?: return@forEach
            size.isEnabled = toggle.isChecked
            toggle.setOnPreferenceChangeListener { _, value ->
                size.isEnabled = value as Boolean
                true
            }
        }
    }

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(R.string.pref_attachments)
    }
}
