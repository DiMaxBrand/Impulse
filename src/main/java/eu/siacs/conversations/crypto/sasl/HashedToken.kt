package eu.siacs.conversations.crypto.sasl

import android.util.Log
import com.google.common.base.Strings
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import com.google.common.hash.HashFunction
import com.google.common.primitives.Bytes
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.utils.SSLSockets
import java.nio.charset.StandardCharsets
import java.util.Arrays
import javax.net.ssl.SSLSocket

abstract class HashedToken(account: Account, @get:JvmName("getChannelBindingValue") protected val channelBinding: ChannelBinding) :
    SaslMechanism(account), ChannelBindingMechanism {

    override fun getPriority(): Int {
        throw UnsupportedOperationException()
    }

    override fun getClientFirstMessage(sslSocket: SSLSocket): ByteArray {
        val token = Strings.nullToEmpty(this.account.fastToken)
        val hashing = getHashFunction(token.toByteArray(StandardCharsets.UTF_8))
        val cbData = getChannelBindingData(sslSocket)
        val initiatorHashedToken = hashing.hashBytes(Bytes.concat(INITIATOR, cbData)).asBytes()
        return Bytes.concat(
            account.username.toByteArray(StandardCharsets.UTF_8),
            byteArrayOf(0x00),
            initiatorHashedToken
        )
    }

    private fun getChannelBindingData(sslSocket: SSLSocket): ByteArray {
        if (this.channelBinding == ChannelBinding.NONE) {
            return ByteArray(0)
        }
        return try {
            ChannelBindingMechanism.getChannelBindingData(sslSocket, this.channelBinding)
        } catch (e: SaslMechanism.AuthenticationException) {
            Log.e(
                Config.LOGTAG,
                "${account.jid.asBareJid()}: unable to retrieve channel binding data for ${getMechanism()}",
                e
            )
            ByteArray(0)
        }
    }

    @Throws(SaslMechanism.AuthenticationException::class)
    override fun getResponse(challenge: ByteArray, socket: SSLSocket): ByteArray {
        val token = Strings.nullToEmpty(this.account.fastToken)
        val hashing = getHashFunction(token.toByteArray(StandardCharsets.UTF_8))
        val cbData = getChannelBindingData(socket)
        val expectedResponderMessage = hashing.hashBytes(Bytes.concat(RESPONDER, cbData)).asBytes()
        // TODO handle the 0x00 prefix for success responses
        // we know the length of the hmac and if the response is exactly one byte longer and is 00
        // then it's fine
        if (Arrays.equals(challenge, expectedResponderMessage)) {
            return ByteArray(0)
        }
        throw SaslMechanism.AuthenticationException("Responder message did not match")
    }

    protected abstract fun getHashFunction(key: ByteArray): HashFunction

    abstract fun getTokenMechanism(): Mechanism

    override fun getMechanism(): String = getTokenMechanism().name()

    override fun getChannelBinding(): ChannelBinding = this.channelBinding

    data class Mechanism(val hashFunction: String, val channelBinding: ChannelBinding) {

        fun name(): String =
            String.format("%s-%s-%s", PREFIX, hashFunction, ChannelBinding.SHORT_NAMES.get(channelBinding))

        companion object {
            @JvmStatic
            fun of(mechanism: String): Mechanism {
                val first = mechanism.indexOf('-')
                val last = mechanism.lastIndexOf('-')
                if (first == -1 || last == -1 || last < first) {
                    throw IllegalArgumentException("Not a valid HashedToken name")
                }
                if (mechanism.substring(0, first) == PREFIX) {
                    val hashFunction = mechanism.substring(first + 1, last)
                    val cbShortName = mechanism.substring(last + 1)
                    val channelBinding = ChannelBinding.SHORT_NAMES.inverse()[cbShortName]
                        ?: throw IllegalArgumentException("Unknown channel binding $cbShortName")
                    return Mechanism(hashFunction, channelBinding)
                } else {
                    throw IllegalArgumentException("HashedToken name does not start with HT")
                }
            }

            @JvmStatic
            fun ofOrNull(mechanism: String?): Mechanism? {
                return try {
                    if (mechanism == null) null else of(mechanism)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }

            @JvmStatic
            fun of(mechanisms: Collection<String>): Multimap<String, ChannelBinding> {
                val builder = ImmutableMultimap.builder<String, ChannelBinding>()
                for (name in mechanisms) {
                    try {
                        val mechanism = of(name)
                        builder.put(mechanism.hashFunction, mechanism.channelBinding)
                    } catch (ignored: IllegalArgumentException) {
                    }
                }
                return builder.build()
            }

            @JvmStatic
            fun best(mechanisms: Collection<String>, sslVersion: SSLSockets.Version): Mechanism? {
                val multimap = of(mechanisms)
                for (hashFunction in HASH_FUNCTIONS) {
                    val channelBindings = multimap.get(hashFunction)
                    if (channelBindings.isEmpty()) {
                        continue
                    }
                    val cb = ChannelBinding.best(channelBindings, sslVersion)
                    return Mechanism(hashFunction, cb)
                }
                return null
            }
        }
    }

    companion object {
        private const val PREFIX = "HT"
        private val HASH_FUNCTIONS = listOf("SHA-512", "SHA-256")
        private val INITIATOR = "Initiator".toByteArray(StandardCharsets.UTF_8)
        private val RESPONDER = "Responder".toByteArray(StandardCharsets.UTF_8)
    }
}
