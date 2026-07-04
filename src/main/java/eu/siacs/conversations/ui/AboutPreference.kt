package eu.siacs.conversations.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.common.base.Strings
import eu.siacs.conversations.BuildConfig
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.activity.DeveloperOptionsActivity

@Suppress("DEPRECATION")
class AboutPreference : android.preference.Preference {
    // Hidden entry point: a genuine two-finger tap (not a tap count, not a long-press — both
    // already mean something else in the app) on this row opens Developer Options. Three
    // fingers was the original idea but many OEM skins (Samsung OneUI among them) intercept
    // three-finger touches system-wide for screenshot capture before the app ever sees them.
    // Consuming the gesture once triggered stops the normal single-tap click from also firing
    // and opening AboutActivity.
    private var developerGestureTriggered = false

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

    override fun onBindView(view: View) {
        super.onBindView(view)
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    developerGestureTriggered = false
                    false
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2 && !developerGestureTriggered) {
                        developerGestureTriggered = true
                        view.context.startActivity(
                            Intent(view.context, DeveloperOptionsActivity::class.java)
                        )
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    developerGestureTriggered
                else -> false
            }
        }
    }

    override fun onClick() {
        super.onClick()
        context.startActivity(Intent(context, AboutActivity::class.java))
    }
}
