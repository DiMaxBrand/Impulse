package eu.siacs.conversations.ui

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * The two special permissions the incoming-call pop-up depends on, the same ones the
 * notification setup screen shows cards for. Some phones (Samsung's "remove permissions if app
 * isn't used", Xiaomi after an update) switch them off again on their own, so the app re-checks
 * them instead of trusting the one-time setup.
 */
object CallPermissions {
    @JvmStatic
    fun anyMissing(context: Context): Boolean {
        val overlay = Settings.canDrawOverlays(context)
        val fullScreen =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                NotificationManagerCompat.from(context).canUseFullScreenIntent()
            } else {
                true
            }
        return !overlay || !fullScreen
    }
}
