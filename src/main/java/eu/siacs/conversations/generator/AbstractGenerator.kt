package eu.siacs.conversations.generator

import eu.siacs.conversations.BuildConfig
import eu.siacs.conversations.services.XmppConnectionService
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

abstract class AbstractGenerator(protected var mXmppConnectionService: XmppConnectionService) {
    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

        @JvmStatic
        fun getTimestamp(time: Long): String {
            DATE_FORMAT.timeZone = TimeZone.getTimeZone("UTC")
            return DATE_FORMAT.format(time)
        }
    }

    internal fun getIdentityVersion(): String = BuildConfig.VERSION_NAME
}
