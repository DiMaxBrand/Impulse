package eu.siacs.conversations.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.google.common.base.Strings
import eu.siacs.conversations.R
import eu.siacs.conversations.persistance.FileBackend
import eu.siacs.conversations.utils.MimeUtils
import java.io.File
import java.util.Objects

object ViewUtil {

    const val WILDCARD = "*/*"

    @JvmStatic
    fun view(context: Context, attachment: Attachment) {
        val file = File(Objects.requireNonNull(attachment.getUri().path))
        view(context, file, attachment.getUuid().toString(), nullToWildcard(attachment.getMime()))
    }

    @JvmStatic
    fun view(context: Context, file: File, uuid: String) {
        if (!file.exists()) {
            Toast.makeText(context, R.string.file_deleted, Toast.LENGTH_SHORT).show()
            return
        }
        val mime = nullToWildcard(MimeUtils.getMimeType(file))
        view(context, file, uuid, mime)
    }

    @JvmStatic
    fun nullToWildcard(mime: String?): String {
        return if (Strings.isNullOrEmpty(mime)) WILDCARD else mime!!
    }

    private fun view(context: Context, file: File, uuid: String, mime: String) {
        val openIntent = Intent(Intent.ACTION_VIEW)
        val uri: Uri
        try {
            uri = FileBackend.getUriForFile(context, file)
                .buildUpon()
                .appendQueryParameter("uuid", uuid)
                .build()
        } catch (e: SecurityException) {
            Toast.makeText(
                context,
                context.getString(R.string.no_permission_to_access_x, file.absolutePath),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        openIntent.setDataAndType(uri, mime)
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(openIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT)
                .show()
        }
    }
}
