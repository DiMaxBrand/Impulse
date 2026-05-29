package eu.siacs.conversations.worker

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.common.base.Strings
import com.google.common.collect.ImmutableList
import com.google.gson.stream.JsonWriter
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import eu.siacs.conversations.crypto.axolotl.SQLiteAxolotlStore
import eu.siacs.conversations.entities.AbstractEntity
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.persistance.DatabaseBackend
import eu.siacs.conversations.persistance.FileBackend
import eu.siacs.conversations.services.QuickConversationsService
import eu.siacs.conversations.utils.BackupFileHeader
import eu.siacs.conversations.utils.Compatibility
import eu.siacs.conversations.utils.Compatibility.s
import eu.siacs.conversations.utils.Random
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.spec.InvalidKeySpecException
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Arrays
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class ExportBackupWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    private val recurringBackup: Boolean

    init {
        val inputData = workerParams.inputData
        this.recurringBackup = inputData.getBoolean("recurring_backup", false)
    }

    @NonNull
    override fun doWork(): Result {
        setForegroundAsync(getForegroundInfo())
        val files: List<Uri>
        try {
            files = export()
        } catch (e: IOException) {
            Log.d(Config.LOGTAG, "could not create backup", e)
            return Result.failure()
        } catch (e: InvalidKeySpecException) {
            Log.d(Config.LOGTAG, "could not create backup", e)
            return Result.failure()
        } catch (e: InvalidAlgorithmParameterException) {
            Log.d(Config.LOGTAG, "could not create backup", e)
            return Result.failure()
        } catch (e: InvalidKeyException) {
            Log.d(Config.LOGTAG, "could not create backup", e)
            return Result.failure()
        } catch (e: NoSuchPaddingException) {
            Log.d(Config.LOGTAG, "could not create backup", e)
            return Result.failure()
        } catch (e: NoSuchAlgorithmException) {
            Log.d(Config.LOGTAG, "could not create backup", e)
            return Result.failure()
        } catch (e: NoSuchProviderException) {
            Log.d(Config.LOGTAG, "could not create backup", e)
            return Result.failure()
        } finally {
            applicationContext.getSystemService(NotificationManager::class.java)
                .cancel(NOTIFICATION_ID)
        }
        Log.d(Config.LOGTAG, "done creating ${files.size} backup files")
        if (files.isEmpty() || recurringBackup) {
            return Result.success()
        }
        notifySuccess(files)
        return Result.success()
    }

    @NonNull
    override fun getForegroundInfo(): ForegroundInfo {
        Log.d(Config.LOGTAG, "getForegroundInfo()")
        val notification = getNotification()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification.build())
        }
    }

    @Throws(
        IOException::class,
        InvalidKeySpecException::class,
        InvalidAlgorithmParameterException::class,
        InvalidKeyException::class,
        NoSuchPaddingException::class,
        NoSuchAlgorithmException::class,
        NoSuchProviderException::class
    )
    private fun export(): List<Uri> {
        val context = applicationContext
        val appSettings = AppSettings(context)
        val backupLocation = appSettings.backupLocation
        val database = DatabaseBackend.getInstance(context)
        val accounts = database.accounts

        var count = 0
        val max = accounts.size
        val locations = ImmutableList.Builder<Uri>()
        Log.d(Config.LOGTAG, "starting backup for $max accounts")
        for (account in accounts) {
            if (isStopped) {
                Log.d(Config.LOGTAG, "ExportBackupWorker has stopped. Returning what we have")
                return locations.build()
            }
            val password = account.password
            if (Strings.nullToEmpty(password).trim().isEmpty()) {
                Log.d(
                    Config.LOGTAG,
                    String.format(
                        "skipping backup for %s because password is empty. unable to encrypt",
                        account.jid.asBareJid()
                    )
                )
                count++
                continue
            }
            val uri: Uri
            try {
                uri = export(database, account, password, backupLocation, max, count)
            } catch (e: WorkStoppedException) {
                Log.d(Config.LOGTAG, "ExportBackupWorker has stopped. Returning what we have")
                return locations.build()
            }
            locations.add(uri)
            count++
        }
        return locations.build()
    }

    @Throws(
        IOException::class,
        InvalidKeySpecException::class,
        InvalidAlgorithmParameterException::class,
        InvalidKeyException::class,
        NoSuchPaddingException::class,
        NoSuchAlgorithmException::class,
        NoSuchProviderException::class,
        WorkStoppedException::class
    )
    private fun export(
        database: DatabaseBackend,
        account: Account,
        password: String,
        backupLocation: Uri,
        max: Int,
        count: Int
    ): Uri {
        val context = applicationContext
        Log.d(
            Config.LOGTAG,
            String.format(
                "exporting data for account %s (%s)",
                account.jid.asBareJid(),
                account.getUuid()
            )
        )
        val IV = ByteArray(12)
        val salt = ByteArray(16)
        Random.SECURE_RANDOM.nextBytes(IV)
        Random.SECURE_RANDOM.nextBytes(salt)
        val backupFileHeader = BackupFileHeader(
            context.getString(R.string.app_name),
            account.jid,
            System.currentTimeMillis(),
            IV,
            salt
        )
        val notification = getNotification()
        val cancelPendingIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
        notification.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_cancel_24dp,
                context.getString(R.string.cancel),
                cancelPendingIntent
            ).build()
        )
        val progress = Progress(notification, max, count)
        val filename = String.format(
            "%s.%s.ceb",
            account.jid.asBareJid().toString(),
            DATE_FORMAT.format(Date())
        )
        val outputStream: OutputStream
        val location: Uri
        if ("file".equals(backupLocation.scheme, ignoreCase = true)) {
            val file = File(backupLocation.path, filename)
            val directory = file.parentFile
            if (directory != null && directory.mkdirs()) {
                Log.d(Config.LOGTAG, "created backup directory ${directory.absolutePath}")
            }
            outputStream = FileOutputStream(file)
            location = Uri.fromFile(file)
        } else {
            val tree = DocumentFile.fromTreeUri(context, backupLocation)
                ?: throw IOException(
                    String.format("DocumentFile.fromTreeUri returned null for %s", backupLocation)
                )
            val file = tree.createFile(MIME_TYPE, filename)
                ?: throw IOException(
                    String.format("Could not create %s in %s", filename, backupLocation)
                )
            location = file.uri
            outputStream = context.contentResolver.openOutputStream(location)!!
        }
        val dataOutputStream = DataOutputStream(outputStream)
        backupFileHeader.write(dataOutputStream)
        dataOutputStream.flush()

        val cipher = if (Compatibility.twentyEight())
            Cipher.getInstance(CIPHER_MODE)
        else
            Cipher.getInstance(CIPHER_MODE, PROVIDER)
        val key = getKey(password, salt)
        val keySpec = SecretKeySpec(key, KEY_TYPE)
        val ivSpec = IvParameterSpec(IV)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val cipherOutputStream = CipherOutputStream(outputStream, cipher)

        val gzipOutputStream = GZIPOutputStream(cipherOutputStream)
        val jsonWriter = JsonWriter(OutputStreamWriter(gzipOutputStream, StandardCharsets.UTF_8))
        jsonWriter.beginArray()
        val db = database.readableDatabase
        val uuid = account.getUuid()!!
        accountExport(db, uuid, jsonWriter)
        simpleExport(db, Conversation.TABLENAME, Conversation.ACCOUNT, uuid, jsonWriter)
        messageExport(db, uuid, location, jsonWriter, progress)
        for (table in Arrays.asList(
            SQLiteAxolotlStore.PREKEY_TABLENAME,
            SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
            SQLiteAxolotlStore.SESSION_TABLENAME,
            SQLiteAxolotlStore.IDENTITIES_TABLENAME
        )) {
            throwIfWorkStopped(location)
            simpleExport(db, table, SQLiteAxolotlStore.ACCOUNT, uuid, jsonWriter)
        }
        jsonWriter.endArray()
        jsonWriter.flush()
        jsonWriter.close()
        val path = location.path
        if ("file".equals(location.scheme, ignoreCase = true) && path != null) {
            mediaScannerScanFile(File(path))
        }
        Log.d(Config.LOGTAG, "written backup to $location")
        return location
    }

    private fun getNotification(): NotificationCompat.Builder {
        val context = applicationContext
        val notification = NotificationCompat.Builder(context, "backup")
        notification
            .setContentTitle(context.getString(R.string.notification_create_backup_title))
            .setSmallIcon(R.drawable.ic_archive_24dp)
            .setProgress(1, 0, false)
        notification.setOngoing(true)
        notification.setLocalOnly(true)
        return notification
    }

    @Throws(WorkStoppedException::class)
    private fun throwIfWorkStopped(location: Uri) {
        if (isStopped) {
            if ("file".equals(location.scheme, ignoreCase = true)) {
                val file = File(location.path)
                if (file.delete()) {
                    Log.d(Config.LOGTAG, "deleted ${file.absolutePath}")
                }
            } else {
                val documentFile = DocumentFile.fromSingleUri(applicationContext, location)
                if (documentFile != null && documentFile.delete()) {
                    Log.d(Config.LOGTAG, "deleted $location")
                }
            }
            throw WorkStoppedException()
        }
    }

    private fun mediaScannerScanFile(file: File) {
        val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        intent.data = Uri.fromFile(file)
        applicationContext.sendBroadcast(intent)
    }

    @Throws(IOException::class)
    private fun messageExport(
        db: SQLiteDatabase,
        uuid: String,
        location: Uri,
        writer: JsonWriter,
        progress: Progress
    ) {
        val notificationManager =
            applicationContext.getSystemService(NotificationManager::class.java)
        db.rawQuery(
            "select messages.* from messages join conversations on conversations.uuid=messages.conversationUuid where conversations.accountUuid=?",
            arrayOf(uuid)
        ).use { cursor ->
            val size = cursor.count
            Log.d(Config.LOGTAG, "exporting $size messages for account $uuid")
            var lastUpdate: Long = 0
            var i = 0
            var p = Int.MIN_VALUE
            while (cursor.moveToNext()) {
                throwIfWorkStopped(location)
                writer.beginObject()
                writer.name("table")
                writer.value(Message.TABLENAME)
                writer.name("values")
                writer.beginObject()
                for (j in 0 until cursor.columnCount) {
                    val name = cursor.getColumnName(j)
                    writer.name(name)
                    val value = cursor.getString(j)
                    writer.value(value)
                }
                writer.endObject()
                writer.endObject()
                val percentage = i * 100 / size
                if (p < percentage && (SystemClock.elapsedRealtime() - lastUpdate) > 2_000) {
                    p = percentage
                    lastUpdate = SystemClock.elapsedRealtime()
                    notificationManager.notify(NOTIFICATION_ID, progress.build(p))
                }
                i++
                if (i % 500 == 0) {
                    Log.d(Config.LOGTAG, "flushing writer after $i messages")
                    writer.flush()
                }
            }
        }
    }

    private fun notifySuccess(locations: List<Uri>) {
        val context = applicationContext
        val appSettings = AppSettings(context)
        val path = appSettings.backupLocationAsPath
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
        val uris = ArrayList<Uri>()
        for (uri in locations) {
            if ("file".equals(uri.scheme, ignoreCase = true)) {
                uris.add(FileBackend.getUriForFile(context, File(uri.path)))
            } else {
                uris.add(uri)
            }
        }
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.type = MIME_TYPE
        val chooser = Intent.createChooser(intent, context.getString(R.string.share_backup_files))
        val shareFilesIntent = PendingIntent.getActivity(context, 190, chooser, PENDING_INTENT_FLAGS)

        val builder = NotificationCompat.Builder(context, "backup")
        builder.setContentTitle(context.getString(R.string.notification_backup_created_title))
            .setContentText(
                context.getString(R.string.notification_backup_created_subtitle, path)
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        context.getString(
                            R.string.notification_backup_created_subtitle,
                            path
                        )
                    )
            )
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_archive_24dp)

        builder.addAction(
            R.drawable.ic_share_24dp,
            context.getString(R.string.share_backup_files),
            shareFilesIntent
        )
        builder.setLocalOnly(true)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(BACKUP_CREATED_NOTIFICATION_ID, builder.build())
    }

    private class Progress(
        private val notification: NotificationCompat.Builder,
        private val max: Int,
        private val count: Int
    ) {
        fun build(percentage: Int): Notification {
            notification.setProgress(max * 100, count * 100 + percentage, false)
            return notification.build()
        }
    }

    private class WorkStoppedException : Exception()

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.US)

        private const val KEY_TYPE = "AES"
        private const val CIPHER_MODE = "AES/GCM/NoPadding"
        private const val PROVIDER = "BC"

        const val MIME_TYPE = "application/vnd.conversations.backup"

        private const val NOTIFICATION_ID = 19
        private const val BACKUP_CREATED_NOTIFICATION_ID = 23

        private val PENDING_INTENT_FLAGS = if (s())
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else
            PendingIntent.FLAG_UPDATE_CURRENT

        @JvmStatic
        @Throws(InvalidKeySpecException::class)
        fun getKey(password: String, salt: ByteArray): ByteArray {
            val factory = try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            } catch (e: NoSuchAlgorithmException) {
                throw IllegalStateException(e)
            }
            return factory.generateSecret(PBEKeySpec(password.toCharArray(), salt, 1024, 128))
                .encoded
        }

        @Throws(IOException::class)
        private fun accountExport(db: SQLiteDatabase, uuid: String, writer: JsonWriter) {
            db.query(
                Account.TABLENAME,
                null,
                AbstractEntity.UUID + "=?",
                arrayOf(uuid),
                null,
                null,
                null
            ).use { accountCursor ->
                while (accountCursor != null && accountCursor.moveToNext()) {
                    writer.beginObject()
                    writer.name("table")
                    writer.value(Account.TABLENAME)
                    writer.name("values")
                    writer.beginObject()
                    for (i in 0 until accountCursor.columnCount) {
                        val name = accountCursor.getColumnName(i)
                        writer.name(name)
                        val value = accountCursor.getString(i)
                        if (value == null
                            || Account.ROSTERVERSION == accountCursor.getColumnName(i)
                        ) {
                            writer.nullValue()
                        } else if (Account.OPTIONS == accountCursor.getColumnName(i)
                            && value.matches(Regex("\\d+"))
                        ) {
                            var intValue = value.toInt()
                            if (QuickConversationsService.isConversations()) {
                                intValue = intValue or (1 shl Account.OPTION_DISABLED)
                            }
                            writer.value(intValue)
                        } else {
                            writer.value(value)
                        }
                    }
                    writer.endObject()
                    writer.endObject()
                }
            }
        }

        @Throws(IOException::class)
        private fun simpleExport(
            db: SQLiteDatabase,
            table: String,
            column: String,
            uuid: String,
            writer: JsonWriter
        ) {
            db.query(table, null, "$column=?", arrayOf(uuid), null, null, null).use { cursor ->
                while (cursor != null && cursor.moveToNext()) {
                    writer.beginObject()
                    writer.name("table")
                    writer.value(table)
                    writer.name("values")
                    writer.beginObject()
                    for (i in 0 until cursor.columnCount) {
                        val name = cursor.getColumnName(i)
                        writer.name(name)
                        val value = cursor.getString(i)
                        writer.value(value)
                    }
                    writer.endObject()
                    writer.endObject()
                }
            }
        }
    }
}
