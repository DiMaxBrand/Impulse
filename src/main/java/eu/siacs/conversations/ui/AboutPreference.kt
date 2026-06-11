package eu.siacs.conversations.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.AttributeSet
import com.google.common.base.Strings
import eu.siacs.conversations.BuildConfig
import eu.siacs.conversations.R

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

    override fun onClick() {
        super.onClick()
        context.startActivity(Intent(context, AboutActivity::class.java))
    }
}
