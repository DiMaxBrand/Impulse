package eu.siacs.conversations.ui.util

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.ui.tunedSeed

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
        // Options are built per activity: the accent seed can change at runtime, and Material's
        // content-based seed is baked into the options object. With dynamic colours on we hand the
        // system wallpaper palette over to Material; otherwise the seed (default: the app's green)
        // is applied to the View screens, matching what the Compose screens generate.
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                    apply(activity)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) apply(activity)
                }

                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}

                private fun apply(activity: Activity) {
                    val settings = AppSettings(activity)
                    val dynamic = settings.isDynamicColorsDesired
                    val custom = !dynamic && settings.accentSeed != AppSettings.DEFAULT_ACCENT_SEED
                    // Default green without dynamic colours: leave the View theme's own palette alone.
                    if (!dynamic && !custom) return
                    val builder = DynamicColorsOptions.Builder()
                    // The default green is the View theme's own palette: no overlay needed.
                    if (custom) builder.setContentBasedSource(tunedSeed(settings.accentSeed))
                    DynamicColors.applyToActivityIfAvailable(activity, builder.build())
                }
            }
        )
    }
}
