package eu.siacs.conversations.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import eu.siacs.conversations.Config
import java.io.File

/**
 * Installs a downloaded update through a PackageInstaller session, asking Android not to show
 * its confirmation screen. On Android 12+ that is granted when Impulse is the installer of
 * record for itself (so the very first update done this way still asks once) and the
 * UPDATE_PACKAGES_WITHOUT_USER_ACTION permission is declared; otherwise Android answers
 * STATUS_PENDING_USER_ACTION and [UpdateInstallReceiver] turns that into a "tap to finish"
 * notification. Used by the nightly update; the manual "Install" button still goes through
 * [UpdateDownloader.installApk].
 */
object SilentInstaller {

    const val ACTION_INSTALL_STATUS = "eu.siacs.conversations.UPDATE_INSTALL_STATUS"

    fun canTry(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.packageManager.canRequestPackageInstalls()

    /**
     * Developer Options test: runs the same silent install the nightly update uses and returns
     * what it tried to install -- a newer downloaded update if one is ready, otherwise the
     * app's own APK (the current version) over itself. The result arrives later on
     * [InstallStatusBus]; if the install succeeds the app is replaced and restarts, so there may
     * be no result to show at all. Returns null when the session could not even be started.
     */
    fun testInstall(context: Context): String? {
        val prefs = UpdatePreferences(context)
        val newer = prefs.downloadedApkPath?.let { path ->
            File(android.net.Uri.parse(path).path ?: path)
        }
        val (file, label) =
            if (newer != null && newer.exists() && UpdateChecker.isNewerThanInstalled(prefs.downloadedVersion)) {
                newer to "the downloaded update ${prefs.downloadedVersion}"
            } else {
                File(context.applicationInfo.sourceDir) to "the installed version again"
            }
        return if (install(context, file)) label else null
    }

    /** Returns true if the session was committed (not that the install already succeeded). */
    fun install(context: Context, apk: File): Boolean {
        return try {
            val installer = context.packageManager.packageInstaller
            val params =
                PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                    .apply {
                        setAppPackageName(context.packageName)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            setRequireUserAction(
                                PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                            )
                        }
                    }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("impulse-update.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val intent = Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName)
                // Mutable: the system fills in the status extras.
                val pending =
                    PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                session.commit(pending.intentSender)
            }
            true
        } catch (e: Exception) {
            Log.w(Config.LOGTAG, "silent update install failed to start", e)
            false
        }
    }
}
