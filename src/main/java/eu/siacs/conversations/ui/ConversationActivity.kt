package eu.siacs.conversations.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import eu.siacs.conversations.utils.SignupUtils

open class ConversationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (LauncherBrand.isImpulse(this)) {
            window.setBackgroundDrawableResource(eu.siacs.conversations.R.drawable.background_splash_impulse)
        }
        super.onCreate(savedInstanceState)
        startActivity(SignupUtils.getRedirectionIntent(this))
        Handler(Looper.getMainLooper()).post { finish() }
    }
}

/** Target for the original launcher component. */
class LauncherHostActivity : ConversationActivity()

/** Separate target lets Android select the Impulse splash before the activity starts. */
class ImpulseLauncherHostActivity : ConversationActivity()
