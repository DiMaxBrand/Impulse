package eu.siacs.conversations.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.AttributeSet
import android.view.View
import com.google.common.base.Strings
import eu.siacs.conversations.BuildConfig
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.activity.DeveloperOptionsActivity

@Suppress("DEPRECATION")
class AboutPreference : android.preference.Preference {
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        setSummaryAndTitle(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        setSummaryAndTitle(context)
    }

    private fun setSummaryAndTitle(context: Context) {
        val appName = context.getString(R.string.app_name)
        summary = String.format(
            "%s %s %s (%s)",
            appName,
            BuildConfig.VERSION_NAME,
            im.conversations.webrtc.BuildConfig.WEBRTC_VERSION,
            Strings.nullToEmpty(Build.DEVICE),
        )
        title = context.getString(R.string.title_activity_about_x, appName)
    }

    // Hidden entry point: long-pressing this row opens Developer Options. Two earlier attempts
    // failed for reasons rooted in this legacy ListView-hosted row's own built-in gesture
    // handling: multi-finger taps got armed-and-committed by the list's click detection before
    // a second/third pointer registered, and a custom 3-second hold via raw touch tracking got
    // cancelled by the list's own long-press detector at the standard ~500ms threshold (which
    // sends ACTION_CANCEL to the child view once it claims the gesture for itself). Using the
    // platform's real setOnLongClickListener works because it's the one gesture this widget is
    // actually built to cooperate with, rather than fighting its internals further.
    override fun onBindView(view: View) {
        super.onBindView(view)
        view.setOnLongClickListener {
            it.context.startActivity(Intent(it.context, DeveloperOptionsActivity::class.java))
            true
        }
    }

    override fun onClick() {
        super.onClick()
        context.startActivity(Intent(context, AboutActivity::class.java))
    }
}
