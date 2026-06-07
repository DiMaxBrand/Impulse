package eu.siacs.conversations.http

import android.content.Context
import androidx.annotation.NonNull
import com.google.common.base.MoreObjects
import com.google.common.base.Strings
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.entities.Account
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import java.lang.reflect.Type
import java.time.Instant
import java.util.Arrays
import java.util.Locale

class ServiceOutageStatus(
    private val planned: Boolean,
    private val beginning: Instant?,
    @SerializedName("expected_end") private val expectedEnd: Instant?,
    private val message: Map<String, String>?
) {

    fun isNow(): Boolean {
        val now = Instant.now()
        val hasDefault = message != null && message.containsKey("default")
        return hasDefault
                && beginning != null
                && expectedEnd != null
                && beginning.isBefore(now)
                && expectedEnd.isAfter(now)
    }

    fun isPlanned(): Boolean = planned

    fun getExpectedEnd(): Long {
        return expectedEnd?.toEpochMilli() ?: 0L
    }

    fun getMessage(): String? {
        val translated = message?.get(Locale.getDefault().language)
        return if (Strings.isNullOrEmpty(translated)) message?.get("default") else translated
    }

    @NonNull
    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .add("planned", planned)
            .add("beginning", beginning)
            .add("expectedEnd", expectedEnd)
            .add("message", message)
            .toString()
    }

    private class InstantDeserializer : JsonDeserializer<Instant> {
        @Throws(JsonParseException::class)
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): Instant {
            return Instant.parse(json.asString)
        }
    }

    companion object {
        private val SERVICE_OUTAGE_STATE: Collection<Account.State> = Arrays.asList(
            Account.State.CONNECTION_TIMEOUT,
            Account.State.SERVER_NOT_FOUND,
            Account.State.STREAM_OPENING_ERROR
        )

        @JvmStatic
        fun fetch(context: Context, url: HttpUrl): ListenableFuture<ServiceOutageStatus> {
            val appSettings = AppSettings(context)
            val builder = HttpConnectionManager.okHttpClient(context).newBuilder()
            if (appSettings.isUseTor()) {
                builder.proxy(HttpConnectionManager.getProxy())
            }
            val client = builder.build()
            val future: SettableFuture<ServiceOutageStatus> = SettableFuture.create()
            val request = Request.Builder().url(url).build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(@NonNull call: Call, @NonNull e: IOException) {
                    future.setException(e)
                }

                override fun onResponse(@NonNull call: Call, @NonNull response: Response) {
                    response.body.use { body ->
                        if (!response.isSuccessful || body == null) {
                            future.setException(
                                IOException("unexpected server response (${response.code})")
                            )
                            return
                        }
                        try {
                            val gson = GsonBuilder()
                                .registerTypeAdapter(Instant::class.java, InstantDeserializer())
                                .create()
                            future.set(gson.fromJson(body.string(), ServiceOutageStatus::class.java))
                        } catch (e: Exception) {
                            future.setException(e)
                        }
                    }
                }
            })

            return future
        }

        @JvmStatic
        fun isPossibleOutage(state: Account.State): Boolean {
            return SERVICE_OUTAGE_STATE.contains(state)
        }
    }
}
