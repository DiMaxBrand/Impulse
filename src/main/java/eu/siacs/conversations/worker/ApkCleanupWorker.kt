package eu.siacs.conversations.worker

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import eu.siacs.conversations.update.UpdatePreferences
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Nightly sweep of leftover update APKs in the app's Downloads dir, keeping only the one
 * currently tracked as installable (if any) so a pending, not-yet-installed update survives. */
class ApkCleanupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val dir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val files = dir?.listFiles { file ->
            file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX)
        } ?: return Result.success()

        val prefs = UpdatePreferences(applicationContext)
        val keepPath = prefs.downloadedApkPath?.let { File(Uri.parse(it).path ?: it).absolutePath }

        for (file in files) {
            if (file.absolutePath != keepPath) {
                file.delete()
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "nightly_apk_cleanup"
        private const val FILE_PREFIX = "impulse-update-"
        private const val FILE_SUFFIX = ".apk"

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
