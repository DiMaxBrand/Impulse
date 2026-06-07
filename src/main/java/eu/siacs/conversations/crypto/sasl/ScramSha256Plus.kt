package eu.siacs.conversations.crypto.sasl

import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import eu.siacs.conversations.entities.Account

class ScramSha256Plus(account: Account, channelBinding: ChannelBinding) :
    ScramPlusMechanism(account, channelBinding) {

    companion object {
        const val MECHANISM = "SCRAM-SHA-256-PLUS"
    }

    override fun getHMac(key: ByteArray?): HashFunction =
        if (key == null || key.isEmpty()) Hashing.hmacSha256(EMPTY_KEY) else Hashing.hmacSha256(key)

    override fun getDigest(): HashFunction = Hashing.sha256()

    override fun getPriority(): Int = 40 + ChannelBinding.priority(channelBinding)

    override fun getMechanism(): String = MECHANISM
}
