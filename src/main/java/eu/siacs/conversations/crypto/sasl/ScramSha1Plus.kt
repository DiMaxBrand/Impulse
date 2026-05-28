package eu.siacs.conversations.crypto.sasl

import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import eu.siacs.conversations.entities.Account

class ScramSha1Plus(account: Account, channelBinding: ChannelBinding) :
    ScramPlusMechanism(account, channelBinding) {

    companion object {
        const val MECHANISM = "SCRAM-SHA-1-PLUS"
    }

    override fun getHMac(key: ByteArray?): HashFunction =
        if (key == null || key.isEmpty()) Hashing.hmacSha1(EMPTY_KEY) else Hashing.hmacSha1(key)

    override fun getDigest(): HashFunction = Hashing.sha1()

    override fun getPriority(): Int = 35 + ChannelBinding.priority(channelBinding)

    override fun getMechanism(): String = MECHANISM
}
