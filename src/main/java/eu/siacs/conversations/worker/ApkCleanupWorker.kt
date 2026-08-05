package eu.siacs.conversations.worker

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import eu.siacs.conversations.update.UpdateChecker
import eu.siacs.conversations.update.UpdateDownloader
import eu.siacs.conversations.update.UpdatePreferences
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Nightly cleanup of the dedicated update-APK subfolder. That folder holds nothing but our own
 * downloaded update APKs — never shared with received attachments or anything else — but a full,
 * *unconditional* wipe is NOT always safe: a download can be actively writing into it at exactly
 * this moment, and a legitimately downloaded, not-yet-installed update (the user just hasn't
 * gotten to it yet) is not an orphaned leftover. This only ever deletes files that are neither. */
class ApkCleanupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val prefs = UpdatePreferences(applicationContext)
        // Never touch the folder while a download is in flight — DownloadManager may still be
        // writing to a file in here right now; deleting mid-write would corrupt it. It'll get
        // swept on a later night once it's no longer active.
        if (prefs.activeDownloadId != -1L) return Result.success()

        val dir = File(
            applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            UpdateDownloader.UPDATES_SUBDIR,
        )
        // A downloaded update that's still newer than what's installed is worth keeping — the user
        // simply hasn't tapped install yet, not something to wipe out from under them.
        val keepValid = prefs.downloadedApkPath != null &&
            prefs.downloadedApkExists() &&
            UpdateChecker.isNewerThanInstalled(prefs.downloadedVersion)
        val keepPath = if (keepValid) {
            File(Uri.parse(prefs.downloadedApkPath).path ?: prefs.downloadedApkPath!!).canonicalPath
        } else null
        dir.listFiles()?.forEach { file ->
            if (file.canonicalPath != keepPath) file.delete()
        }
        // Only reconcile prefs if what they point to is actually gone/stale now — the same
        // self-heal UpdateSheetFragment.initState() already does on open, not a blind wipe that
        // would erase a still-valid pending install's title/notes along with everything else.
        if (prefs.downloadedApkPath != null && !keepValid) {
            prefs.clearDownload()
        }
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
