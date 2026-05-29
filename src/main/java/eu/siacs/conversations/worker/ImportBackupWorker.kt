package eu.siacs.conversations.worker

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.common.base.Stopwatch
import com.google.common.base.Strings
import com.google.common.collect.ImmutableList
import com.google.common.io.CountingInputStream
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import eu.siacs.conversations.Config
import eu.siacs.conversations.Conversations
import eu.siacs.conversations.R
import eu.siacs.conversations.crypto.axolotl.SQLiteAxolotlStore
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.persistance.DatabaseBackend
import eu.siacs.conversations.services.QuickConversationsService
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.utils.AccountUtils
import eu.siacs.conversations.utils.BackupFileHeader
import eu.siacs.conversations.utils.Compatibility.s
import eu.siacs.conversations.xmpp.Jid
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.spec.InvalidKeySpecException
import java.util.Arrays
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream
import java.util.zip.ZipException
import javax.crypto.BadPaddingException
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.io.CipherInputStream
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.json.JSONException
import org.json.JSONObject

class ImportBackupWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    private val password: String?
    private val uri: Uri
    private val includeOmemo: Boolean

    init {
        val inputData = workerParams.inputData
        this.password = inputData.getString(DATA_KEY_PASSWORD)
        this.uri = Uri.parse(inputData.getString(DATA_KEY_URI))
        this.includeOmemo = inputData.getBoolean(DATA_KEY_INCLUDE_OMEMO, true)
    }

    @NonNull
    override fun doWork(): Result {
        setForegroundAsync(getForegroundInfo())
        val result: Result
        try {
            result = importBackup(this.uri, this.password)
        } catch (e: FileNotFoundException) {
            return failure(Reason.FILE_NOT_FOUND)
        } catch (e: Exception) {
            Log.d(Config.LOGTAG, "error restoring backup $uri", e)
            val throwable = e.cause
            return if (throwable is BadPaddingException || e is ZipException) {
                failure(Reason.DECRYPTION_FAILED)
            } else {
                failure(Reason.GENERIC)
            }
        } finally {
            applicationContext.getSystemService(NotificationManager::class.java)
                .cancel(NOTIFICATION_ID)
        }
        return result
    }

    @NonNull
    override fun getForegroundInfo(): ForegroundInfo {
        val notification = createImportBackupNotification(1, 0)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    @Throws(IOException::class, InvalidKeySpecException::class)
    private fun importBackup(uri: Uri, password: String?): Result {
        val context = applicationContext
        val database = DatabaseBackend.getInstance(context)
        Log.d(Config.LOGTAG, "importing backup from $uri")
        val stopwatch = Stopwatch.createStarted()
        val db = database.writableDatabase
        val inputStream: InputStream?
        val path = uri.path
        val fileSize: Long
        if ("file" == uri.scheme && path != null) {
            val file = File(path)
            inputStream = FileInputStream(file)
            fileSize = file.length()
        } else {
            val returnCursor = context.contentResolver.query(uri, null, null, null, null)
            if (returnCursor == null) {
                fileSize = 0
            } else {
                returnCursor.moveToFirst()
                fileSize = returnCursor.getLong(
                    returnCursor.getColumnIndexOrThrow(OpenableColumns.SIZE)
                )
                returnCursor.close()
            }
            inputStream = context.contentResolver.openInputStream(uri)
        }
        if (inputStream == null) {
            return failure(Reason.FILE_NOT_FOUND)
        }
        val countingInputStream = CountingInputStream(inputStream)
        val dataInputStream = DataInputStream(countingInputStream)
        val backupFileHeader = BackupFileHeader.read(dataInputStream)
        Log.d(Config.LOGTAG, backupFileHeader.toString())

        val accounts = database.accountAddresses

        if (QuickConversationsService.isQuicksy() && accounts.isNotEmpty()) {
            return failure(Reason.ACCOUNT_ALREADY_EXISTS)
        }

        if (accounts.contains(backupFileHeader.jid)) {
            return failure(Reason.ACCOUNT_ALREADY_EXISTS)
        }

        val key = ExportBackupWorker.getKey(password!!, backupFileHeader.salt)

        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(
            false,
            AEADParameters(KeyParameter(key), 128, backupFileHeader.iv)
        )
        val cipherInputStream = CipherInputStream(countingInputStream, cipher)

        val gzipInputStream = GZIPInputStream(cipherInputStream)
        val reader = BufferedReader(InputStreamReader(gzipInputStream, StandardCharsets.UTF_8))
        val jsonReader = JsonReader(reader)
        if (jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
            jsonReader.beginArray()
        } else {
            throw IllegalStateException("Backup file did not begin with array")
        }
        db.beginTransaction()
        while (jsonReader.hasNext()) {
            if (jsonReader.peek() == JsonToken.BEGIN_OBJECT) {
                importRow(db, jsonReader, backupFileHeader.jid, password)
            } else if (jsonReader.peek() == JsonToken.END_ARRAY) {
                jsonReader.endArray()
                continue
            }
            updateImportBackupNotification(fileSize, countingInputStream.count)
        }
        db.setTransactionSuccessful()
        db.endTransaction()
        val jid = backupFileHeader.jid
        val countCursor = db.rawQuery(
            "select count(messages.uuid) from messages join conversations on conversations.uuid=messages.conversationUuid join accounts on conversations.accountUuid=accounts.uuid where accounts.username=? and accounts.server=?",
            arrayOf(jid.local, jid.domain.toString())
        )
        countCursor.moveToFirst()
        val count = countCursor.getInt(0)
        Log.d(Config.LOGTAG, String.format("restored %d messages in %s", count, stopwatch.stop()))
        countCursor.close()
        Conversations.getInstance(applicationContext).resetAccounts()
        stopBackgroundService()
        notifySuccess()
        return Result.success()
    }

    @Throws(IOException::class)
    private fun importRow(
        db: SQLiteDatabase,
        jsonReader: JsonReader,
        account: Jid,
        passphrase: String
    ) {
        jsonReader.beginObject()
        val firstParameter = jsonReader.nextName()
        if (firstParameter != "table") {
            throw IllegalStateException("Expected key 'table'")
        }
        val table = jsonReader.nextString()
        if (!TABLE_ALLOW_LIST.contains(table)) {
            throw IOException(String.format("%s is not recognized for import", table))
        }
        val contentValues = ContentValues()
        val secondParameter = jsonReader.nextName()
        if (secondParameter != "values") {
            throw IllegalStateException("Expected key 'values'")
        }
        jsonReader.beginObject()
        while (jsonReader.peek() != JsonToken.END_OBJECT) {
            val name = jsonReader.nextName()
            if (COLUMN_PATTERN.matcher(name).matches()) {
                if (jsonReader.peek() == JsonToken.NULL) {
                    jsonReader.nextNull()
                    contentValues.putNull(name)
                } else if (jsonReader.peek() == JsonToken.NUMBER) {
                    contentValues.put(name, jsonReader.nextLong())
                } else {
                    contentValues.put(name, jsonReader.nextString())
                }
            } else {
                throw IOException(String.format("Unexpected column name %s", name))
            }
        }
        jsonReader.endObject()
        jsonReader.endObject()
        if (Account.TABLENAME == table) {
            val jid = Jid.of(
                contentValues.getAsString(Account.USERNAME),
                contentValues.getAsString(Account.SERVER),
                null
            )
            val password = contentValues.getAsString(Account.PASSWORD)
            if (QuickConversationsService.isQuicksy()) {
                if (jid.domain != Config.QUICKSY_DOMAIN) {
                    throw IOException("Trying to restore non Quicksy account on Quicksy")
                }
            }
            if (jid == account && passphrase == password) {
                Log.d(Config.LOGTAG, "jid and password from backup header had matching row")
            } else {
                throw IOException("jid or password in table did not match backup")
            }
            val keys = Account.parseKeys(contentValues.getAsString(Account.KEYS))
            val deviceId = keys.optString(SQLiteAxolotlStore.JSONKEY_REGISTRATION_ID)
            val importReadyKeys = JSONObject()
            if (!Strings.isNullOrEmpty(deviceId) && this.includeOmemo) {
                try {
                    importReadyKeys.put(SQLiteAxolotlStore.JSONKEY_REGISTRATION_ID, deviceId)
                } catch (e: JSONException) {
                    Log.e(Config.LOGTAG, "error writing omemo registration id", e)
                }
            }
            contentValues.put(Account.KEYS, importReadyKeys.toString())
        }
        if (this.includeOmemo) {
            db.insert(table, null, contentValues)
        } else {
            if (OMEMO_TABLE_LIST.contains(table)) {
                if (SQLiteAxolotlStore.IDENTITIES_TABLENAME == table
                    && contentValues.getAsInteger(SQLiteAxolotlStore.OWN) == 0
                ) {
                    db.insert(table, null, contentValues)
                } else {
                    Log.d(Config.LOGTAG, "skipping over omemo key material in table $table")
                }
            } else {
                db.insert(table, null, contentValues)
            }
        }
    }

    private fun stopBackgroundService() {
        val intent = Intent(applicationContext, XmppConnectionService::class.java)
        applicationContext.stopService(intent)
    }

    private fun updateImportBackupNotification(total: Long, current: Long) {
        val max: Int
        val progress: Int
        if (total == 0L) {
            max = 1
            progress = 0
        } else {
            max = 100
            progress = (current * 100 / total).toInt()
        }
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, createImportBackupNotification(max, progress))
    }

    private fun createImportBackupNotification(max: Int, progress: Int): Notification {
        val context = applicationContext
        val builder = NotificationCompat.Builder(applicationContext, "backup")
        builder.setContentTitle(context.getString(R.string.restoring_backup))
            .setSmallIcon(R.drawable.ic_unarchive_24dp)
            .setProgress(max, progress, max == 1 && progress == 0)
        return builder.build()
    }

    private fun notifySuccess() {
        val context = applicationContext
        val builder = NotificationCompat.Builder(context, "backup")
        builder.setContentTitle(context.getString(R.string.notification_restored_backup_title))
            .setContentText(context.getString(R.string.notification_restored_backup_subtitle))
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_unarchive_24dp)
        if (QuickConversationsService.isConversations()
            && AccountUtils.MANAGE_ACCOUNT_ACTIVITY != null
        ) {
            builder.setContentText(
                context.getString(R.string.notification_restored_backup_subtitle)
            )
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    145,
                    Intent(context, AccountUtils.MANAGE_ACCOUNT_ACTIVITY),
                    if (s())
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    else
                        PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID + 2, builder.build())
    }

    enum class Reason {
        ACCOUNT_ALREADY_EXISTS,
        DECRYPTION_FAILED,
        FILE_NOT_FOUND,
        GENERIC;

        companion object {
            @JvmStatic
            fun valueOfOrGeneric(value: String?): Reason {
                if (Strings.isNullOrEmpty(value)) return GENERIC
                return try {
                    valueOf(value!!)
                } catch (e: IllegalArgumentException) {
                    GENERIC
                }
            }
        }
    }

    companion object {
        const val TAG_IMPORT_BACKUP = "tag-import-backup"

        private const val DATA_KEY_PASSWORD = "password"
        private const val DATA_KEY_URI = "uri"
        private const val DATA_KEY_INCLUDE_OMEMO = "omemo"

        private val OMEMO_TABLE_LIST: Collection<String> = Arrays.asList(
            SQLiteAxolotlStore.PREKEY_TABLENAME,
            SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
            SQLiteAxolotlStore.SESSION_TABLENAME,
            SQLiteAxolotlStore.IDENTITIES_TABLENAME
        )

        private val TABLE_ALLOW_LIST: List<String> = ImmutableList.Builder<String>()
            .add(Account.TABLENAME, Conversation.TABLENAME, Message.TABLENAME)
            .addAll(OMEMO_TABLE_LIST)
            .build()

        private val COLUMN_PATTERN: Pattern = Pattern.compile("^[a-zA-Z_]+$")

        private const val NOTIFICATION_ID = 21

        @JvmStatic
        fun data(password: String, uri: Uri, includeOmemo: Boolean): Data {
            return Data.Builder()
                .putString(DATA_KEY_PASSWORD, password)
                .putString(DATA_KEY_URI, uri.toString())
                .putBoolean(DATA_KEY_INCLUDE_OMEMO, includeOmemo)
                .build()
        }

        private fun failure(reason: Reason): Result {
            return Result.failure(
                Data.Builder().putString("reason", reason.toString()).build()
            )
        }
    }
}
