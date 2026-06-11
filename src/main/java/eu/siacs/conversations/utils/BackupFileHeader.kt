package eu.siacs.conversations.utils

import eu.siacs.conversations.xmpp.Jid
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.time.Instant

class BackupFileHeader(
    val app: String,
    val jid: Jid,
    val timestamp: Long,
    val iv: ByteArray,
    val salt: ByteArray
) {

    override fun toString(): String =
        "BackupFileHeader{app='$app', jid=$jid, timestamp=$timestamp" +
            ", iv=${CryptoHelper.bytesToHex(iv)}, salt=${CryptoHelper.bytesToHex(salt)}}"

    @Throws(IOException::class)
    fun write(dataOutputStream: DataOutputStream) {
        dataOutputStream.writeInt(VERSION)
        dataOutputStream.writeUTF(app)
        dataOutputStream.writeUTF(jid.asBareJid().toString())
        dataOutputStream.writeLong(timestamp)
        dataOutputStream.write(iv)
        dataOutputStream.write(salt)
    }

    fun getInstant(): Instant = Instant.ofEpochMilli(timestamp)

    class OutdatedBackupFileVersion : RuntimeException()

    companion object {
        private const val VERSION = 2

        @JvmStatic
        @Throws(IOException::class)
        fun read(inputStream: DataInputStream): BackupFileHeader {
            val version = inputStream.readInt()
            val app = inputStream.readUTF()
            val jid = inputStream.readUTF()
            val timestamp = inputStream.readLong()
            val iv = ByteArray(12)
            inputStream.readFully(iv)
            val salt = ByteArray(16)
            inputStream.readFully(salt)
            if (version < VERSION) {
                throw OutdatedBackupFileVersion()
            }
            if (version != VERSION) {
                throw IllegalArgumentException(
                    "Backup File version was $version but app only supports version $VERSION"
                )
            }
            return BackupFileHeader(app, Jid.of(jid), timestamp, iv, salt)
        }
    }
}
