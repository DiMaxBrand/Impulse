package eu.siacs.conversations.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import eu.siacs.conversations.update.UpdateChecker
import eu.siacs.conversations.update.UpdateDownloader
import eu.siacs.conversations.update.UpdatePreferences
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val prefs = UpdatePreferences(applicationContext)
        if (!prefs.autoCheck) return Result.success()

        val checker = UpdateChecker(OkHttpClient())
        val result = checker.checkForUpdate(prefs.selectedChannel)
        if (result !is UpdateChecker.CheckResult.UpdateAvailable) return Result.success()
        val info = result.info

        prefs.pendingUpdateVersion = info.versionName
        prefs.pendingUpdateUrl = info.downloadUrl

        if (UpdateDownloader.isUnmeteredNetworkAvailable(applicationContext)) {
            prefs.pendingNoWifi = false
            val id = UpdateDownloader.startDownload(applicationContext, info)
            prefs.activeDownloadId = id
        } else {
            prefs.pendingNoWifi = true
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "weekly_update_check"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
