package eu.siacs.conversations.ui.fragment.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.R

class InterfaceBubblesSettingsFragment : XmppPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_interface_bubbles, rootKey)
        val showAvatars11 = findPreference<Preference>(AppSettings.SHOW_AVATARS_11)
        val showAvatarsAccount = findPreference<Preference>(AppSettings.SHOW_AVATARS_ACCOUNTS)
        val alignStart = findPreference<SwitchPreferenceCompat>(AppSettings.ALIGN_START)
        if (showAvatars11 == null || showAvatarsAccount == null || alignStart == null) {
            throw IllegalStateException(
                "The preference resource file is missing some preferences"
            )
        }
        updateShowAvatars11Summary(showAvatars11, alignStart.isChecked)
    }

    override fun onSharedPreferenceChanged(key: String) {
        super.onSharedPreferenceChanged(key)
        when (key) {
            AppSettings.ALIGN_START -> runOnUiThread { updateShowAvatars11Summary() }
            AppSettings.SHOW_CONNECTION_OPTIONS -> reconnectAccounts()
        }
    }

    private fun updateShowAvatars11Summary() {
        val showAvatars11 = findPreference<Preference>(AppSettings.SHOW_AVATARS_11)
        updateShowAvatars11Summary(showAvatars11)
    }

    private fun updateShowAvatars11Summary(preference: Preference?) {
        val appSettings = AppSettings(requireContext())
        updateShowAvatars11Summary(preference, appSettings.isAlignStart)
    }

    private fun updateShowAvatars11Summary(preference: Preference?, alignStart: Boolean) {
        preference?.setSummary(
            if (alignStart) R.string.pref_show_11_chats_summary
            else R.string.pref_show_11_sender_chats_summary
        )
    }

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(R.string.pref_title_bubbles)
    }
}
