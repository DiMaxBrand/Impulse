package eu.siacs.conversations.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import eu.siacs.conversations.utils.SignupUtils

open class ConversationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(SignupUtils.getRedirectionIntent(this))
        Handler(Looper.getMainLooper()).post { finish() }
    }
}

/** Target for both launcher aliases; the original component name remains the default alias. */
class LauncherHostActivity : ConversationActivity()
