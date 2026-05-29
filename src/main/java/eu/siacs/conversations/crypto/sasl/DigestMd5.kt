package eu.siacs.conversations.crypto.sasl

import android.util.Log
import androidx.annotation.NonNull
import com.google.common.base.Preconditions
import com.google.common.base.Splitter
import com.google.common.base.Strings
import com.google.common.collect.ImmutableMap
import com.google.common.hash.Hashing
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.utils.CryptoHelper
import java.nio.charset.Charset
import javax.net.ssl.SSLSocket

class DigestMd5(account: Account) : SaslMechanism(account) {

    companion object {
        const val MECHANISM = "DIGEST-MD5"

        @JvmStatic
        fun trimQuotes(@NonNull input: String): String {
            if (input.length >= 2 && input[0] == '"' && input[input.length - 1] == '"') {
                return input.substring(1, input.length - 1)
            }
            return input
        }

        private fun messageToAttributes(asBytes: ByteArray): Map<String, String> {
            try {
                return splitToAttributes(String(asBytes))
            } catch (e: IllegalArgumentException) {
                throw SaslMechanism.AuthenticationException("Duplicate attributes")
            }
        }

        private fun splitToAttributes(message: String): Map<String, String> {
            val builder = ImmutableMap.Builder<String, String>()
            for (token in Splitter.on(',').split(message)) {
                val tuple = Splitter.on('=').limit(2).splitToList(token)
                if (tuple.size == 2) {
                    val value = tuple[1]
                    builder.put(tuple[0], trimQuotes(value))
                }
            }
            return builder.buildOrThrow()
        }
    }

    private var digestMd5State = State.INITIAL
    private var precalculatedRSPAuth: String? = null

    override fun getPriority(): Int = 10

    override fun getMechanism(): String = MECHANISM

    override fun getClientFirstMessage(sslSocket: SSLSocket): ByteArray {
        Preconditions.checkState(
            this.digestMd5State == State.INITIAL,
            "Calling getClientFirstMessage from invalid state"
        )
        this.digestMd5State = State.AUTH_TEXT_SENT
        return ByteArray(0)
    }

    @Throws(SaslMechanism.AuthenticationException::class)
    override fun getResponse(challenge: ByteArray, socket: SSLSocket): ByteArray {
        return when (digestMd5State) {
            State.AUTH_TEXT_SENT -> processChallenge(challenge, socket)
            State.RESPONSE_SENT -> validateServerResponse(challenge)
            State.VALID_SERVER_RESPONSE -> validateUnnecessarySuccessMessage(challenge)
            else -> throw InvalidStateException(digestMd5State)
        }
    }

    // ejabberd sends the RSPAuth response as a challenge and then an empty success
    // technically this is allowed as per https://datatracker.ietf.org/doc/html/rfc2222#section-5.2
    // although it says to do that only if the profile of the protocol does not allow data to be put
    // into success. which xmpp does allow. obviously
    @Throws(SaslMechanism.AuthenticationException::class)
    private fun validateUnnecessarySuccessMessage(challenge: ByteArray): ByteArray {
        if (challenge.isEmpty()) {
            return ByteArray(0)
        }
        throw SaslMechanism.AuthenticationException("Success message must be empty")
    }

    @Throws(SaslMechanism.AuthenticationException::class)
    private fun validateServerResponse(challenge: ByteArray): ByteArray {
        val attributes = messageToAttributes(challenge)
        Log.d(Config.LOGTAG, "attributes: $attributes")
        val rspauth = attributes["rspauth"]
        if (Strings.isNullOrEmpty(rspauth)) {
            throw SaslMechanism.AuthenticationException("no rspauth in server finish message")
        }
        val expected = this.precalculatedRSPAuth
        if (Strings.isNullOrEmpty(expected) || this.precalculatedRSPAuth != rspauth) {
            throw SaslMechanism.AuthenticationException("RSPAuth mismatch")
        }
        this.digestMd5State = State.VALID_SERVER_RESPONSE
        return ByteArray(0)
    }

    @Throws(SaslMechanism.AuthenticationException::class)
    private fun processChallenge(challenge: ByteArray, socket: SSLSocket): ByteArray {
        Log.d(Config.LOGTAG, "DigestMd5.processChallenge()")
        this.digestMd5State = State.RESPONSE_SENT
        val attributes = messageToAttributes(challenge)

        val nonce = attributes["nonce"]

        if (Strings.isNullOrEmpty(nonce)) {
            throw SaslMechanism.AuthenticationException("Server nonce missing")
        }
        val digestUri = "xmpp/" + account.server
        val nonceCount = "00000001"
        val x = account.username + ":" + account.server + ":" + account.password
        val y = Hashing.md5().hashBytes(x.toByteArray(Charset.defaultCharset())).asBytes()
        val cNonce = CryptoHelper.random(100)
        val a1 = CryptoHelper.concatenateByteArrays(
            y, (":" + nonce + ":" + cNonce).toByteArray(Charset.defaultCharset())
        )
        val a2 = "AUTHENTICATE:$digestUri"
        val ha1 = CryptoHelper.bytesToHex(Hashing.md5().hashBytes(a1).asBytes())
        val ha2 = CryptoHelper.bytesToHex(
            Hashing.md5().hashBytes(a2.toByteArray(Charset.defaultCharset())).asBytes()
        )
        val kd = "$ha1:$nonce:$nonceCount:$cNonce:auth:$ha2"

        val a2ForResponse = ":$digestUri"
        val ha2ForResponse = CryptoHelper.bytesToHex(
            Hashing.md5().hashBytes(a2ForResponse.toByteArray(Charset.defaultCharset())).asBytes()
        )
        val kdForResponseInput = "$ha1:$nonce:$nonceCount:$cNonce:auth:$ha2ForResponse"

        this.precalculatedRSPAuth = CryptoHelper.bytesToHex(
            Hashing.md5().hashBytes(kdForResponseInput.toByteArray(Charset.defaultCharset())).asBytes()
        )

        val response = CryptoHelper.bytesToHex(
            Hashing.md5().hashBytes(kd.toByteArray(Charset.defaultCharset())).asBytes()
        )

        val saslString =
            "username=\"${account.username}\",realm=\"${account.server}\",nonce=\"$nonce\",cnonce=\"$cNonce\",nc=$nonceCount,qop=auth,digest-uri=\"$digestUri\",response=$response,charset=utf-8"
        return saslString.toByteArray()
    }
}
