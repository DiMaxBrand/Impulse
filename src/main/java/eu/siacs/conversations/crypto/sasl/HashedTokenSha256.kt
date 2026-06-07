package eu.siacs.conversations.crypto.sasl

import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import eu.siacs.conversations.entities.Account

class HashedTokenSha256(account: Account, channelBinding: ChannelBinding) :
    HashedToken(account, channelBinding) {

    override fun getHashFunction(key: ByteArray): HashFunction = Hashing.hmacSha256(key)

    override fun getTokenMechanism(): Mechanism = Mechanism("SHA-256", channelBinding)
}
