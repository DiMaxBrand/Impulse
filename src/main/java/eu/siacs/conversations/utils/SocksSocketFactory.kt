package eu.siacs.conversations.utils

import com.google.common.io.ByteStreams
import com.google.common.net.InetAddresses
import eu.siacs.conversations.Config
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer

object SocksSocketFactory {

    private val LOCALHOST = byteArrayOf(127, 0, 0, 1)

    @JvmStatic
    @Throws(IOException::class)
    fun createSocksConnection(socket: Socket, destination: String, port: Int) {
        val proxyIs = socket.getInputStream()
        val proxyOs = socket.getOutputStream()
        proxyOs.write(byteArrayOf(0x05, 0x01, 0x00))
        proxyOs.flush()
        val handshake = ByteArray(2)
        ByteStreams.readFully(proxyIs, handshake)
        if (handshake[0] != 0x05.toByte() || handshake[1] != 0x00.toByte()) {
            throw SocksConnectionException("Socks 5 handshake failed")
        }
        val type: Byte
        val request: ByteBuffer
        if (InetAddresses.isInetAddress(destination)) {
            val ip = InetAddresses.forString(destination)
            val dest = ip.address
            request = ByteBuffer.allocate(6 + dest.size)
            type = when (ip) {
                is Inet4Address -> 0x01
                is Inet6Address -> 0x04
                else -> throw IOException("IP address is of unknown subtype")
            }
            request.put(byteArrayOf(0x05, 0x01, 0x00, type))
            request.put(dest)
        } else {
            val dest = destination.toByteArray()
            type = 0x03
            request = ByteBuffer.allocate(7 + dest.size)
            request.put(byteArrayOf(0x05, 0x01, 0x00, type))
            request.put(dest.size.toByte())
            request.put(dest)
        }
        request.putShort(port.toShort())
        proxyOs.write(request.array())
        proxyOs.flush()
        val response = ByteArray(4)
        ByteStreams.readFully(proxyIs, response)
        val ver = response[0]
        if (ver != 0x05.toByte()) {
            throw IOException(String.format("Unknown Socks version %02X ", ver))
        }
        val status = response[1]
        val bndAddressType = response[3]
        val bndDestination = readDestination(bndAddressType, proxyIs)
        val bndPort = ByteArray(2)
        if (bndAddressType == 0x03.toByte()) {
            val receivedDestination = String(bndDestination)
            if (!receivedDestination.equals(destination, ignoreCase = true)) {
                throw IOException(
                    String.format(
                        "Destination mismatch. Received %s Expected %s",
                        receivedDestination,
                        destination
                    )
                )
            }
        }
        ByteStreams.readFully(proxyIs, bndPort)
        if (status != 0x00.toByte()) {
            if (status == 0x04.toByte()) {
                throw HostNotFoundException("Host unreachable")
            }
            if (status == 0x05.toByte()) {
                throw HostNotFoundException("Connection refused")
            }
            throw IOException(String.format("Unknown status code %02X ", status))
        }
    }

    @Throws(IOException::class)
    private fun readDestination(type: Byte, inputStream: InputStream): ByteArray {
        val bndDestination: ByteArray = when (type) {
            0x01.toByte() -> ByteArray(4)
            0x03.toByte() -> {
                val length = inputStream.read()
                ByteArray(length)
            }
            0x04.toByte() -> ByteArray(16)
            else -> throw IOException(String.format("Unknown Socks address type %02X ", type))
        }
        ByteStreams.readFully(inputStream, bndDestination)
        return bndDestination
    }

    @JvmStatic
    fun contains(needle: Byte, haystack: ByteArray): Boolean {
        for (hay in haystack) {
            if (hay == needle) return true
        }
        return false
    }

    @Throws(IOException::class)
    private fun createSocket(address: InetSocketAddress, destination: String, port: Int): Socket {
        val socket = Socket()
        try {
            socket.connect(address, Config.CONNECT_TIMEOUT * 1000)
        } catch (e: IOException) {
            throw SocksProxyNotFoundException()
        }
        createSocksConnection(socket, destination, port)
        return socket
    }

    @JvmStatic
    @Throws(IOException::class)
    fun createSocketOverTor(destination: String, port: Int): Socket {
        return createSocket(
            InetSocketAddress(InetAddress.getByAddress(LOCALHOST), 9050),
            destination,
            port
        )
    }

    internal open class SocksConnectionException(message: String) : IOException(message)

    class SocksProxyNotFoundException : IOException()

    internal class HostNotFoundException(message: String) : SocksConnectionException(message)
}
