package eu.siacs.conversations.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import com.google.android.material.elevation.SurfaceColors

object Activities {

    @JvmStatic
    fun setStatusAndNavigationBarColors(activity: Activity, view: View) {
        setStatusAndNavigationBarColors(activity, view, false)
    }

    @JvmStatic
    fun setStatusAndNavigationBarColors(activity: Activity, view: View, raisedStatusBar: Boolean) {
        setStatusAndNavigationBarColors(activity, view, isLightMode(activity), raisedStatusBar)
    }

    @JvmStatic
    fun setStatusAndNavigationBarColors(
        activity: Activity,
        view: View,
        isLightMode: Boolean,
        raisedStatusBar: Boolean
    ) {
        val window = activity.window
        val flags = view.systemUiVisibility
        // an elevation of 4 matches the MaterialToolbar elevation
        if (raisedStatusBar) {
            window.statusBarColor = SurfaceColors.SURFACE_5.getColor(activity)
        } else {
            window.statusBarColor = SurfaceColors.SURFACE_0.getColor(activity)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.navigationBarColor = SurfaceColors.SURFACE_1.getColor(activity)
            if (isLightMode) {
                view.systemUiVisibility =
                    flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        } else if (isLightMode) {
            view.systemUiVisibility = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    private fun isLightMode(context: Context): Boolean {
        val nightModeFlags =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags != Configuration.UI_MODE_NIGHT_YES
    }

    @JvmStatic
    fun isNightMode(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }
}
