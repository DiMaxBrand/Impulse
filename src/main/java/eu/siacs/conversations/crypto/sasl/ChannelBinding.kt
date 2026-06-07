package eu.siacs.conversations.crypto.sasl

import android.util.Log
import com.google.common.base.CaseFormat
import com.google.common.base.Strings
import com.google.common.collect.BiMap
import com.google.common.collect.ImmutableBiMap
import eu.siacs.conversations.Config
import eu.siacs.conversations.utils.SSLSockets
import im.conversations.android.xmpp.model.cb.SaslChannelBinding
import java.util.Arrays

enum class ChannelBinding {
    NONE,
    TLS_EXPORTER,
    TLS_SERVER_END_POINT,
    TLS_UNIQUE;

    companion object {
        @JvmField
        val SHORT_NAMES: BiMap<ChannelBinding, String>

        init {
            val builder = ImmutableBiMap.builder<ChannelBinding, String>()
            for (cb in values()) {
                builder.put(cb, shortName(cb))
            }
            SHORT_NAMES = builder.build()
        }

        @JvmStatic
        fun of(channelBinding: SaslChannelBinding?): Collection<ChannelBinding> {
            if (channelBinding == null) {
                return emptyList()
            }
            return channelBinding.channelBindings.mapNotNull { cb -> of(cb.type) }
        }

        private fun of(type: String?): ChannelBinding? {
            if (type == null) {
                return null
            }
            return try {
                valueOf(
                    CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.UPPER_UNDERSCORE).convert(type)!!
                )
            } catch (e: IllegalArgumentException) {
                Log.d(Config.LOGTAG, "$type is not a known channel binding")
                null
            }
        }

        @JvmStatic
        fun get(name: String?): ChannelBinding {
            if (Strings.isNullOrEmpty(name)) {
                return NONE
            }
            return try {
                valueOf(name!!)
            } catch (e: IllegalArgumentException) {
                NONE
            }
        }

        @JvmStatic
        fun best(bindings: Collection<ChannelBinding>, sslVersion: SSLSockets.Version): ChannelBinding {
            if (sslVersion == SSLSockets.Version.NONE) {
                return NONE
            }
            return if (bindings.contains(TLS_EXPORTER) && sslVersion == SSLSockets.Version.TLS_1_3) {
                TLS_EXPORTER
            } else if (bindings.contains(TLS_UNIQUE)
                && listOf(
                    SSLSockets.Version.TLS_1_0,
                    SSLSockets.Version.TLS_1_1,
                    SSLSockets.Version.TLS_1_2
                ).contains(sslVersion)
            ) {
                TLS_UNIQUE
            } else if (bindings.contains(TLS_SERVER_END_POINT)) {
                TLS_SERVER_END_POINT
            } else if (bindings.isEmpty()) {
                Log.w(Config.LOGTAG, "no supported bindings. making a guess")
                fallback(sslVersion)
            } else {
                NONE
            }
        }

        private fun fallback(version: SSLSockets.Version): ChannelBinding {
            return if (version == SSLSockets.Version.TLS_1_3) {
                TLS_EXPORTER
            } else if (listOf(
                    SSLSockets.Version.TLS_1_0,
                    SSLSockets.Version.TLS_1_1,
                    SSLSockets.Version.TLS_1_2
                ).contains(version)
            ) {
                TLS_UNIQUE
            } else {
                NONE
            }
        }

        @JvmStatic
        fun isAvailable(channelBinding: ChannelBinding, sslVersion: SSLSockets.Version): Boolean {
            return best(listOf(channelBinding), sslVersion) == channelBinding
        }

        private fun shortName(channelBinding: ChannelBinding): String {
            return when (channelBinding) {
                TLS_UNIQUE -> "UNIQ"
                TLS_EXPORTER -> "EXPR"
                TLS_SERVER_END_POINT -> "ENDP"
                NONE -> "NONE"
            }
        }

        @JvmStatic
        fun priority(channelBinding: ChannelBinding): Int {
            return if (listOf(TLS_EXPORTER, TLS_UNIQUE).contains(channelBinding)) {
                2
            } else if (channelBinding == TLS_SERVER_END_POINT) {
                1
            } else {
                0
            }
        }
    }
}
