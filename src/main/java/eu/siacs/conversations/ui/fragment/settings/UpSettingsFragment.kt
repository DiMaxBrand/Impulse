package eu.siacs.conversations.ui.fragment.settings

import android.os.Bundle
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import com.google.common.base.Strings
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import eu.siacs.conversations.R
import eu.siacs.conversations.receiver.UnifiedPushDistributor
import eu.siacs.conversations.xmpp.Jid
import java.net.URI
import java.net.URISyntaxException
import java.util.Arrays

class UpSettingsFragment : XmppPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_up, rootKey)
    }

    override fun onBackendConnected() {
        val upAccounts = findPreference<ListPreference>(UnifiedPushDistributor.PREFERENCE_ACCOUNT)
        val pushServer = findPreference<EditTextPreference>(UnifiedPushDistributor.PREFERENCE_PUSH_SERVER)
        if (upAccounts == null || pushServer == null) {
            throw IllegalStateException()
        }
        pushServer.setOnPreferenceChangeListener { _, newValue ->
            if (newValue is String) {
                if (Strings.isNullOrEmpty(newValue) || isJidInvalid(newValue) || isHttpUri(newValue)) {
                    Toast.makeText(requireActivity(), R.string.invalid_jid, Toast.LENGTH_LONG).show()
                    false
                } else {
                    true
                }
            } else {
                Toast.makeText(requireActivity(), R.string.invalid_jid, Toast.LENGTH_LONG).show()
                false
            }
        }
        reconfigureUpAccountPreference(upAccounts)
    }

    override fun onSharedPreferenceChanged(key: String) {
        super.onSharedPreferenceChanged(key)
        if (UnifiedPushDistributor.PREFERENCES.contains(key)) {
            val service = requireService()
            if (service.reconfigurePushDistributor()) {
                service.renewUnifiedPushEndpoints()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(R.string.unified_push_distributor)
    }

    private fun reconfigureUpAccountPreference(listPreference: ListPreference) {
        val accounts: List<CharSequence> = ImmutableList.copyOf(
            Lists.transform(requireService().getAccounts()) { a ->
                a!!.jid.asBareJid().toString() as CharSequence
            }
        )
        val entries = ImmutableList.Builder<CharSequence>()
        val entryValues = ImmutableList.Builder<CharSequence>()
        entries.add(getString(R.string.no_account_deactivated))
        entryValues.add("none")
        entries.addAll(accounts)
        entryValues.addAll(accounts)
        listPreference.entries = entries.build().toTypedArray()
        listPreference.entryValues = entryValues.build().toTypedArray()
        if (!accounts.contains(listPreference.value)) {
            listPreference.value = "none"
        }
    }

    companion object {
        private fun isJidInvalid(input: String): Boolean {
            return try {
                val jid = Jid.ofUserInput(input)
                !jid.isBareJid()
            } catch (e: IllegalArgumentException) {
                true
            }
        }

        private fun isHttpUri(input: String): Boolean {
            val uri: URI = try {
                URI(input)
            } catch (e: URISyntaxException) {
                return false
            }
            return Arrays.asList("http", "https").contains(uri.scheme)
        }
    }
}
