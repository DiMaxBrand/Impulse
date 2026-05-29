package eu.siacs.conversations.android

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.google.common.base.Strings
import eu.siacs.conversations.Config

class Device(private val context: Context) {

    fun isScreenLocked(): Boolean {
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val locked = keyguardManager != null && keyguardManager.isKeyguardLocked
        val interactive: Boolean
        try {
            interactive = powerManager != null && powerManager.isInteractive
        } catch (e: Exception) {
            return false
        }
        return locked || !interactive
    }

    fun isPhoneSilenced(includeSilentModes: Boolean): Boolean {
        return try {
            isPhoneSilencedUnchecked(includeSilentModes)
        } catch (throwable: Throwable) {
            Log.e(Config.LOGTAG, "could not check DND mode", throwable)
            false
        }
    }

    private fun isPhoneSilencedUnchecked(includeSilentModes: Boolean): Boolean {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val filter =
            notificationManager?.currentInterruptionFilter
                ?: NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        if (filter >= NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
            return true
        }
        if (includeSilentModes) {
            val audioManager = context.getSystemService(AudioManager::class.java)
            val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
            return AudioManager.RINGER_MODE_NORMAL != ringerMode
        } else {
            return false
        }
    }

    fun isPhysicalDevice(): Boolean = !isEmulator()

    fun getDeviceName(): String =
        String.format(
            "%s %s",
            Strings.nullToEmpty(Build.MANUFACTURER).trim(),
            Strings.nullToEmpty(Build.MODEL).trim()
        )

    companion object {
        private fun isEmulator(): Boolean {
            return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("sdk_gphone64_arm64")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
        }
    }
}
