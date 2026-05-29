package eu.siacs.conversations.crypto.sasl

import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import eu.siacs.conversations.entities.Account

class ScramSha512(account: Account) : ScramMechanism(account, ChannelBinding.NONE) {

    companion object {
        const val MECHANISM = "SCRAM-SHA-512"
    }

    override fun getHMac(key: ByteArray?): HashFunction =
        if (key == null || key.isEmpty()) Hashing.hmacSha512(EMPTY_KEY) else Hashing.hmacSha512(key)

    override fun getDigest(): HashFunction = Hashing.sha512()

    override fun getPriority(): Int = 30

    override fun getMechanism(): String = MECHANISM
}
