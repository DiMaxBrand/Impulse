package eu.siacs.conversations.utils

import com.google.common.net.InetAddresses
import de.gultsch.common.Patterns

object IP {

    @JvmStatic
    fun matches(server: String?): Boolean {
        return server != null
            && (Patterns.IPV4.matcher(server).matches()
                || Patterns.IPV6.matcher(server).matches()
                || Patterns.IPV6_6HEX4DEC.matcher(server).matches()
                || Patterns.IPV6_HEX4_DECOMPRESSED.matcher(server).matches()
                || Patterns.IPV6_HEX_COMPRESSED.matcher(server).matches())
    }

    @JvmStatic
    fun wrapIPv6(host: String): String {
        return if (InetAddresses.isInetAddress(host)) {
            val inetAddress = try {
                InetAddresses.forString(host)
            } catch (e: IllegalArgumentException) {
                return host
            }
            InetAddresses.toUriString(inetAddress)
        } else {
            host
        }
    }

    @JvmStatic
    fun unwrapIPv6(host: String): String {
        if (host.length > 2 && host[0] == '[' && host[host.length - 1] == ']') {
            val ip = host.substring(1, host.length - 1)
            if (InetAddresses.isInetAddress(ip)) {
                return ip
            }
        }
        return host
    }
}
