package eu.siacs.conversations

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Emergency mode as the screens see it. The service decides (see
 * `XmppConnectionService.refreshEmergencyMode()`) and publishes here; the indicator reads [active]
 * and its button calls [dismiss]. Emergency mode is about a weak or failing connection, not about
 * saving data.
 */
object EmergencyMode {
    /** Whether the mode is on right now (snapshot state, so the indicator follows it). */
    var active by mutableStateOf(false)
        private set

    @Volatile private var dismissed = false
    @Volatile private var onDismiss: Runnable? = null

    @JvmStatic fun publish(value: Boolean) { active = value }

    /** The user turned it off by hand: it stays off until the connection has been healthy once. */
    @JvmStatic fun isDismissed(): Boolean = dismissed
    @JvmStatic fun setDismissed(value: Boolean) { dismissed = value }
    @JvmStatic fun setOnDismiss(callback: Runnable?) { onDismiss = callback }

    fun dismiss() {
        dismissed = true
        onDismiss?.run()
    }
}
