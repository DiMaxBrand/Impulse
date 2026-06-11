package eu.siacs.conversations.crypto.sasl

import eu.siacs.conversations.entities.Account
import javax.net.ssl.SSLSocket

abstract class ScramPlusMechanism(account: Account, channelBinding: ChannelBinding) :
    ScramMechanism(account, channelBinding), ChannelBindingMechanism {

    @Throws(AuthenticationException::class)
    override fun getChannelBindingData(sslSocket: SSLSocket): ByteArray =
        ChannelBindingMechanism.getChannelBindingData(sslSocket, channelBinding)

    override fun getChannelBinding(): ChannelBinding = channelBinding
}
