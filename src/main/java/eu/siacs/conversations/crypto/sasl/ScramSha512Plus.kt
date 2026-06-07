package eu.siacs.conversations.crypto.sasl

import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import eu.siacs.conversations.entities.Account

class ScramSha512Plus(account: Account, channelBinding: ChannelBinding) :
    ScramPlusMechanism(account, channelBinding) {

    companion object {
        const val MECHANISM = "SCRAM-SHA-512-PLUS"
    }

    override fun getHMac(key: ByteArray?): HashFunction =
        if (key == null || key.isEmpty()) Hashing.hmacSha512(EMPTY_KEY) else Hashing.hmacSha512(key)

    override fun getDigest(): HashFunction = Hashing.sha512()

    override fun getPriority(): Int = 45 + ChannelBinding.priority(channelBinding)

    override fun getMechanism(): String = MECHANISM
}
