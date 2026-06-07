package eu.siacs.conversations.crypto.sasl

import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import eu.siacs.conversations.entities.Account

class ScramSha1(account: Account) : ScramMechanism(account, ChannelBinding.NONE) {
    companion object {
        const val MECHANISM = "SCRAM-SHA-1"
    }

    override fun getHMac(key: ByteArray?): HashFunction =
        if (key == null || key.isEmpty()) Hashing.hmacSha1(EMPTY_KEY) else Hashing.hmacSha1(key)

    override fun getDigest(): HashFunction = Hashing.sha1()

    override fun getPriority(): Int = 20

    override fun getMechanism(): String = MECHANISM
}
