package eu.siacs.conversations.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import eu.siacs.conversations.R

object TorServiceUtils {

    private const val URI_ORBOT = "org.torproject.android"
    private val ORBOT_PLAYSTORE_URI: Uri = Uri.parse("market://details?id=$URI_ORBOT")
    private const val ACTION_START_TOR = "org.torproject.android.START_TOR"

    @JvmField
    val INSTALL_INTENT: Intent = Intent(Intent.ACTION_VIEW, ORBOT_PLAYSTORE_URI)

    @JvmField
    val LAUNCH_INTENT: Intent = Intent(ACTION_START_TOR)

    const val ACTION_STATUS = "org.torproject.android.intent.action.STATUS"
    const val EXTRA_STATUS = "org.torproject.android.intent.extra.STATUS"

    @JvmStatic
    fun isOrbotInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(URI_ORBOT, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: RuntimeException) {
            false
        }
    }

    @JvmStatic
    fun downloadOrbot(activity: Activity, requestCode: Int) {
        try {
            activity.startActivityForResult(INSTALL_INTENT, requestCode)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.no_market_app_installed, Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun startOrbot(activity: Activity, requestCode: Int) {
        try {
            activity.startActivityForResult(LAUNCH_INTENT, requestCode)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.install_orbot, Toast.LENGTH_LONG).show()
        }
    }
}
