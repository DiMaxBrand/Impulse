package eu.siacs.conversations.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

object UpdateDownloader {

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun startDownload(context: Context, info: UpdateInfo): Long {
        val fileName = "impulse-update-${info.versionName}.apk"
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("Impulse ${info.versionName}")
            .setDescription("Downloading update…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        return dm.enqueue(request)
    }

    fun cancelDownload(context: Context, downloadId: Long) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.remove(downloadId)
    }

    fun queryProgress(context: Context, downloadId: Long): DownloadProgress {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        dm.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return DownloadProgress.Unknown
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val fraction = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f
            return when (status) {
                DownloadManager.STATUS_RUNNING -> DownloadProgress.InProgress(fraction, statusText = null)
                DownloadManager.STATUS_PENDING -> DownloadProgress.InProgress(fraction, statusText = "Queued…")
                DownloadManager.STATUS_PAUSED ->
                    DownloadProgress.InProgress(fraction, statusText = pausedReasonText(reason))
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    DownloadProgress.Complete(localUri)
                }
                DownloadManager.STATUS_FAILED -> DownloadProgress.Failed(failedReasonText(reason))
                else -> DownloadProgress.Unknown
            }
        }
    }

    private fun pausedReasonText(reason: Int): String = when (reason) {
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for network…"
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "Connection lost, retrying…"
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi…"
        DownloadManager.PAUSED_UNKNOWN -> "Paused…"
        else -> "Paused…"
    }

    private fun failedReasonText(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage space"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage not available"
        DownloadManager.ERROR_CANNOT_RESUME -> "Connection interrupted, couldn't resume"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "Network error"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Server redirect error"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Server error"
        DownloadManager.ERROR_FILE_ERROR -> "File error"
        else -> "Download failed"
    }

    fun installApk(context: Context, filePath: String) {
        val file = File(Uri.parse(filePath).path ?: filePath)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    sealed class DownloadProgress {
        data class InProgress(val fraction: Float, val statusText: String? = null) : DownloadProgress()
        data class Complete(val localUri: String) : DownloadProgress()
        data class Failed(val reasonText: String = "Download failed") : DownloadProgress()
        object Unknown : DownloadProgress()
    }
}
