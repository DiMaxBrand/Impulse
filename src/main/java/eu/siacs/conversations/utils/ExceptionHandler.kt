package eu.siacs.conversations.utils

import android.content.Context
import android.os.Build
import androidx.annotation.NonNull
import com.google.common.base.Joiner
import com.google.common.base.Strings
import com.google.common.collect.ImmutableList
import eu.siacs.conversations.BuildConfig
import eu.siacs.conversations.services.NotificationService
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.Thread.UncaughtExceptionHandler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExceptionHandler internal constructor(private val context: Context) : UncaughtExceptionHandler {

    private val defaultHandler: UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(@NonNull thread: Thread, throwable: Throwable) {
        NotificationService.cancelIncomingCallNotification(context)
        val report = try {
            buildReport(throwable)
        } catch (e: IOException) {
            return
        }
        ExceptionHelper.writeToStacktraceFile(context, report)
        this.defaultHandler?.uncaughtException(thread, throwable)
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ENGLISH)

        /**
         * Shared by the fatal (uncaughtException, above) and non-fatal
         * (ExceptionHelper.reportCaughtException) paths, so a manually-reported caught exception
         * carries the same version/device/timestamp header the support inbox already expects from
         * a real crash report.
         */
        fun buildReport(throwable: Throwable): String {
            val stringWriter = StringWriter()
            val printWriter = PrintWriter(stringWriter)
            throwable.printStackTrace(printWriter)
            printWriter.close()
            stringWriter.close()
            val report = ImmutableList.of(
                String.format("Version: %s %s", BuildConfig.APP_NAME, BuildConfig.VERSION_NAME),
                String.format("Manufacturer: %s", Strings.nullToEmpty(Build.MANUFACTURER)),
                String.format("Device: %s", Strings.nullToEmpty(Build.DEVICE)),
                String.format("Timestamp: %s", DATE_FORMAT.format(Date())),
                stringWriter.toString()
            )
            return Joiner.on("\n").join(report)
        }
    }
}
