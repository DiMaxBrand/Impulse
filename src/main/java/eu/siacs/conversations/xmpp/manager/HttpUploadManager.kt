package eu.siacs.conversations.xmpp.manager

import android.util.Base64
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.google.common.base.MoreObjects
import com.google.common.collect.ImmutableMap
import com.google.common.primitives.Longs
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import eu.siacs.conversations.Config
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.ExtensionFactory
import im.conversations.android.xmpp.model.disco.info.InfoQuery
import im.conversations.android.xmpp.model.stanza.Iq
import im.conversations.android.xmpp.model.upload.Request
import im.conversations.android.xmpp.model.upload.purpose.Purpose
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.UUID
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response

class HttpUploadManager(
    private val service: XmppConnectionService,
    connection: XmppConnection
) : AbstractManager(service.applicationContext, connection) {

    fun request(file: File, mime: String, size: Long): ListenableFuture<Slot> {
        return request(file.name, mime, size, null)
    }

    fun request(
        filename: String,
        mime: String,
        size: Long,
        @Nullable purpose: Purpose?
    ): ListenableFuture<Slot> {
        val result =
            getManager(DiscoManager::class.java).findDiscoItemByFeature(Namespace.HTTP_UPLOAD)
                ?: return Futures.immediateFailedFuture(
                    IllegalStateException("No HTTP upload host found")
                )
        return requestHttpUpload(result.key, filename, mime, size, purpose)
    }

    fun upload(file: File, mime: String, purpose: Purpose): ListenableFuture<HttpUrl> {
        val filename = file.name
        val size = file.length()
        val slotFuture = request(filename, mime, size, purpose)
        return Futures.transformAsync(
            slotFuture,
            { slot: Slot -> upload(file, mime, slot) },
            MoreExecutors.directExecutor()
        )
    }

    private fun upload(file: File, mime: String, slot: Slot): ListenableFuture<HttpUrl> {
        val future: SettableFuture<HttpUrl> = SettableFuture.create()
        val client: OkHttpClient =
            service.httpConnectionManager
                .buildHttpClient(slot.put, account, 0, false)
        val body = RequestBody.create(mime.toMediaTypeOrNull(), file)
        val request: okhttp3.Request =
            okhttp3.Request.Builder().url(slot.put).put(body).headers(slot.headers).build()
        client.newCall(request)
            .enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        future.setException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            future.set(slot.get)
                        } else {
                            future.setException(
                                IllegalStateException(
                                    String.format(
                                        "Response code was %s",
                                        response.code
                                    )
                                )
                            )
                        }
                    }
                }
            )
        return future
    }

    private fun requestHttpUpload(
        host: Jid,
        filename: String,
        mime: String,
        size: Long,
        @Nullable purpose: Purpose?
    ): ListenableFuture<Slot> {
        val iq = Iq(Iq.Type.GET)
        iq.setTo(host)
        val request = iq.addExtension(Request())
        request.setFilename(convertFilename(filename))
        request.setSize(size)
        request.setContentType(mime)
        if (purpose != null) {
            request.addExtension(purpose)
        }
        Log.d(Config.LOGTAG, "-->" + iq)
        val iqFuture = this.connection.sendIqPacket(iq)
        return Futures.transform(
            iqFuture,
            { response: Iq ->
                val slot =
                    response.getExtension(im.conversations.android.xmpp.model.upload.Slot::class.java)
                        ?: throw IllegalStateException("Slot not found in IQ response")
                val getUrl = slot.getUrl
                val put = slot.put
                if (getUrl == null || put == null) {
                    throw IllegalStateException("Missing get or put in slot response")
                }
                val putUrl = put.url
                    ?: throw IllegalStateException("Missing put url")
                val contentType = if (mime == null) "application/octet-stream" else mime
                val headers =
                    ImmutableMap.Builder<String, String>()
                        .putAll(put.headersAllowList)
                        .put("Content-Type", contentType)
                        .buildKeepingLast()
                Slot(putUrl, getUrl, headers)
            },
            MoreExecutors.directExecutor()
        )
    }

    fun getService(): Service? {
        if (Config.ENABLE_HTTP_UPLOAD) {
            val entry =
                getManager(DiscoManager::class.java).findDiscoItemByFeature(Namespace.HTTP_UPLOAD)
            return if (entry == null) null else Service(entry)
        }
        return null
    }

    fun isAvailableForSize(size: Long): Boolean {
        val result = getManager(HttpUploadManager::class.java).getService() ?: return false
        val maxSize: Long? = result.maxFileSize
        if (maxSize == null) {
            return true
        }
        return if (size <= maxSize) {
            true
        } else {
            Log.d(
                Config.LOGTAG,
                account.jid.asBareJid().toString()
                        + ": http upload is not available for files with size "
                        + size
                        + " (max is "
                        + maxSize
                        + ")"
            )
            false
        }
    }

    class Service(private val addressInfoQuery: Map.Entry<Jid, InfoQuery>) {

        fun getAddress(): Jid = this.addressInfoQuery.key

        fun getInfoQuery(): InfoQuery = this.addressInfoQuery.value

        fun supportsPurpose(purpose: Class<out Purpose>): Boolean {
            val id = ExtensionFactory.id(purpose)
                ?: throw IllegalStateException("Purpose has not been annotated as @XmlElement")
            val feature = String.format("%s#%s", id.namespace(), id.name())
            return getInfoQuery().hasFeature(feature)
        }

        val maxFileSize: Long?
            get() {
                val value =
                    getInfoQuery()
                        .getServiceDiscoveryExtension(Namespace.HTTP_UPLOAD, "max-file-size")
                return if (value == null) null else Longs.tryParse(value)
            }

    }

    class Slot @JvmOverloads constructor(
        @JvmField val put: HttpUrl,
        @JvmField val get: HttpUrl,
        @JvmField val headers: Headers
    ) {
        constructor(put: HttpUrl, get: HttpUrl, headers: kotlin.collections.Map<String, String>) :
                this(put, get, headers.toHeaders())

        override fun toString(): String {
            return MoreObjects.toStringHelper(this)
                .add("put", put)
                .add("get", get)
                .add("headers", headers)
                .toString()
        }
    }

    companion object {
        @JvmStatic
        private fun convertFilename(name: String): String {
            val pos = name.indexOf('.')
            if (pos < 0) {
                return name
            }
            return try {
                val uuid = UUID.fromString(name.substring(0, pos))
                val bb = ByteBuffer.wrap(ByteArray(16))
                bb.putLong(uuid.mostSignificantBits)
                bb.putLong(uuid.leastSignificantBits)
                Base64.encodeToString(
                    bb.array(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                ) + name.substring(pos)
            } catch (e: Exception) {
                name
            }
        }
    }
}
