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

    /**
     * Some servers (e.g. share.on-chat.ru) append a `|<filesize>` hint after the
     * IV+KEY hex in the fragment. Strip it before matching/decoding the key.
     */
    @JvmStatic
    fun keyPart(ref: String): String = ref.substringBefore('|')

    @JvmStatic
    fun isValidKeyFragment(ref: String): Boolean = IV_KEY.matcher(keyPart(ref)).matches()

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
