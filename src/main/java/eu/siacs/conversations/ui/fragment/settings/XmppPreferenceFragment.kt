package eu.siacs.conversations.ui.fragment.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.ArrayRes
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.common.base.Function
import com.google.common.base.Strings
import com.google.common.primitives.Ints
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.ui.XmppActivity
import eu.siacs.conversations.utils.TimeFrameUtils

abstract class XmppPreferenceFragment : PreferenceFragmentCompat() {

    private val sharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null) {
                return@OnSharedPreferenceChangeListener
            }
            if (isAdded) {
                onSharedPreferenceChanged(key)
            }
        }

    protected open fun onSharedPreferenceChanged(key: String) {
        Log.d(Config.LOGTAG, "onSharedPreferenceChanged($key)")
    }

    open fun onBackendConnected() {}

    override fun onResume() {
        super.onResume()
        val sharedPreferences = preferenceManager.sharedPreferences
        sharedPreferences?.registerOnSharedPreferenceChangeListener(this.sharedPreferenceChangeListener)
        val xmppActivity = requireXmppActivity()
        if (xmppActivity.xmppConnectionService != null) {
            this.onBackendConnected()
        }
    }

    override fun onPause() {
        super.onPause()
        val sharedPreferences = preferenceManager.sharedPreferences
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this.sharedPreferenceChangeListener)
    }

    protected fun reconnectAccounts() {
        val service = requireService()
        for (account in service.getAccounts()) {
            if (account.isEnabled) {
                service.reconnectAccountInBackground(account)
            }
        }
    }

    protected fun requireXmppActivity(): XmppActivity {
        val activity = requireActivity()
        if (activity is XmppActivity) {
            return activity
        }
        throw IllegalStateException()
    }

    protected fun requireService(): XmppConnectionService {
        val xmppActivity = requireXmppActivity()
        val service = xmppActivity.xmppConnectionService
        if (service != null) {
            return service
        }
        throw IllegalStateException()
    }

    protected fun runOnUiThread(runnable: Runnable) {
        requireActivity().runOnUiThread(runnable)
    }

    protected fun setValues(
        listPreference: ListPreference,
        @ArrayRes resId: Int,
        valueToName: Function<Int, String>
    ) {
        val choices = resources.getIntArray(resId)
        val entries = arrayOfNulls<CharSequence>(choices.size)
        val entryValues = arrayOfNulls<CharSequence>(choices.size)
        for (i in choices.indices) {
            val value = choices[i]
            entryValues[i] = value.toString()
            entries[i] = valueToName.apply(value)
        }
        listPreference.entries = entries
        listPreference.entryValues = entryValues
        listPreference.summaryProvider = Preference.SummaryProvider<ListPreference> { preference ->
            val value = Ints.tryParse(Strings.nullToEmpty(preference.value))
            valueToName.apply(value ?: 0)
        }
    }

    companion object {
        @JvmStatic
        protected fun timeframeValueToName(context: Context, value: Int): String {
            return if (value == 0) {
                context.getString(R.string.never)
            } else {
                TimeFrameUtils.resolve(context, 1000L * value)
            }
        }
    }
}
