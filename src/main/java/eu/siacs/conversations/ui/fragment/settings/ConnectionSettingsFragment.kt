package eu.siacs.conversations.ui.fragment.settings

import eu.siacs.conversations.services.AbstractQuickConversationsService
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.NonNull
import com.google.common.base.Strings
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.utils.Resolver
import java.util.Arrays

class ConnectionSettingsFragment : XmppPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_connection, rootKey)
        val connectionOptions = findPreference<androidx.preference.Preference>(AppSettings.SHOW_CONNECTION_OPTIONS)
        val channelDiscovery = findPreference<androidx.preference.Preference>(AppSettings.CHANNEL_DISCOVERY_METHOD)
        val groupsAndConferences = findPreference<androidx.preference.Preference>(GROUPS_AND_CONFERENCES)
        if (connectionOptions == null || channelDiscovery == null || groupsAndConferences == null) {
            throw IllegalStateException()
        }
        if (AbstractQuickConversationsService.isQuicksy()) {
            connectionOptions.isVisible = false
        }
        if (hideChannelDiscovery()) {
            groupsAndConferences.isVisible = false
            channelDiscovery.isVisible = false
        }
    }

    override fun onSharedPreferenceChanged(key: String) {
        super.onSharedPreferenceChanged(key)
        when (key) {
            AppSettings.USE_TOR -> {
                val appSettings = AppSettings(requireContext())
                if (appSettings.isUseTor()) {
                    runOnUiThread {
                        Toast.makeText(
                            requireActivity(),
                            R.string.audio_video_disabled_tor,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                reconnectAccounts()
                requireService().reinitializeMuclumbusService()
            }
            AppSettings.SHOW_CONNECTION_OPTIONS -> reconnectAccounts()
        }
        if (Arrays.asList(AppSettings.USE_TOR, AppSettings.SHOW_CONNECTION_OPTIONS).contains(key)) {
            val appSettings = AppSettings(requireContext())
            if (appSettings.isUseTor() || appSettings.isExtendedConnectionOptions()) {
                return
            }
            resetUserDefinedHostname()
        }
    }

    private fun resetUserDefinedHostname() {
        val service = requireService()
        for (account in service.getAccounts()) {
            Log.d(
                Config.LOGTAG,
                "${account.jid.asBareJid()}: resetting hostname and port to defaults"
            )
            account.setHostname(null)
            account.setPort(Resolver.XMPP_PORT_STARTTLS)
            service.databaseBackend.updateAccount(account)
        }
    }

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(R.string.pref_connection_options)
    }

    companion object {
        private const val GROUPS_AND_CONFERENCES = "groups_and_conferences"

        @JvmStatic
        fun hideChannelDiscovery(): Boolean =
            AbstractQuickConversationsService.isQuicksy()
                    || AbstractQuickConversationsService.isPlayStoreFlavor()
                    || Strings.isNullOrEmpty(Config.CHANNEL_DISCOVERY)
    }
}
