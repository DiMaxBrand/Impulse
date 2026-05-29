package eu.siacs.conversations.ui.fragment.settings

import android.os.Build
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.google.common.base.Strings
import eu.siacs.conversations.BuildConfig
import eu.siacs.conversations.R

class MainSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey)
        val about = findPreference<androidx.preference.Preference>("about")
        val connection = findPreference<androidx.preference.Preference>("connection")
        val up = findPreference<androidx.preference.Preference>("up")
        if (about == null || connection == null || up == null) {
            throw IllegalStateException(
                "The preference resource file is missing some preferences"
            )
        }
        val appName = getString(R.string.app_name)
        about.setTitle(getString(R.string.title_activity_about_x, appName))
        about.setSummary(
            String.format(
                "%s %s %s @ %s · %s · %s",
                appName,
                BuildConfig.VERSION_NAME,
                im.conversations.webrtc.BuildConfig.WEBRTC_VERSION,
                Strings.nullToEmpty(Build.MANUFACTURER),
                Strings.nullToEmpty(Build.DEVICE),
                Strings.nullToEmpty(Build.VERSION.RELEASE)
            )
        )
        if (ConnectionSettingsFragment.hideChannelDiscovery()) {
            connection.setSummary(R.string.pref_connection_summary)
        }
        up.isVisible = !Strings.isNullOrEmpty(getString(R.string.default_push_server))
    }

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(R.string.title_activity_settings)
    }
}
