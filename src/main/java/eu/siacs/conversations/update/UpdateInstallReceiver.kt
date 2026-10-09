package eu.siacs.conversations.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import eu.siacs.conversations.Config

/** Result of a [SilentInstaller] session. Success never really arrives here (the app is replaced
 * and restarted); the useful cases are "Android wants the user to confirm" (post a notification,
 * because a background app cannot start the confirmation screen) and failure. */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        @Suppress("DEPRECATION")
        val confirm: Intent? =
            if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
                null
            } else if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
        // Who is asking for the confirmation is the most useful clue about why the install was
        // not silent (the system installer, Play Protect, a Samsung component ...).
        val askedBy =
            confirm?.let {
                "confirmation asked by: " +
                    (it.component?.flattenToShortString() ?: it.`package` ?: "?") +
                    (it.action?.let { action -> "\naction: $action" } ?: "")
            }
        val requestedPackage = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        val otherPackage =
            intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME)
        val extra =
            listOfNotNull(
                    askedBy,
                    requestedPackage?.let { "package: $it" },
                    otherPackage?.let { "other package: $it" },
                )
                .joinToString("\n")
                .ifEmpty { null }
        Log.d(Config.LOGTAG, "update install status=$status message=$message extra=$extra")
        InstallStatusBus.publish(status, message, extra)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                confirm ?: return
                UpdateNotifications.postReadyToInstall(context, confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val prefs = UpdatePreferences(context)
                // Failed: forget the "just updated" marker so no false "Impulse was updated" shows.
                prefs.clearJustUpdated()
                // The phone refused the silent session outright (Xiaomi's installer answers
                // "INSTALL_FAILED_ABORTED: Permission denied"). Stop trying it every night; the
                // update waits for the user via a quiet notification and the update sheet instead.
                // (A deliberate cancel of a confirmation screen is not "refused".)
                if (message?.contains("Permission denied", ignoreCase = true) == true ||
                    status == PackageInstaller.STATUS_FAILURE_BLOCKED
                ) {
                    prefs.silentInstallBroken = true
                }
                if (prefs.downloadedApkExists() && message?.contains("cancel", ignoreCase = true) != true) {
                    UpdateNotifications.postReadyToInstall(context, null)
                }
            }
        }
    }
}
