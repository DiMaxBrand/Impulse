package eu.siacs.conversations.worker

import android.content.Context
import android.os.Environment
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import eu.siacs.conversations.update.UpdateDownloader
import eu.siacs.conversations.update.UpdatePreferences
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Nightly wipe of the dedicated update-APK subfolder. That folder holds nothing but our own
 * downloaded update APKs — never shared with received attachments or anything else — so a full,
 * unconditional wipe every night is always safe. */
class ApkCleanupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val dir = File(
            applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            UpdateDownloader.UPDATES_SUBDIR,
        )
        dir.listFiles()?.forEach { it.delete() }
        UpdatePreferences(applicationContext).clearDownload()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "nightly_apk_cleanup"

        fun schedule(context: Context) {
            val now = Calendar.getInstance()
            val nextMidnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val initialDelay = nextMidnight.timeInMillis - now.timeInMillis
            val request = PeriodicWorkRequestBuilder<ApkCleanupWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
