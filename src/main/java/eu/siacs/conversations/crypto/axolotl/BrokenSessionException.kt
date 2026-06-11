package eu.siacs.conversations.crypto.axolotl

import org.whispersystems.libsignal.SignalProtocolAddress

class BrokenSessionException(
    val signalProtocolAddress: SignalProtocolAddress,
    e: Exception,
) : CryptoFailedException(e)
