package eu.siacs.conversations.ui.fragment.settings

import android.os.Bundle
import com.google.android.material.color.DynamicColors
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.activity.SettingsActivity
import eu.siacs.conversations.ui.util.SettingsUtils

class InterfaceSettingsFragment : XmppPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_interface, rootKey)
        val themePreference = findPreference<androidx.preference.Preference>("theme")
        val dynamicColors = findPreference<androidx.preference.Preference>("dynamic_colors")
        if (themePreference == null || dynamicColors == null) {
            throw IllegalStateException(
                "The preference resource file did not contain theme or color preferences"
            )
        }
        themePreference.onPreferenceChangeListener =
            androidx.preference.Preference.OnPreferenceChangeListener { _, newValue ->
                if (newValue is String) {
                    val desiredNightMode = AppSettings.getDesiredNightMode(newValue)
                    requireSettingsActivity().setDesiredNightMode(desiredNightMode)
                }
                true
            }
        dynamicColors.isVisible = DynamicColors.isDynamicColorAvailable()
        dynamicColors.onPreferenceChangeListener =
            androidx.preference.Preference.OnPreferenceChangeListener { _, newValue ->
                requireSettingsActivity().setDynamicColors(java.lang.Boolean.TRUE == newValue)
                true
            }
    }

    override fun onSharedPreferenceChanged(key: String) {
        super.onSharedPreferenceChanged(key)
        if (key == AppSettings.ALLOW_SCREENSHOTS) {
            SettingsUtils.applyScreenshotSetting(requireActivity())
        }
    }

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(R.string.pref_title_interface)
    }

    fun requireSettingsActivity(): SettingsActivity {
        val activity = requireActivity()
        if (activity is SettingsActivity) {
            return activity
        }
        throw IllegalStateException(
            String.format(
                "%s is not %s",
                activity.javaClass.name,
                SettingsActivity::class.java.name
            )
        )
    }
}
