package eu.siacs.conversations.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import eu.siacs.conversations.update.UpdateCheckHelper
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Checks for updates once daily at 10:00 local time, instead of on every app launch. On a
 * hit: auto-downloads on wifi, or marks it pending so the sheet shows a manual Download button
 * otherwise (see [UpdateCheckHelper.performCheck]). This worker only ever updates prefs, never
 * touches UI — the next time the app is opened in the foreground,
 * ConversationsActivity.maybeShowUpdateSheet() shows the sheet based on that state via
 * UpdateSheetFragment.shouldShow(). Runs for every channel; beta/alpha additionally get a check
 * on app launch too, via UpdateCheckHelper.checkOnLaunchIfEligible(). */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        UpdateCheckHelper.performCheck(applicationContext)
        return Result.success()
    }

    companion object {
        // Migrating off the old untimed weekly schedule onto the new daily-at-10:00 one.
        private const val OLD_WORK_NAME = "weekly_update_check"
        private const val WORK_NAME = "daily_update_check"

        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(OLD_WORK_NAME)

            val now = Calendar.getInstance()
            val next10am = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val initialDelay = next10am.timeInMillis - now.timeInMillis
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
