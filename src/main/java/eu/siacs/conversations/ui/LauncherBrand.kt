package eu.siacs.conversations.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** The launcher component is the source of truth for the selected artwork. */
object LauncherBrand {
    @JvmStatic
    fun isImpulse(context: Context): Boolean = context.packageManager.getComponentEnabledSetting(
        ComponentName(context, "eu.siacs.conversations.ui.ImpulseLauncherIcon")
    ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
}
