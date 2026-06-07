package eu.siacs.conversations.utils

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import eu.siacs.conversations.Config
import eu.siacs.conversations.receiver.SystemEventReceiver

object Compatibility {

    @JvmStatic
    fun hasStoragePermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            || ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun s(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @JvmStatic
    fun twentySix(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    @JvmStatic
    fun twentyEight(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    @JvmStatic
    fun thirtyFour(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    @JvmStatic
    fun startService(context: Context, intent: Intent) {
        try {
            if (twentySix()) {
                intent.putExtra(SystemEventReceiver.EXTRA_NEEDS_FOREGROUND_SERVICE, true)
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        } catch (e: RuntimeException) {
            Log.d(
                Config.LOGTAG,
                "${context.javaClass.simpleName} was unable to start service"
            )
        }
    }

    @SuppressLint("UnsupportedChromeOsCameraSystemFeature")
    @JvmStatic
    fun hasFeatureCamera(context: Context): Boolean {
        val packageManager = context.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @JvmStatic
    fun getRestrictBackgroundStatus(@NonNull connectivityManager: ConnectivityManager): Int {
        return try {
            connectivityManager.restrictBackgroundStatus
        } catch (e: Exception) {
            Log.d(
                Config.LOGTAG,
                "platform bug detected. Unable to get restrict background status",
                e
            )
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @JvmStatic
    fun isActiveNetworkMetered(@NonNull connectivityManager: ConnectivityManager): Boolean {
        return try {
            connectivityManager.isActiveNetworkMetered
        } catch (e: RuntimeException) {
            // when in doubt better assume it's metered
            true
        }
    }

    @JvmStatic
    fun pgpStartIntentSenderOptions(): Bundle? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                .toBundle()
        } else {
            null
        }
    }
}
