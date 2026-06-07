package eu.siacs.conversations.entities

class TransferablePlaceholder(private val status: Int) : Transferable {
    override fun start(): Boolean = false
    override fun getStatus(): Int = status
    override fun getFileSize(): Long? = null
    override fun getProgress(): Int = 0
    override fun cancel() {}
}
