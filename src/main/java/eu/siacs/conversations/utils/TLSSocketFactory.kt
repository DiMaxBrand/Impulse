package eu.siacs.conversations.utils

import eu.siacs.conversations.services.AbstractQuickConversationsService
import android.content.Context
import eu.siacs.conversations.AppSettings
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import javax.net.ssl.SSLProtocolException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

class TLSSocketFactory @Throws(KeyManagementException::class, NoSuchAlgorithmException::class)
constructor(trustManager: Array<X509TrustManager>, context: Context) : SSLSocketFactory() {

    private val context: Context = context.applicationContext
    private val internalSSLSocketFactory: SSLSocketFactory

    init {
        val sslContext = SSLSockets.getSSLContext()
        sslContext.init(null, trustManager, Random.SECURE_RANDOM)
        this.internalSSLSocketFactory = sslContext.socketFactory
    }

    override fun getDefaultCipherSuites(): Array<String> =
        internalSSLSocketFactory.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> =
        internalSSLSocketFactory.supportedCipherSuites

    @Throws(IOException::class)
    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        enableTLSOnSocket(internalSSLSocketFactory.createSocket(s, host, port, autoClose), context)

    @Throws(IOException::class)
    override fun createSocket(host: String, port: Int): Socket =
        enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port), context)

    @Throws(IOException::class)
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        enableTLSOnSocket(
            internalSSLSocketFactory.createSocket(host, port, localHost, localPort), context
        )

    @Throws(IOException::class)
    override fun createSocket(host: InetAddress, port: Int): Socket =
        enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port), context)

    @Throws(IOException::class)
    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        enableTLSOnSocket(
            internalSSLSocketFactory.createSocket(address, port, localAddress, localPort), context
        )

    companion object {
        @Throws(SSLProtocolException::class)
        private fun enableTLSOnSocket(socket: Socket, context: Context): Socket {
            if (socket is SSLSocket) {
                // in Quicksy the setting for requiring TLSv1.3 is hidden; we always require it
                try {
                    SSLSockets.setSecurity(
                        socket,
                        AbstractQuickConversationsService.isQuicksy() || AppSettings(context).isRequireTlsV13()
                    )
                } catch (e: IllegalArgumentException) {
                    throw SSLProtocolException("Could not set security requirements on socket")
                }
            }
            return socket
        }
    }
}
