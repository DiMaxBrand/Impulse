package eu.siacs.conversations.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import eu.siacs.conversations.R

/**
 * The quiet "Update ready" notification: used when the overnight update cannot install by itself
 * -- Android asked for a confirmation (then [confirm] is that confirmation screen) or this phone
 * refuses silent installs altogether (then it just opens the app, whose update sheet does the
 * installing). Low importance: no sound, no vibration, no screen wake.
 */
object UpdateNotifications {
    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 90018

    fun postReadyToInstall(context: Context, confirm: Intent?) {
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
        val target =
            confirm?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                ?: context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: return
        val pending =
            PendingIntent.getActivity(
                context,
                147,
                target,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_system_update_24dp)
                .setContentTitle(context.getString(R.string.update_ready_notification_title))
                .setContentText(
                    context.getString(
                        if (confirm != null) R.string.update_ready_notification_text
                        else R.string.update_ready_open_notification_text
                    )
                )
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
