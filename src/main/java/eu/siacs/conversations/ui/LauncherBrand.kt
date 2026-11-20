package eu.siacs.conversations.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** The launcher component is the source of truth for the selected artwork. */
object LauncherBrand {
    fun migrateToImpulse(context: Context) {
        val prefs = context.getSharedPreferences("launcher_brand_migrations", Context.MODE_PRIVATE)
        if (prefs.getBoolean("impulse_116", false)) return
        val manager = context.packageManager
        val old = ComponentName(context, "eu.siacs.conversations.ui.ConversationActivity")
        val new = ComponentName(context, "eu.siacs.conversations.ui.ImpulseLauncherIcon")
        if (Build.VERSION.SDK_INT >= 33) {
            manager.setComponentEnabledSettings(listOf(
                PackageManager.ComponentEnabledSetting(new, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP),
                PackageManager.ComponentEnabledSetting(old, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP),
            ))
        } else {
            manager.setComponentEnabledSetting(new, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            manager.setComponentEnabledSetting(old, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        prefs.edit().putBoolean("impulse_116", true).apply()
    }

    @JvmStatic
    fun isImpulse(context: Context): Boolean = context.packageManager.getComponentEnabledSetting(
        ComponentName(context, "eu.siacs.conversations.ui.ImpulseLauncherIcon")
    ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
}
