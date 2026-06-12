package eu.siacs.conversations.utils

import android.content.Context
import android.util.Log
import com.google.common.base.Charsets
import com.google.common.io.Files
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.ui.XmppActivity
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
        if (!appSettings.isSendCrashReports || Config.BUG_REPORTS == null) {
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
        Log.d(Config.LOGTAG, "auto-sending crash report via account=${account.jid.asBareJid()}")
        val conversation: Conversation =
            service.findOrCreateConversation(account, Config.BUG_REPORTS, false, true)
        conversation.nextEncryption = Message.ENCRYPTION_NONE
        val body = "Это автоматическое сообщение отправлено в связи с аварийным завершением работы Impulse. Оно поможет нам исправить ошибку.\n\n$report"
        val message = Message(conversation, body, Message.ENCRYPTION_NONE)
        service.sendMessage(message)
        return true
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
