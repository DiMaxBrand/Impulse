package eu.siacs.conversations.worker

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import eu.siacs.conversations.BuildConfig
import eu.siacs.conversations.Config
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.update.SilentInstaller
import eu.siacs.conversations.update.UpdateNotifications
import eu.siacs.conversations.update.UpdateCheckHelper
import eu.siacs.conversations.update.UpdatePreferences
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * The nightly self-update, around 02:30 local time. Checks for an update and, on Wi-Fi,
 * downloads it ([UpdateCheckHelper.performCheck]); then, if nothing is going on, installs it by
 * itself so the app is already current in the morning.
 *
 * "Nothing is going on" means: no call (ongoing or ringing) and the app not on screen -- an
 * install replaces the app, which would cut a call off or pull the screen away from the user.
 * It also has to be night (this can run late if the phone was off at 02:30), the nightly install
 * switch must be on, and Android 12+ must be willing to install without a prompt.
 * If any of that fails, the update simply stays downloaded for the usual "Install" button in the
 * update sheet.
 *
 * Previously this worker only checked, once a day at 10:00 local; that schedule is kept below
 * (disabled) for reference.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        UpdateCheckHelper.performCheck(applicationContext)
        installIfQuiet(applicationContext)
        return Result.success()
    }

    private fun installIfQuiet(context: Context) {
        val prefs = UpdatePreferences(context)
        if (!prefs.nightlyInstall || !prefs.downloadedApkExists()) return
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour !in NIGHT_START_HOUR until NIGHT_END_HOUR) return
        if (!SilentInstaller.canTry(context)) return
        if (XmppConnectionService.isCallBusy()) {
            Log.d(Config.LOGTAG, "nightly update: a call is going on, not installing")
            return
        }
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            Log.d(Config.LOGTAG, "nightly update: app is on screen, not installing")
            return
        }
        val path = prefs.downloadedApkPath ?: return
        val file = File(android.net.Uri.parse(path).path ?: path)
        if (!file.exists()) return
        if (prefs.silentInstallBroken) {
            // This phone refuses silent installs (Xiaomi does): don't try again every night. Leave
            // one quiet "update ready" notification per version; the update sheet installs it.
            val version = prefs.downloadedVersion
            if (version != null && prefs.readyNotifiedVersion != version) {
                prefs.readyNotifiedVersion = version
                UpdateNotifications.postReadyToInstall(context, null)
            }
            return
        }
        // Written before the install: the process is replaced by it.
        prefs.rememberJustUpdated()
        if (!SilentInstaller.install(context, file)) {
            prefs.clearJustUpdated()
        }
    }

    companion object {
        // Disabled: the old schedule, one check a day at 10:00 local that only checked and
        // downloaded. Superseded by the nightly 02:30 run above; the name is cancelled in
        // schedule() so devices that still have it enqueued stop running it.
        private const val DAILY_10AM_WORK_NAME = "daily_update_check"
        private const val OLD_WORK_NAME = "weekly_update_check"

        private const val WORK_NAME = "nightly_update_check"
        private const val NIGHT_HOUR = 2
        private const val NIGHT_MINUTE = 30
        // Window in which an install is allowed (local hours, end exclusive).
        private const val NIGHT_START_HOUR = 1
        private const val NIGHT_END_HOUR = 6

        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(OLD_WORK_NAME)
            workManager.cancelUniqueWork(DAILY_10AM_WORK_NAME)

            val now = Calendar.getInstance()
            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, NIGHT_HOUR)
                set(Calendar.MINUTE, NIGHT_MINUTE)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val initialDelay = next.timeInMillis - now.timeInMillis
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
