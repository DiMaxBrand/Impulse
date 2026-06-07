package eu.siacs.conversations.crypto.sasl

import com.google.common.base.CaseFormat
import com.google.common.base.Joiner
import com.google.common.base.Objects
import com.google.common.base.Preconditions
import com.google.common.base.Splitter
import com.google.common.base.Strings
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.ImmutableMap
import com.google.common.hash.HashFunction
import com.google.common.io.BaseEncoding
import com.google.common.primitives.Ints
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.utils.CryptoHelper
import java.security.InvalidKeyException
import java.util.Arrays
import java.util.concurrent.ExecutionException
import javax.crypto.SecretKey
import javax.net.ssl.SSLSocket

abstract class ScramMechanism(account: Account, @get:JvmName("getChannelBindingValue") protected val channelBinding: ChannelBinding) :
    SaslMechanism(account) {

    private val gs2Header: String
    private val clientNonce: String
    private val clientFirstMessageBare: String
    private var serverSignature: ByteArray? = null
    private var downgradeProtection: DowngradeProtection? = null

    init {
        gs2Header = if (channelBinding == ChannelBinding.NONE) {
            "y,,"
        } else {
            String.format(
                "p=%s,,",
                CaseFormat.UPPER_UNDERSCORE
                    .converterTo(CaseFormat.LOWER_HYPHEN)
                    .convert(channelBinding.toString())
            )
        }
        // This nonce should be different for each authentication attempt.
        clientNonce = CryptoHelper.random(100)
        clientFirstMessageBare = String.format(
            "n=%s,r=%s",
            CryptoHelper.saslEscape(CryptoHelper.saslPrep(account.username)),
            clientNonce
        )
    }

    fun setDowngradeProtection(downgradeProtection: DowngradeProtection) {
        Preconditions.checkState(
            this.state == State.INITIAL, "setting downgrade protection in invalid state"
        )
        this.downgradeProtection = downgradeProtection
    }

    protected abstract fun getHMac(key: ByteArray?): HashFunction

    protected abstract fun getDigest(): HashFunction

    @Throws(ExecutionException::class)
    private fun getKeyPair(password: String, salt: ByteArray, iterations: Int): KeyPair {
        val key = CacheKey(getMechanism(), password, salt, iterations)
        return CACHE.get(key) { calculateKeyPair(password, salt, iterations) }
    }

    @Throws(InvalidKeyException::class)
    private fun calculateKeyPair(password: String, salt: ByteArray, iterations: Int): KeyPair {
        val saltedPassword = hi(password.toByteArray(), salt, iterations)
        val serverKey = hmac(saltedPassword, SERVER_KEY_BYTES)
        val clientKey = hmac(saltedPassword, CLIENT_KEY_BYTES)
        return KeyPair(clientKey, serverKey)
    }

    override fun getMechanism(): String = ""

    @Throws(InvalidKeyException::class)
    private fun hmac(key: ByteArray, input: ByteArray): ByteArray =
        getHMac(key).hashBytes(input).asBytes()

    private fun digest(bytes: ByteArray): ByteArray =
        getDigest().hashBytes(bytes).asBytes()

    /*
     * Hi() is, essentially, PBKDF2 [RFC2898] with HMAC() as the
     * pseudorandom function (PRF) and with dkLen == output length of
     * HMAC() == output length of H().
     */
    @Throws(InvalidKeyException::class)
    private fun hi(key: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        var u = hmac(key, CryptoHelper.concatenateByteArrays(salt, CryptoHelper.ONE))
        val out = u.clone()
        for (i in 1 until iterations) {
            u = hmac(key, u)
            for (j in u.indices) {
                out[j] = (out[j].toInt() xor u[j].toInt()).toByte()
            }
        }
        return out
    }

    override fun getClientFirstMessage(sslSocket: SSLSocket): ByteArray {
        Preconditions.checkState(
            this.state == State.INITIAL, "Calling getClientFirstMessage from invalid state"
        )
        this.state = State.AUTH_TEXT_SENT
        return (gs2Header + clientFirstMessageBare).toByteArray()
    }

    @Throws(AuthenticationException::class)
    override fun getResponse(challenge: ByteArray, socket: SSLSocket): ByteArray {
        return when (state) {
            State.AUTH_TEXT_SENT -> processServerFirstMessage(challenge, socket)
            State.RESPONSE_SENT -> processServerFinalMessage(challenge)
            else -> throw InvalidStateException(state)
        }
    }

    @Throws(AuthenticationException::class)
    private fun processServerFirstMessage(challenge: ByteArray, socket: SSLSocket): ByteArray {
        if (challenge.isEmpty()) {
            throw AuthenticationException("challenge can not be null")
        }
        val attributes: Map<String, String>
        try {
            attributes = splitToAttributes(String(challenge))
        } catch (e: IllegalArgumentException) {
            throw AuthenticationException("Duplicate attributes")
        }
        if (attributes.containsKey("m")) {
            /*
             * RFC 5802:
             * m: This attribute is reserved for future extensibility.  In this
             * version of SCRAM, its presence in a client or a server message
             * MUST cause authentication failure when the attribute is parsed by
             * the other end.
             */
            throw AuthenticationException("Server sent reserved token: 'm'")
        }
        val i = attributes["i"]
        val s = attributes["s"]
        val nonce = attributes["r"]
        val h = attributes["h"]
        if (Strings.isNullOrEmpty(s) || Strings.isNullOrEmpty(nonce) || Strings.isNullOrEmpty(i)) {
            throw AuthenticationException("Missing attributes from server first message")
        }
        val iterationCount = Ints.tryParse(i!!)

        if (iterationCount == null || iterationCount < 0) {
            throw AuthenticationException("Server did not send iteration count")
        }

        if (iterationCount < ITERATION_COUNT_MINIMUM) {
            throw AuthenticationException(
                String.format(
                    "Weak iteration count. %d instead of %d",
                    iterationCount, ITERATION_COUNT_MINIMUM
                )
            )
        }

        if (!nonce!!.startsWith(clientNonce)) {
            throw AuthenticationException(
                "Server nonce does not contain client nonce: $nonce"
            )
        }

        val salt: ByteArray
        try {
            salt = BaseEncoding.base64().decode(s!!)
        } catch (e: IllegalArgumentException) {
            throw AuthenticationException("Invalid salt in server first message")
        }

        if (h != null && this.downgradeProtection != null) {
            val asSeenInFeatures: String
            try {
                asSeenInFeatures = downgradeProtection!!.asHString()
            } catch (e: SecurityException) {
                throw AuthenticationException(e)
            }
            val hashed = BaseEncoding.base64().encode(digest(asSeenInFeatures.toByteArray()))
            if (hashed != h) {
                throw AuthenticationException("Mismatch in SSDP")
            }
        }

        val channelBindingData = getChannelBindingData(socket)

        val gs2Len = gs2Header.toByteArray().size
        val cMessage = ByteArray(gs2Len + channelBindingData.size)
        System.arraycopy(gs2Header.toByteArray(), 0, cMessage, 0, gs2Len)
        System.arraycopy(channelBindingData, 0, cMessage, gs2Len, channelBindingData.size)

        val clientFinalMessageWithoutProof =
            String.format("c=%s,r=%s", BaseEncoding.base64().encode(cMessage), nonce)

        val authMessage = Joiner.on(',').join(
            clientFirstMessageBare,
            String(challenge),
            clientFinalMessageWithoutProof
        )

        val keys: KeyPair
        try {
            keys = getKeyPair(CryptoHelper.saslPrep(account.password), salt, iterationCount)
        } catch (e: ExecutionException) {
            throw AuthenticationException("Invalid keys generated")
        }
        val clientSignature: ByteArray
        try {
            serverSignature = hmac(keys.serverKey, authMessage.toByteArray())
            val storedKey = digest(keys.clientKey)
            clientSignature = hmac(storedKey, authMessage.toByteArray())
        } catch (e: InvalidKeyException) {
            throw AuthenticationException(e)
        }

        val clientProof = ByteArray(keys.clientKey.size)

        if (clientSignature.size < keys.clientKey.size) {
            throw AuthenticationException("client signature was shorter than clientKey")
        }

        for (j in clientProof.indices) {
            clientProof[j] = (keys.clientKey[j].toInt() xor clientSignature[j].toInt()).toByte()
        }

        val clientFinalMessage = String.format(
            "%s,p=%s",
            clientFinalMessageWithoutProof,
            BaseEncoding.base64().encode(clientProof)
        )
        this.state = State.RESPONSE_SENT
        return clientFinalMessage.toByteArray()
    }

    private fun splitToAttributes(message: String): Map<String, String> {
        val builder = ImmutableMap.Builder<String, String>()
        for (token in Splitter.on(',').split(message)) {
            val tuple = Splitter.on('=').limit(2).splitToList(token)
            if (tuple.size == 2) {
                builder.put(tuple[0], tuple[1])
            }
        }
        return builder.buildOrThrow()
    }

    @Throws(AuthenticationException::class)
    private fun processServerFinalMessage(challenge: ByteArray): ByteArray {
        val serverFinalMessage = String(challenge)
        val clientCalculatedServerFinalMessage =
            String.format("v=%s", BaseEncoding.base64().encode(serverSignature!!))
        if (clientCalculatedServerFinalMessage == serverFinalMessage) {
            this.state = State.VALID_SERVER_RESPONSE
            return ByteArray(0)
        }
        throw AuthenticationException(
            "Server final message does not match calculated final message"
        )
    }

    @Throws(AuthenticationException::class)
    protected open fun getChannelBindingData(sslSocket: SSLSocket): ByteArray {
        if (this.channelBinding == ChannelBinding.NONE) {
            return ByteArray(0)
        }
        throw AssertionError("getChannelBindingData needs to be overwritten")
    }

    private class CacheKey(
        private val algorithm: String,
        private val password: String,
        private val salt: ByteArray,
        private val iterations: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val cacheKey = other as CacheKey
            return iterations == cacheKey.iterations
                && Objects.equal(algorithm, cacheKey.algorithm)
                && Objects.equal(password, cacheKey.password)
                && Arrays.equals(salt, cacheKey.salt)
        }

        override fun hashCode(): Int {
            val result = Objects.hashCode(algorithm, password, iterations)
            return 31 * result + Arrays.hashCode(salt)
        }
    }

    private class KeyPair(val clientKey: ByteArray, val serverKey: ByteArray)

    companion object {
        @JvmField
        val EMPTY_KEY: SecretKey = object : SecretKey {
            override fun getAlgorithm(): String = "HMAC"
            override fun getFormat(): String = "RAW"
            override fun getEncoded(): ByteArray = ByteArray(0)
        }

        // For the SCRAM-SHA-1/SCRAM-SHA-1-PLUS SASL mechanism, servers SHOULD announce a hash
        // iteration-count of at least 4096.
        // https://datatracker.ietf.org/doc/html/rfc5802#section-5.1
        private const val ITERATION_COUNT_MINIMUM = 4096
        private val CLIENT_KEY_BYTES = "Client Key".toByteArray()
        private val SERVER_KEY_BYTES = "Server Key".toByteArray()
        private val CACHE: Cache<CacheKey, KeyPair> =
            CacheBuilder.newBuilder().maximumSize(10).build()
    }
}
