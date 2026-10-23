package eu.siacs.conversations.update

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A short log of the support commands this phone received and what it did with each, kept so that
 * "support sent something and nothing happened" can be answered from the phone itself: it goes along
 * with the device info that Help copies. Commands are hidden, so without this there is nothing to see.
 */
object SupportDiagnostics {
    private const val PREFS = "support_diagnostics"
    private const val KEY = "log"
    private const val MAX_LINES = 12

    @JvmStatic
    @Synchronized
    fun record(context: Context, line: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
        val lines = (prefs.getString(KEY, "") ?: "").lines().filter { it.isNotBlank() }
        val next = (lines + "$stamp $line").takeLast(MAX_LINES)
        prefs.edit().putString(KEY, next.joinToString("\n")).apply()
    }

    @JvmStatic
    fun report(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)?.takeIf { it.isNotBlank() }
}
