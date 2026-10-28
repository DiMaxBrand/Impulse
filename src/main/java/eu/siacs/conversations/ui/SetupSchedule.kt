package eu.siacs.conversations.ui

import android.content.Context
import androidx.preference.PreferenceManager
import eu.siacs.conversations.AppSettings

/**
 * When the first-run prompts may appear, so a new person is not met by all of them at once.
 *  - the notification setup screen: at the first chance, and again on the next day (and each day after)
 *    until it has been done;
 *  - the call-permission reminder: at least a day after the notification setup screen was shown or done
 *    (the setup screen already asks for the same things);
 *  - the birthday question: from the third day on;
 *  - and only one of them per launch of the app.
 */
object SetupSchedule {
    private const val DAY = 24L * 60 * 60 * 1000
    const val KEY_FIRST_LAUNCH = "setup_first_launch_at"
    const val KEY_NOTIFICATION_SHOWN = "setup_notification_last_shown"
    const val KEY_NOTIFICATION_DONE = "setup_notification_done_at"

    @Volatile private var slotTaken = false

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    /** Remembers the first time the chat list was reached (for the "third day" of the birthday question). */
    @JvmStatic
    fun noteLaunch(context: Context) {
        val p = prefs(context)
        if (!p.contains(KEY_FIRST_LAUNCH)) p.edit().putLong(KEY_FIRST_LAUNCH, System.currentTimeMillis()).apply()
    }

    /** The one prompt this launch may show: true for the first caller only. */
    @JvmStatic
    fun claim(): Boolean {
        if (slotTaken) return false
        slotTaken = true
        return true
    }

    @JvmStatic
    fun notificationSetupDue(context: Context): Boolean {
        val shown = prefs(context).getLong(KEY_NOTIFICATION_SHOWN, 0L)
        return shown == 0L || System.currentTimeMillis() - shown >= DAY
    }

    @JvmStatic
    fun noteNotificationSetupShown(context: Context) {
        prefs(context).edit().putLong(KEY_NOTIFICATION_SHOWN, System.currentTimeMillis()).apply()
    }

    @JvmStatic
    fun callReminderDue(context: Context): Boolean {
        val p = prefs(context)
        val anchor = maxOf(p.getLong(KEY_NOTIFICATION_SHOWN, 0L), p.getLong(KEY_NOTIFICATION_DONE, 0L))
        // No record at all: the setup was finished before this schedule existed (so it is allowed), or it
        // has not been shown yet (so it is not).
        if (anchor == 0L) return AppSettings(context).isNotificationSetupDone
        return System.currentTimeMillis() - anchor >= DAY
    }

    @JvmStatic
    fun birthdayPromptDue(context: Context): Boolean {
        val first = prefs(context).getLong(KEY_FIRST_LAUNCH, 0L)
        return first != 0L && System.currentTimeMillis() - first >= 3 * DAY
    }
}
