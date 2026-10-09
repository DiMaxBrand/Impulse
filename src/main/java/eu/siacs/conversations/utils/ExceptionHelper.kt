package eu.siacs.conversations.utils

import android.content.Context
import android.util.Log
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.base.Charsets
import com.google.common.io.Files
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.ui.XmppActivity
import eu.siacs.conversations.update.BugReportRegistry
import eu.siacs.conversations.update.UpdatePreferences
import java.io.File
import java.io.IOException

object ExceptionHelper {

    private const val FILENAME = "stacktrace.txt"

    @JvmStatic
    fun init(context: Context) {
        if (Thread.getDefaultUncaughtExceptionHandler() is ExceptionHandler) {
            return
        }
        Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler(context))
    }

    @JvmStatic
    fun checkForCrash(activity: XmppActivity?): Boolean {
        val service = activity?.xmppConnectionService ?: return false
        val appSettings = AppSettings(activity)
        if (!appSettings.isSendCrashReports) {
            return false
        }
        val account = AccountUtils.getFirstEnabled(service) ?: return false
        val file = File(activity.cacheDir, FILENAME)
        if (!file.exists()) {
            return false
        }
        val report: String = try {
            Files.asCharSource(file, Charsets.UTF_8).read()
        } catch (e: IOException) {
            return false
        }
        if (file.delete()) {
            Log.d(Config.LOGTAG, "deleted crash report file")
        }
        val builder = MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_Impulse_FilledPositiveButton)
        builder.setTitle(
            activity.getString(R.string.crash_report_title, activity.getString(R.string.app_name))
        )
        builder.setMessage(
            activity.getString(R.string.crash_report_message, activity.getString(R.string.app_name)) +
                "\n\n" + activity.getString(R.string.bug_report_fixed_notice)
        )
        builder.setPositiveButton(activity.getText(R.string.send_now)) { _, _ ->
            Log.d(
                Config.LOGTAG,
                "using account=" + account.jid.asBareJid() + " to send in stack trace"
            )
            val conversation: Conversation =
                service.findOrCreateConversation(account, Config.BUG_REPORTS, false, true)
            val message = Message(conversation, trackedReport(activity, report), Message.ENCRYPTION_NONE)
            service.sendMessage(message)
        }
        // Plain dismiss, not "never again" — a one-tap permanent opt-out is too easy to hit by
        // accident for what it costs (every future report, silently). Turning reports off for
        // good is still possible, just moved to a deliberate Settings toggle instead of a dialog
        // button — appSettings.isSendCrashReports is the same preference either way.
        builder.setNegativeButton(activity.getText(R.string.not_now), null)
        builder.create().show()
        return true
    }

    /**
     * For exceptions the app already caught and recovered from — a failed attachment, a background
     * task that threw — where nothing actually crashed, so there's no restart for checkForCrash()
     * to catch it on next launch. Offers to send it right away instead, through the same
     * isSendCrashReports preference and support-chat destination as a real crash, just with
     * distinct, non-alarming copy since the app is still running fine. Returns false (does nothing)
     * if reporting is off, there's no account to send from, or [activity] isn't available.
     */
    @JvmStatic
    fun reportCaughtException(activity: XmppActivity?, throwable: Throwable): Boolean {
        val service = activity?.xmppConnectionService ?: return false
        val appSettings = AppSettings(activity)
        if (!appSettings.isSendCrashReports || Config.BUG_REPORTS == null) {
            return false
        }
        val account = AccountUtils.getFirstEnabled(service) ?: return false
        val report = ExceptionHandler.buildReport(throwable)
        val builder = MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_Impulse_FilledPositiveButton)
        builder.setTitle(
            activity.getString(R.string.error_report_title, activity.getString(R.string.app_name))
        )
        builder.setMessage(
            activity.getString(R.string.error_report_message, activity.getString(R.string.app_name)) +
                "\n\n" + activity.getString(R.string.bug_report_fixed_notice)
        )
        builder.setPositiveButton(activity.getText(R.string.send_now)) { _, _ ->
            val conversation: Conversation =
                service.findOrCreateConversation(account, Config.BUG_REPORTS, false, true)
            val message = Message(conversation, trackedReport(activity, report), Message.ENCRYPTION_NONE)
            service.sendMessage(message)
        }
        // Plain dismiss, not "never again" — a one-tap permanent opt-out is too easy to hit by
        // accident for what it costs (every future report, silently). Turning reports off for
        // good is still possible, just moved to a deliberate Settings toggle instead of a dialog
        // button — appSettings.isSendCrashReports is the same preference either way.
        builder.setNegativeButton(activity.getText(R.string.not_now), null)
        builder.create().show()
        return true
    }

    /**
     * Gives [report] a tracking ID (BUG-XXXX), remembers it together with the reporter's update
     * channel, and returns the text to send: a header for whoever reads it (the developer) plus
     * the report. When a later release's notes mention the ID, the reporter is told it was fixed.
     */
    @JvmStatic
    fun trackedReport(context: Context, report: String): String =
        trackedReportWithId(context, report).second

    @JvmStatic
    fun trackedReportWithId(context: Context, report: String): Pair<String, String> {
        val channel = UpdatePreferences(context).selectedChannel.id
        val id = BugReportRegistry(context).register(channel)
        val header =
            "Report ID: $id\n" +
                "Update channel: $channel\n" +
                "(For whoever reads this: put \"$id\" in the release description of the release " +
                "that fixes it, and the reporter is notified when it ships.)\n\n"
        return id to header + report
    }

    @JvmStatic
    internal fun writeToStacktraceFile(context: Context, msg: String) {
        try {
            Files.asCharSink(File(context.cacheDir, FILENAME), Charsets.UTF_8).write(msg)
        } catch (e: IOException) {
            Log.w(Config.LOGTAG, "could not write stack trace to file", e)
        }
    }
}
