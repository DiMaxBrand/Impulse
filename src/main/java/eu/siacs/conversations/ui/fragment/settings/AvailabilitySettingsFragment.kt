package eu.siacs.conversations.ui.fragment.settings

import android.os.Bundle
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.R

class AvailabilitySettingsFragment : XmppPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_availability, rootKey)
    }

    override fun onSharedPreferenceChanged(key: String) {
        super.onSharedPreferenceChanged(key)
        when (key) {
            AppSettings.AWAY_WHEN_SCREEN_IS_OFF, AppSettings.MANUALLY_CHANGE_PRESENCE -> {
                requireService().toggleScreenEventReceiver()
                requireService().refreshAllPresences()
            }
            AppSettings.DND_SYNC_SYSTEM, AppSettings.DND_INCLUDE_SILENT_MODES ->
                requireService().refreshAllPresences()
        }
    }

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(R.string.pref_presence_settings)
    }
}
