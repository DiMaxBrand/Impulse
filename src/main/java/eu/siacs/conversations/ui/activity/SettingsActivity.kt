package eu.siacs.conversations.ui.activity

import android.app.Notification
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import androidx.preference.PreferenceFragmentCompat
import com.google.common.collect.ImmutableSet
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.ActivitySettingsBinding
import eu.siacs.conversations.ui.Activities
import eu.siacs.conversations.ui.XmppActivity
import eu.siacs.conversations.ui.fragment.settings.MainSettingsFragment
import eu.siacs.conversations.ui.fragment.settings.NotificationsSettingsFragment
import eu.siacs.conversations.ui.fragment.settings.XmppPreferenceFragment
import java.util.Collections

class SettingsActivity : XmppActivity() {

    override fun refreshUiReal() {}

    override fun onBackendConnected() {
        val fragmentManager = supportFragmentManager
        val currentFragment = fragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment is XmppPreferenceFragment) {
            currentFragment.onBackendConnected()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivitySettingsBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_settings)
        setSupportActionBar(binding.materialToolbar)
        Activities.setStatusAndNavigationBarColors(this, binding.root)

        val intent = getIntent()
        val categories = if (intent == null) Collections.emptySet<String>() else intent.categories
        val preferenceFragment: PreferenceFragmentCompat
        if (ImmutableSet.of(Notification.INTENT_CATEGORY_NOTIFICATION_PREFERENCES) == categories) {
            preferenceFragment = NotificationsSettingsFragment()
        } else {
            preferenceFragment = MainSettingsFragment()
        }

        val fragmentManager = supportFragmentManager
        val currentFragment = fragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment == null) {
            fragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, preferenceFragment)
                .commit()
        }
        binding.materialToolbar.setNavigationOnClickListener {
            if (fragmentManager.backStackEntryCount == 0) {
                finish()
            } else {
                fragmentManager.popBackStack()
            }
        }
    }
}
