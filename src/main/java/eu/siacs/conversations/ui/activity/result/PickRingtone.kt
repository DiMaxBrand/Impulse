package eu.siacs.conversations.ui.activity.result

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

class PickRingtone(private val ringToneType: Int) : ActivityResultContract<Uri?, Uri?>() {

    override fun createIntent(context: Context, existing: Uri?): Intent {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, ringToneType)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        if (noneToNull(existing) != null) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
        }
        return intent
    }

    override fun parseResult(resultCode: Int, data: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return null
        }
        return nullToNone(data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI))
    }

    companion object {
        private val NONE: Uri = Uri.parse("about:blank")

        @JvmStatic
        fun noneToNull(uri: Uri?): Uri? {
            return if (uri == null || NONE == uri) null else uri
        }

        @JvmStatic
        fun nullToNone(uri: Uri?): Uri {
            return uri ?: NONE
        }
    }
}
