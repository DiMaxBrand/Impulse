package eu.siacs.conversations.ui

import androidx.fragment.app.Fragment
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.ui.interfaces.OnBackendConnected

abstract class XmppFragment : Fragment(), OnBackendConnected {

    abstract fun refresh()

    protected fun runOnUiThread(runnable: Runnable) {
        requireActivity().runOnUiThread(runnable)
    }

    fun requireXmppActivity(): XmppActivity {
        val activity = activity
        if (activity is XmppActivity) {
            return activity
        }
        throw IllegalStateException("Fragment is not hosted by XmppActivity")
    }

    fun getXmppConnectionService(): XmppConnectionService? {
        val activity = activity
        if (activity is XmppActivity) {
            return activity.xmppConnectionService
        }
        return null
    }
}
