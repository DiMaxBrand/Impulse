package eu.siacs.conversations.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import de.gultsch.common.MiniUri
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import eu.siacs.conversations.utils.XmppUriLauncher

class YuriLauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = getIntent()
        val data = if (intent == null) null else intent.data
        if (data == null) {
            Handler(Looper.getMainLooper()).post { finish() }
            return
        }
        val uri: MiniUri
        try {
            uri = MiniUri.tryInternalParse(data.toString())
        } catch (e: IllegalArgumentException) {
            Log.d(Config.LOGTAG, "could not parse mini uri", e)
            Toast.makeText(this, R.string.invalid_jid, Toast.LENGTH_LONG).show()
            Handler(Looper.getMainLooper()).post { finish() }
            return
        }
        val xmpp: MiniUri.Xmpp
        if (uri is MiniUri.Xmpp) {
            xmpp = uri
        } else if (uri is MiniUri.Transformable && uri.transform() is MiniUri.Xmpp) {
            xmpp = uri.transform() as MiniUri.Xmpp
        } else {
            Log.d(Config.LOGTAG, "mini uri is of unknown type: " + uri.javaClass.simpleName)
            Toast.makeText(this, R.string.invalid_jid, Toast.LENGTH_LONG).show()
            Handler(Looper.getMainLooper()).post { finish() }
            return
        }
        val launcher = XmppUriLauncher(this)
        launcher.launch(xmpp)
        Handler(Looper.getMainLooper()).post { finish() }
    }
}
