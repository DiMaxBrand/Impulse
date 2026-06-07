package eu.siacs.conversations.ui.activity.result

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.google.common.base.Strings
import eu.siacs.conversations.ui.ScanQrCodeActivity

class ScanQrCode : ActivityResultContract<Void?, String?>() {
    override fun createIntent(context: Context, input: Void?): Intent =
        Intent(context, ScanQrCodeActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        if (resultCode == ScanQrCodeActivity.RESULT_OK && intent != null) {
            return Strings.nullToEmpty(intent.getStringExtra(ScanQrCodeActivity.INTENT_EXTRA_RESULT))
        }
        return null
    }
}
