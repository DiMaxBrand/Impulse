package eu.siacs.conversations.ui.text

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Spannable
import android.text.Spanned
import android.text.style.URLSpan
import android.util.Log
import android.view.SoundEffectConstants
import android.view.View
import android.widget.Toast
import com.google.common.base.Joiner
import de.gultsch.common.MiniUri
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.ConversationsActivity
import eu.siacs.conversations.ui.ShowLocationActivity
import eu.siacs.conversations.ui.YuriLauncherActivity

@SuppressLint("ParcelCreator")
class FixedURLSpan private constructor(url: String) : URLSpan(url) {

    override fun onClick(widget: View) {
        val uri: MiniUri = try {
            MiniUri.asMiniUri(url)
        } catch (e: IllegalArgumentException) {
            return
        }
        val context: Context = widget.context
        val asXmppUri: MiniUri.Xmpp? = when {
            uri is MiniUri.Xmpp -> uri
            uri is MiniUri.Transformable && uri.transform() is MiniUri.Xmpp -> uri.transform() as MiniUri.Xmpp
            else -> null
        }
        if (asXmppUri != null && asXmppUri.isAddress) {
            if (context is ConversationsActivity && context.onXmppUriClicked(asXmppUri)) {
                Log.d(Config.LOGTAG, "handled xmpp uri internally")
                widget.playSoundEffect(SoundEffectConstants.CLICK)
                return
            }
            val intent = Intent(context, YuriLauncherActivity::class.java)
            intent.data = asXmppUri.asUri()
            startActivity(widget, intent)
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, uri.asUri())
        if ("web+ap" == uri.scheme) {
            if (intent.resolveActivity(context.packageManager) == null) {
                Log.d(Config.LOGTAG, "no app found to handle web+ap")
                val webApAsHttps = Uri.parse(
                    "https://${uri.authority}/${Joiner.on('/').join(uri.pathSegments)}"
                )
                val viewHttpsIntent = Intent(Intent.ACTION_VIEW, webApAsHttps)
                startActivity(widget, viewHttpsIntent)
                return
            }
        }
        if ("geo" == uri.scheme) {
            intent.setClass(context, ShowLocationActivity::class.java)
        } else {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        }
        startActivity(widget, intent)
    }

    private fun startActivity(widget: View, intent: Intent) {
        val context = widget.context
        try {
            context.startActivity(intent)
            widget.playSoundEffect(SoundEffectConstants.CLICK)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_application_found_to_open_link, Toast.LENGTH_SHORT)
                .show()
        }
    }

    companion object {
        @JvmStatic
        fun fix(spannable: Spannable) {
            for (urlspan in spannable.getSpans(0, spannable.length - 1, URLSpan::class.java)) {
                val start = spannable.getSpanStart(urlspan)
                val end = spannable.getSpanEnd(urlspan)
                spannable.removeSpan(urlspan)
                spannable.setSpan(
                    FixedURLSpan(urlspan.url),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }
}
