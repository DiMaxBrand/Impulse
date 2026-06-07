package eu.siacs.conversations.ui

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.ui.util.SettingsUtils

abstract class BaseActivity : AppCompatActivity() {
    private var isDynamicColors: Boolean? = null

    override fun onStart() {
        super.onStart()
        val appSettings = AppSettings(this)
        val desiredNightMode = appSettings.desiredNightMode
        if (setDesiredNightMode(desiredNightMode)) {
            return
        }
        val isDynamicColors = appSettings.isDynamicColorsDesired
        setDynamicColors(isDynamicColors)
    }

    override fun onResume() {
        super.onResume()
        SettingsUtils.applyScreenshotSetting(this)
    }

    fun setDynamicColors(isDynamicColors: Boolean) {
        if (this.isDynamicColors == null) {
            this.isDynamicColors = isDynamicColors
        } else {
            if (this.isDynamicColors != isDynamicColors) {
                Log.i(
                    "Recreating {} because dynamic color setting has changed",
                    javaClass.simpleName
                )
                recreate()
            }
        }
    }

    fun setDesiredNightMode(desiredNightMode: Int): Boolean {
        if (desiredNightMode == AppCompatDelegate.getDefaultNightMode()) {
            return false
        }
        AppCompatDelegate.setDefaultNightMode(desiredNightMode)
        Log.i("Recreating {} because desired night mode has changed", javaClass.simpleName)
        recreate()
        return true
    }
}
