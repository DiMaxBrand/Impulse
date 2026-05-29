package eu.siacs.conversations.xmpp.jingle

import com.google.common.collect.ImmutableList
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.UnknownHostException

object DirectConnectionUtils {

    @JvmStatic
    fun getLocalAddresses(): List<InetAddress> {
        val inetAddresses = ImmutableList.builder<InetAddress>()
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()
        } catch (e: SocketException) {
            return inetAddresses.build()
        }
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val inetAddressEnumeration = networkInterface.inetAddresses
            while (inetAddressEnumeration.hasMoreElements()) {
                val inetAddress = inetAddressEnumeration.nextElement()
                if (inetAddress.isLoopbackAddress || inetAddress.isLinkLocalAddress) {
                    continue
                }
                if (inetAddress is Inet6Address) {
                    // let's get rid of scope
                    try {
                        inetAddresses.add(Inet6Address.getByAddress(inetAddress.address))
                    } catch (e: UnknownHostException) {
                        // ignored
                    }
                } else {
                    inetAddresses.add(inetAddress)
                }
            }
        }
        return inetAddresses.build()
    }
}
