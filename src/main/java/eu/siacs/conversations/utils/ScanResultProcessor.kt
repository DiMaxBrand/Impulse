package eu.siacs.conversations.utils

import android.content.Context
import android.util.Log
import com.google.common.base.Objects
import com.google.common.base.Splitter
import com.google.common.base.Strings
import com.google.common.collect.Iterables
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import de.gultsch.common.MiniUri
import de.gultsch.common.Patterns
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.Config
import eu.siacs.conversations.http.HttpConnectionManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.UnknownHostException
import java.time.Duration
import java.util.regex.Pattern

class ScanResultProcessor(context: Context) {

    private val context: Context = context.applicationContext

    companion object {
        private val LINK_HEADER_PATTERN: Pattern = Pattern.compile("<(.*?)>")
        private val V_CARD_XMPP_PATTERN: Pattern = Pattern.compile("\nIMPP([^:]*):(xmpp:.+)\n")
        private const val VCARD_BEGIN = "BEGIN:VCARD"
        private const val VCARD_END = "END:VCARD"
    }

    fun process(input: String?): ListenableFuture<MiniUri> {
        if (Strings.isNullOrEmpty(input)) {
            return Futures.immediateFailedFuture(IllegalArgumentException("QR code was empty"))
        }
        val lines = Splitter.on('\n').trimResults().omitEmptyStrings().splitToList(input!!)
        if (lines.size == 1) {
            val line = Iterables.getOnlyElement(lines)
            if (Patterns.URI_GENERIC.matcher(line).matches()) {
                return try {
                    process(MiniUri.asMiniUri(line))
                } catch (e: IllegalArgumentException) {
                    Futures.immediateFailedFuture(e)
                }
            }
            return Futures.immediateFailedFuture(IllegalArgumentException("QR code is not a URI"))
        } else if (Objects.equal(VCARD_BEGIN, Iterables.getFirst(lines, null))
            && Objects.equal(VCARD_END, Iterables.getLast(lines))
        ) {
            val matcher = V_CARD_XMPP_PATTERN.matcher(input)
            if (matcher.find()) {
                return try {
                    process(MiniUri.asMiniUri(matcher.group(2)))
                } catch (e: IllegalArgumentException) {
                    Futures.immediateFailedFuture(e)
                }
            } else {
                return Futures.immediateFailedFuture(
                    IllegalArgumentException("VCard contains no XMPP uri")
                )
            }
        }
        return Futures.immediateFailedFuture(IllegalArgumentException("Unrecognized content"))
    }

    private fun process(uri: MiniUri): ListenableFuture<MiniUri> {
        if (uri is MiniUri.Xmpp) {
            if (uri.isAddress) {
                return Futures.immediateFuture(uri)
            }
            return Futures.immediateFailedFuture(
                IllegalArgumentException("xmpp uri has no address")
            )
        } else if (uri is MiniUri.Http) {
            val transformed = uri.transform()
            if (transformed is MiniUri.Xmpp) {
                if (transformed.isAddress) {
                    return Futures.immediateFuture(transformed)
                }
                return Futures.immediateFailedFuture(
                    IllegalArgumentException("xmpp uri has no address")
                )
            }
            if (AppSettings(context).isUseTor) {
                return Futures.immediateFuture(uri)
            }
            if (eligibleForLinkHeaderDiscovery(uri.asHttpUrl())) {
                val linkHeaderFuture = fetchLinkHeader(uri.asHttpUrl())
                return Futures.catching(
                    linkHeaderFuture,
                    Exception::class.java,
                    { ex ->
                        Log.d(Config.LOGTAG, "error looking up link header", ex)
                        uri
                    },
                    MoreExecutors.directExecutor()
                )
            } else {
                return Futures.immediateFuture(uri)
            }
        } else {
            return Futures.immediateFailedFuture(
                IllegalArgumentException("Unsupported URI scheme " + uri.scheme)
            )
        }
    }

    private fun eligibleForLinkHeaderDiscovery(url: HttpUrl): Boolean {
        // we want to rule out IP addresses and local domains from the start; we do a DNS double
        // check later
        // some invite pages encode information in the fragment; we can't use those since it won't
        // have any Link headers
        return url.isHttps && url.topPrivateDomain() != null && url.fragment == null
    }

    private fun fetchLinkHeader(url: HttpUrl): ListenableFuture<MiniUri> {
        val future: SettableFuture<MiniUri> = SettableFuture.create()
        Log.d(Config.LOGTAG, "checking for link header on $url")
        val okHttp = HttpConnectionManager.okHttpClient(context)
            .newBuilder()
            .dns { hostname ->
                val addresses = Dns.SYSTEM.lookup(hostname)
                for (address in addresses) {
                    if (address.isLoopbackAddress
                        || address.isSiteLocalAddress
                        || address.isLinkLocalAddress
                    ) {
                        throw UnknownHostException("Not performing look ups on local addresses")
                    }
                }
                addresses
            }
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(Duration.ofSeconds(3))
            .build()
        val call = okHttp.newCall(Request.Builder().url(url).head().build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                future.setException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val link = response.header("Link")
                    val uri = if (Strings.isNullOrEmpty(link)) null else processLinkHeader(link!!)
                    if (uri != null) {
                        future.set(uri)
                    } else {
                        future.setException(
                            IllegalStateException("No link header found in response")
                        )
                    }
                } else {
                    future.setException(IllegalStateException("HTTP call was unsuccessful"))
                }
            }
        })
        return future
    }

    private fun processLinkHeader(header: String): MiniUri.Xmpp? {
        val matcher = LINK_HEADER_PATTERN.matcher(header)
        if (matcher.find()) {
            val group = matcher.group()
            val link = group.substring(1, group.length - 1)
            try {
                val miniUri = MiniUri.tryParse(link)
                if (miniUri is MiniUri.Xmpp && miniUri.isAddress) {
                    return miniUri
                }
            } catch (e: IllegalArgumentException) {
                Log.d(Config.LOGTAG, "found invalid uri in link header", e)
            }
        }
        return null
    }
}
