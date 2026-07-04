package eu.siacs.conversations.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
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

    // Hidden entry point: holding this row for DEVELOPER_HOLD_MS opens Developer Options.
    // Multi-finger taps were tried first but proved unreliable — the very first finger's
    // touchdown already arms the enclosing ListView's own click detection (that's the gray
    // ripple), so by the time a second or third pointer lands it's often too late to override.
    // A custom-duration hold uses the platform's real long-press machinery instead of fighting
    // the list's touch handling, and 3s is deliberately longer than a normal long-press so it
    // doesn't fire by accident. Long-press has no existing meaning on this specific screen —
    // it's only used elsewhere, in the conversation view.
    override fun onBindView(view: View) {
        super.onBindView(view)
        val handler = Handler(Looper.getMainLooper())
        var triggered = false
        val holdRunnable = Runnable {
            triggered = true
            view.context.startActivity(Intent(view.context, DeveloperOptionsActivity::class.java))
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    triggered = false
                    handler.postDelayed(holdRunnable, DEVELOPER_HOLD_MS)
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(holdRunnable)
                    triggered
                }
                else -> false
            }
        }
    }

    override fun onClick() {
        super.onClick()
        context.startActivity(Intent(context, AboutActivity::class.java))
    }

    companion object {
        private const val DEVELOPER_HOLD_MS = 3000L
    }
}
