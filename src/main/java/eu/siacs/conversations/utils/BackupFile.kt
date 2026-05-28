package eu.siacs.conversations.utils

import android.content.Context
import android.content.UriPermission
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.common.collect.Collections2
import com.google.common.collect.ComparisonChain
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Ordering
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import eu.siacs.conversations.persistance.DatabaseBackend
import eu.siacs.conversations.persistance.FileBackend
import eu.siacs.conversations.services.QuickConversationsService
import eu.siacs.conversations.worker.ExportBackupWorker
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.time.temporal.ChronoUnit
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BackupFile private constructor(
    private val uri: Uri,
    private val header: BackupFileHeader
) : Comparable<BackupFile> {

    fun getHeader(): BackupFileHeader = header

    fun getUri(): Uri = uri

    override fun compareTo(other: BackupFile): Int =
        ComparisonChain.start()
            .compare(
                other.header.getInstant().truncatedTo(ChronoUnit.DAYS),
                header.getInstant().truncatedTo(ChronoUnit.DAYS)
            )
            .compare(header.jid, other.header.jid)
            .compare(other.header.timestamp, header.timestamp)
            .result()

    companion object {
        private val BACKUP_FILE_READER_EXECUTOR: ExecutorService =
            Executors.newSingleThreadExecutor()

        @JvmStatic
        fun readAsync(context: Context, uri: Uri): ListenableFuture<BackupFile> =
            Futures.submit<BackupFile>({ read(context, uri) }, BACKUP_FILE_READER_EXECUTOR)

        @Throws(IOException::class)
        private fun read(file: File): BackupFile {
            val fileInputStream = FileInputStream(file)
            val dataInputStream = DataInputStream(fileInputStream)
            val backupFileHeader = BackupFileHeader.read(dataInputStream)
            fileInputStream.close()
            return BackupFile(Uri.fromFile(file), backupFileHeader)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun read(context: Context, uri: Uri): BackupFile {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException()
            val dataInputStream = DataInputStream(inputStream)
            val backupFileHeader = BackupFileHeader.read(dataInputStream)
            inputStream.close()
            return BackupFile(uri, backupFileHeader)
        }

        @JvmStatic
        fun listAsync(context: Context): ListenableFuture<List<BackupFile>> =
            Futures.submit<List<BackupFile>>({ list(context) }, BACKUP_FILE_READER_EXECUTOR)

        private fun list(context: Context): List<BackupFile> {
            val database = DatabaseBackend.getInstance(context)
            val accounts = database.accountAddresses
            val backupFiles = ImmutableList.Builder<BackupFile>()
            val apps = ImmutableSet.of(
                "Conversations", "Impulse", "Импульс", "Quicksy",
                context.getString(R.string.app_name)
            )

            val uriPermissions = context.contentResolver.persistedUriPermissions

            for (uriPermission: UriPermission in uriPermissions) {
                val uri = uriPermission.uri
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    && DocumentsContract.isTreeUri(uri)
                ) {
                    Log.d(Config.LOGTAG, "looking for backups in $uri")
                    val tree = DocumentFile.fromTreeUri(context, uriPermission.uri)
                    val files = tree?.listFiles() ?: emptyArray()
                    for (documentFile: DocumentFile in files) {
                        val name = documentFile.name
                        if (documentFile.isFile
                            && (ExportBackupWorker.MIME_TYPE == documentFile.type
                                || (name != null && name.endsWith(".ceb")))
                        ) {
                            try {
                                val backupFile = read(context, documentFile.uri)
                                if (accounts.contains(backupFile.header.jid)) {
                                    Log.d(Config.LOGTAG, "skipping backup for ${backupFile.header.jid}")
                                } else {
                                    backupFiles.add(backupFile)
                                }
                            } catch (e: IOException) {
                                Log.d(Config.LOGTAG, "unable to read backup file ", e)
                            } catch (e: IllegalArgumentException) {
                                Log.d(Config.LOGTAG, "unable to read backup file ", e)
                            } catch (e: BackupFileHeader.OutdatedBackupFileVersion) {
                                Log.d(Config.LOGTAG, "unable to read backup file ", e)
                            }
                        }
                    }
                }
            }

            val directories = mutableListOf<File>()
            for (app: String in apps) {
                directories.add(FileBackend.getLegacyBackupDirectory(app))
            }
            if (uriPermissions.isEmpty()) {
                Log.d(
                    Config.LOGTAG,
                    "including default directory since no uri permissions have been granted"
                )
                directories.add(FileBackend.getBackupDirectory(context))
            }
            for (directory: File in directories) {
                if (!directory.exists() || !directory.isDirectory) {
                    Log.d(Config.LOGTAG, "directory not found: ${directory.absolutePath}")
                    continue
                }
                val files = directory.listFiles() ?: continue
                Log.d(Config.LOGTAG, "looking for backups in $directory")
                for (file: File in files) {
                    if (file.isFile && file.name.endsWith(".ceb")) {
                        try {
                            val backupFile = read(file)
                            if (accounts.contains(backupFile.header.jid)) {
                                Log.d(Config.LOGTAG, "skipping backup for ${backupFile.header.jid}")
                            } else {
                                backupFiles.add(backupFile)
                            }
                        } catch (e: IOException) {
                            Log.d(Config.LOGTAG, "unable to read backup file ", e)
                        } catch (e: IllegalArgumentException) {
                            Log.d(Config.LOGTAG, "unable to read backup file ", e)
                        } catch (e: BackupFileHeader.OutdatedBackupFileVersion) {
                            Log.d(Config.LOGTAG, "unable to read backup file ", e)
                        }
                    }
                }
            }
            val list = backupFiles.build()
            if (QuickConversationsService.isQuicksy()) {
                return Ordering.natural<BackupFile>().immutableSortedCopy(
                    Collections2.filter(list) { b ->
                        b!!.header.jid.domain == Config.QUICKSY_DOMAIN
                    }
                )
            }
            return Ordering.natural<BackupFile>().immutableSortedCopy(backupFiles.build())
        }
    }
}
