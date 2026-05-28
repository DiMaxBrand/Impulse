package eu.siacs.conversations.ui

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.common.base.Strings
import eu.siacs.conversations.Config

class UnifiedPushDistributor : AppCompatActivity() {
    companion object {
        private const val DUMMY_APP = "org.unifiedpush.dummy_app"
        private const val EXTRA_PENDING_INTENT = "pi"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        val callingPackage = callingPackage
        val result = Intent()
        if (intent == null || Strings.isNullOrEmpty(callingPackage)) {
            setResult(RESULT_CANCELED, result)
            finish()
            return
        }
        Log.d(Config.LOGTAG, "a package ($callingPackage) called our link activity")
        val pendingIntent = PendingIntent.getBroadcast(this, 0, Intent(DUMMY_APP), PendingIntent.FLAG_IMMUTABLE)
        result.putExtra(EXTRA_PENDING_INTENT, pendingIntent)
        setResult(RESULT_OK, result)
        finish()
    }
}
