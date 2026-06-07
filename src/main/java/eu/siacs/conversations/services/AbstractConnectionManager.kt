package eu.siacs.conversations.services

import android.util.Log
import androidx.annotation.Nullable
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Transferable
import okio.source
import eu.siacs.conversations.http.HttpUploadConnection
import im.conversations.android.model.TransportSecurity
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.Source
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.io.CipherInputStream
import org.bouncycastle.crypto.modes.AEADBlockCipher
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter

open class AbstractConnectionManager(service: XmppConnectionService) {

    @JvmField protected var mXmppConnectionService: XmppConnectionService = service

    fun getXmppConnectionService(): XmppConnectionService = this.mXmppConnectionService

    interface ProgressListener {
        fun onProgress(progress: Long)
    }

    class Extension private constructor(@JvmField val main: String?, @JvmField val secondary: String?) {

        fun getExtension(): String? {
            return if (Transferable.VALID_CRYPTO_EXTENSIONS.contains(main)) {
                secondary
            } else {
                main
            }
        }

        companion object {
            @JvmStatic
            fun of(path: String): Extension {
                // TODO accept List<String> pathSegments
                val pos = path.lastIndexOf('/')
                val filename = path.substring(pos + 1).lowercase()
                val parts = filename.split(".").toTypedArray()
                val main = if (parts.size >= 2) parts[parts.size - 1] else null
                val secondary = if (parts.size >= 3) parts[parts.size - 2] else null
                return Extension(main, secondary)
            }
        }
    }

    companion object {
        // For progress tracking see:
        // https://github.com/square/okhttp/blob/master/samples/guide/src/main/java/okhttp3/recipes/Progress.java

        @JvmStatic
        fun requestBody(
            upload: HttpUploadConnection.Upload,
            progressListener: ProgressListener
        ): RequestBody {
            return object : RequestBody() {
                override fun contentLength(): Long = upload.totalSize()

                override fun contentType(): MediaType? = upload.mime().toMediaTypeOrNull()

                @Throws(IOException::class)
                override fun writeTo(sink: BufferedSink) {
                    var transmitted: Long = 0
                    openFileInputStream(upload.file(), upload.transportSecurity()).source().use { source ->
                        var read: Long
                        while (source.read(sink.buffer(), 8196).also { read = it } != -1L) {
                            transmitted += read
                            sink.flush()
                            progressListener.onProgress(transmitted)
                        }
                    }
                }
            }
        }

        @JvmStatic
        @Throws(FileNotFoundException::class)
        private fun openFileInputStream(
            file: File,
            @Nullable transportSecurity: TransportSecurity?
        ): InputStream {
            val fileInputStream = FileInputStream(file)
            return if (transportSecurity == null) {
                fileInputStream
            } else {
                val cipher: AEADBlockCipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
                cipher.init(
                    true,
                    AEADParameters(
                        KeyParameter(transportSecurity.key()),
                        128,
                        transportSecurity.iv()
                    )
                )
                Log.d(Config.LOGTAG, "setting up CipherInputStream")
                CipherInputStream(fileInputStream, cipher)
            }
        }
    }
}
