package eu.siacs.conversations.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import eu.siacs.conversations.Config
import eu.siacs.conversations.R

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
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                notifyConfirm(context, confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                // Failed: forget the "just updated" marker so no false "Impulse was updated" shows.
                UpdatePreferences(context).clearJustUpdated()
            }
        }
    }

    private fun notifyConfirm(context: Context, confirm: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.update_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        val pending =
            PendingIntent.getActivity(
                context,
                147,
                confirm,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_system_update_24dp)
                .setContentTitle(context.getString(R.string.update_ready_notification_title))
                .setContentText(context.getString(R.string.update_ready_notification_text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "app_updates"
        const val NOTIFICATION_ID = 90018
    }
}
