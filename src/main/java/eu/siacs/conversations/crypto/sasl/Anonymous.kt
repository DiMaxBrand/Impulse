package eu.siacs.conversations.crypto.sasl

import com.google.common.base.Preconditions
import eu.siacs.conversations.entities.Account
import javax.net.ssl.SSLSocket

class Anonymous(account: Account) : SaslMechanism(account) {

    companion object {
        const val MECHANISM = "ANONYMOUS"
    }

    override fun getPriority(): Int = 0

    override fun getMechanism(): String = MECHANISM

    override fun getClientFirstMessage(sslSocket: SSLSocket): ByteArray {
        Preconditions.checkState(
            this.state == State.INITIAL,
            "Calling getClientFirstMessage from invalid state"
        )
        this.state = State.AUTH_TEXT_SENT
        return ByteArray(0)
    }

    @Throws(SaslMechanism.AuthenticationException::class)
    override fun getResponse(challenge: ByteArray, sslSocket: SSLSocket): ByteArray {
        checkState(State.AUTH_TEXT_SENT)
        if (challenge.isEmpty()) {
            this.state = State.VALID_SERVER_RESPONSE
            return ByteArray(0)
        }
        throw AuthenticationException("Unexpected server response")
    }
}
