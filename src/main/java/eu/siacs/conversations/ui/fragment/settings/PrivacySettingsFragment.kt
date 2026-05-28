package eu.siacs.conversations.ui.fragment.settings

import android.os.Bundle
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.R

class PrivacySettingsFragment : XmppPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_privacy, rootKey)
    }

    override fun onSharedPreferenceChanged(key: String) {
        super.onSharedPreferenceChanged(key)
        when (key) {
            AppSettings.READ_RECEIPTS,
            AppSettings.BROADCAST_LAST_ACTIVITY,
            AppSettings.ALLOW_MESSAGE_CORRECTION -> requireService().refreshAllPresences()
        }
    }

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(R.string.pref_privacy)
    }
}
