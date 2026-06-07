package eu.siacs.conversations.crypto.sasl

import com.google.common.base.Preconditions
import eu.siacs.conversations.entities.Account
import javax.net.ssl.SSLSocket

class External(account: Account) : SaslMechanism(account) {

    companion object {
        const val MECHANISM = "EXTERNAL"
    }

    override fun getPriority(): Int = 25

    override fun getMechanism(): String = MECHANISM

    override fun getClientFirstMessage(sslSocket: SSLSocket): ByteArray {
        Preconditions.checkState(
            this.state == State.INITIAL,
            "Calling getClientFirstMessage from invalid state"
        )
        this.state = State.AUTH_TEXT_SENT
        val message = account.jid.asBareJid().toString()
        return message.toByteArray()
    }

    @Throws(SaslMechanism.AuthenticationException::class)
    override fun getResponse(challenge: ByteArray, sslSocket: SSLSocket): ByteArray {
        if (this.state != State.AUTH_TEXT_SENT) {
            throw InvalidStateException(this.state)
        }
        this.state = State.VALID_SERVER_RESPONSE
        return ByteArray(0)
    }
}
