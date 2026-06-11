package eu.siacs.conversations.utils

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.common.base.Strings
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Account
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.NoSuchAlgorithmException
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import org.conscrypt.Conscrypt

object SSLSockets {

    @JvmStatic
    fun setSecurity(sslSocket: SSLSocket, requireTlsV13: Boolean) {
        if (requireTlsV13) {
            sslSocket.enabledProtocols = arrayOf("TLSv1.3")
        } else {
            val available = ImmutableSet.copyOf(sslSocket.supportedProtocols)
            sslSocket.enabledProtocols =
                Sets.intersection(available, ImmutableSet.of("TLSv1.2", "TLSv1.3"))
                    .toTypedArray()
        }
    }

    @JvmStatic
    fun setHostname(socket: SSLSocket, hostname: String) {
        if (Conscrypt.isConscrypt(socket)) {
            Conscrypt.setHostname(socket, hostname)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setHostnameNougat(socket, hostname)
        } else {
            setHostnameReflection(socket, hostname)
        }
    }

    private fun setHostnameReflection(socket: SSLSocket, hostname: String) {
        try {
            socket.javaClass.getMethod("setHostname", String::class.java).invoke(socket, hostname)
        } catch (e: Throwable) {
            Log.e(Config.LOGTAG, "unable to set SNI name on socket ($hostname)", e)
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun setHostnameNougat(socket: SSLSocket, hostname: String) {
        val parameters = SSLParameters()
        parameters.serverNames = listOf(SNIHostName(hostname))
        socket.sslParameters = parameters
    }

    private fun setApplicationProtocolReflection(socket: SSLSocket, protocol: String) {
        try {
            val method = socket.javaClass.getMethod("setAlpnProtocols", ByteArray::class.java)
            // the concatenation of 8-bit, length prefixed protocol names, just one in our case...
            // http://tools.ietf.org/html/draft-agl-tls-nextprotoneg-04#page-4
            val protocolUTF8Bytes = protocol.toByteArray(StandardCharsets.UTF_8)
            val lengthPrefixedProtocols = ByteArray(protocolUTF8Bytes.size + 1)
            lengthPrefixedProtocols[0] = protocol.length.toByte() // cannot be over 255 anyhow
            System.arraycopy(protocolUTF8Bytes, 0, lengthPrefixedProtocols, 1, protocolUTF8Bytes.size)
            method.invoke(socket, lengthPrefixedProtocols)
        } catch (e: Throwable) {
            Log.e(Config.LOGTAG, "unable to set ALPN on socket", e)
        }
    }

    @JvmStatic
    fun setApplicationProtocol(socket: SSLSocket, protocol: String) {
        if (Conscrypt.isConscrypt(socket)) {
            Conscrypt.setApplicationProtocols(socket, arrayOf(protocol))
        } else {
            setApplicationProtocolReflection(socket, protocol)
        }
    }

    @JvmStatic
    @Throws(NoSuchAlgorithmException::class)
    fun getSSLContext(): SSLContext {
        return try {
            SSLContext.getInstance("TLSv1.3")
        } catch (e: NoSuchAlgorithmException) {
            Log.d(Config.LOGTAG, "Could not get TLSv1.3 context", e)
            SSLContext.getInstance("TLSv1.2")
        }
    }

    @JvmStatic
    fun log(account: Account, socket: SSLSocket) {
        val session = socket.session
        Log.d(
            Config.LOGTAG,
            "${account.jid.asBareJid()}: protocol=${session.protocol} cipher=${session.cipherSuite}"
        )
    }

    @JvmStatic
    fun version(socket: Socket): Version {
        return if (socket is SSLSocket) {
            if (Conscrypt.isConscrypt(socket)) {
                Version.of(socket.session.protocol)
            } else {
                Version.TLS_UNSUPPORTED_VERSION
            }
        } else {
            Version.NONE
        }
    }

    enum class Version {
        TLS_1_0,
        TLS_1_1,
        TLS_1_2,
        TLS_1_3,
        TLS_UNSUPPORTED_VERSION,
        NONE;

        companion object {
            @JvmStatic
            fun of(protocol: String?): Version {
                return when (Strings.nullToEmpty(protocol)) {
                    "TLSv1" -> TLS_1_0
                    "TLSv1.1" -> TLS_1_1
                    "TLSv1.2" -> TLS_1_2
                    "TLSv1.3" -> TLS_1_3
                    else -> TLS_UNSUPPORTED_VERSION
                }
            }
        }
    }
}
