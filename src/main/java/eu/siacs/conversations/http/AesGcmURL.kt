package eu.siacs.conversations.http

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.regex.Pattern

object AesGcmURL {

    /**
     * This matches a 48 or 44 byte IV + KEY hex combo, like used in http/aesgcm upload anchors
     */
    @JvmField
    val IV_KEY: Pattern = Pattern.compile("([A-Fa-f0-9]{2}){48}|([A-Fa-f0-9]{2}){44}")

    const val PROTOCOL_NAME = "aesgcm"

    @JvmStatic
    fun toAesGcmUrl(url: HttpUrl): String {
        return if (url.isHttps) {
            PROTOCOL_NAME + url.toString().substring(5)
        } else {
            url.toString()
        }
    }

    @JvmStatic
    fun of(url: String): HttpUrl {
        val end = url.indexOf("://")
        if (end < 0) {
            throw IllegalArgumentException("Scheme not found")
        }
        val protocol = url.substring(0, end)
        return if (PROTOCOL_NAME == protocol) {
            ("https" + url.substring(PROTOCOL_NAME.length)).toHttpUrl()
        } else {
            url.toHttpUrl()
        }
    }
}
