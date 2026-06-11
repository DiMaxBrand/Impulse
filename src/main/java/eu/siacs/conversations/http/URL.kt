package eu.siacs.conversations.http

import okhttp3.HttpUrl
import java.net.URI
import java.net.URISyntaxException

object URL {
    @JvmField
    val WELL_KNOWN_SCHEMES = listOf("http", "https", AesGcmURL.PROTOCOL_NAME)

    @JvmStatic
    fun tryParse(url: String): String? {
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return null
        }
        return if (WELL_KNOWN_SCHEMES.contains(uri.scheme)) uri.toString() else null
    }

    @JvmStatic
    fun stripFragment(url: HttpUrl): HttpUrl = url.newBuilder().fragment(null).build()
}
