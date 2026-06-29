package eu.siacs.conversations.entities

interface Transferable {
    companion object {
        @JvmField
        val VALID_IMAGE_EXTENSIONS = listOf("webp", "jpeg", "jpg", "png", "jpe")
        @JvmField
        val VALID_CRYPTO_EXTENSIONS = listOf("pgp", "gpg")
        @JvmField
        val GCM_AUTHENTICATION_TAG_LENGTH = 16
        @JvmField
        val STATUS_UNKNOWN = 0x200
        @JvmField
        val STATUS_CHECKING = 0x201
        @JvmField
        val STATUS_FAILED = 0x202
        @JvmField
        val STATUS_OFFER = 0x203
        @JvmField
        val STATUS_DOWNLOADING = 0x204
        @JvmField
        val STATUS_OFFER_CHECK_FILESIZE = 0x206
        @JvmField
        val STATUS_UPLOADING = 0x207
        @JvmField
        val STATUS_CANCELLED = 0x208
        @JvmField
        val STATUS_COMPRESSING = 0x209
    }

    fun start(): Boolean
    fun getStatus(): Int
    fun getFileSize(): Long?
    fun getProgress(): Int
    fun cancel()
}
