package eu.siacs.conversations.crypto.axolotl

open class CryptoFailedException : Exception {
    constructor(msg: String) : super(msg)
    constructor(msg: String, e: Exception) : super(msg, e)
    constructor(e: Exception) : super(e)
}
