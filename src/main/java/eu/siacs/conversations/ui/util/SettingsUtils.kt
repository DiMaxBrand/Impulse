package eu.siacs.conversations.ui.util

import android.app.Activity
import android.app.Application
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import eu.siacs.conversations.AppSettings

object SettingsUtils {
    @JvmStatic
    fun applyScreenshotSetting(activity: Activity) {
        val window = activity.window
        if (AppSettings(activity).isAllowScreenshots) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    @JvmStatic
    fun applyThemeSettings(application: Application) {
        AppCompatDelegate.setDefaultNightMode(AppSettings(application).desiredNightMode)
        val options = DynamicColorsOptions.Builder()
            .setPrecondition { activity, _ -> AppSettings(activity).isDynamicColorsDesired }
            .build()
        DynamicColors.applyToActivitiesIfAvailable(application, options)
    }
}
