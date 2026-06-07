package eu.siacs.conversations.xmpp.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.Patterns
import androidx.annotation.NonNull
import com.google.common.base.Optional
import com.google.common.base.Strings
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Iterables
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.IqErrorException
import im.conversations.android.xmpp.model.data.Data
import im.conversations.android.xmpp.model.error.Condition
import im.conversations.android.xmpp.model.oob.OutOfBandData
import im.conversations.android.xmpp.model.pars.PreAuth
import im.conversations.android.xmpp.model.register.Instructions
import im.conversations.android.xmpp.model.register.Password
import im.conversations.android.xmpp.model.register.Register
import im.conversations.android.xmpp.model.register.Remove
import im.conversations.android.xmpp.model.register.Username
import im.conversations.android.xmpp.model.stanza.Iq
import java.util.Arrays
import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class RegistrationManager(context: Context, connection: XmppConnection) :
    AbstractManager(context, connection) {

    fun setPassword(password: String): ListenableFuture<Void?> {
        val account = getAccount()
        val iq = Iq(Iq.Type.SET)
        iq.setTo(account.getJid().getDomain())
        val register = iq.addExtension(Register())
        register.addUsername(account.getJid().getLocal())
        register.addPassword(password)
        return Futures.transform(
            connection.sendIqPacket(iq),
            { _: Iq? ->
                account.setPassword(password)
                account.setOption(Account.OPTION_MAGIC_CREATE, false)
                getDatabase().updateAccount(account)
                null
            },
            MoreExecutors.directExecutor()
        )
    }

    fun register(): ListenableFuture<Void?> {
        val account = getAccount()
        val iq = Iq(Iq.Type.SET)
        iq.setTo(account.getJid().getDomain())
        val register = iq.addExtension(Register())
        register.addUsername(account.getJid().getLocal())
        register.addPassword(account.getPassword())
        val future: ListenableFuture<Void?> =
            Futures.transform(
                connection.sendIqPacket(iq, true),
                { _: Iq? -> null },
                MoreExecutors.directExecutor()
            )
        return Futures.catchingAsync(
            future,
            IqErrorException::class.java,
            { ex ->
                Futures.immediateFailedFuture(RegistrationFailedException(ex!!.getResponse()))
            },
            MoreExecutors.directExecutor()
        )
    }

    fun register(data: Data, ocr: String): ListenableFuture<Void?> {
        val account = getAccount()
        val submission =
            data.submit(
                ImmutableMap.of<String, Any>(
                    "username",
                    account.getJid().getLocal(),
                    "password",
                    account.getPassword(),
                    "ocr",
                    ocr
                )
            )
        val iq = Iq(Iq.Type.SET)
        val register = iq.addExtension(Register())
        register.addExtension(submission)
        val future: ListenableFuture<Void?> =
            Futures.transform(
                connection.sendIqPacket(iq, true),
                { _: Iq? -> null },
                MoreExecutors.directExecutor()
            )
        return Futures.catchingAsync(
            future,
            IqErrorException::class.java,
            { ex ->
                Futures.immediateFailedFuture(RegistrationFailedException(ex!!.getResponse()))
            },
            MoreExecutors.directExecutor()
        )
    }

    fun unregister(): ListenableFuture<Void?> {
        val account = getAccount()
        val iq = Iq(Iq.Type.SET)
        iq.setTo(account.getJid().getDomain())
        val register = iq.addExtension(Register())
        register.addExtension(Remove())
        return Futures.transform(
            connection.sendIqPacket(iq),
            { _: Iq? -> null },
            MoreExecutors.directExecutor()
        )
    }

    fun getRegistration(): ListenableFuture<Registration> {
        val account = getAccount()
        val iq = Iq(Iq.Type.GET)
        iq.setTo(account.getDomain())
        iq.addExtension(Register())
        val future = connection.sendIqPacket(iq, true)
        return Futures.transformAsync(
            future,
            { result ->
                val register =
                    result!!.getExtension(Register::class.java)
                        ?: throw IllegalStateException("Server did not include register in response")
                if (register.hasExtension(Username::class.java) &&
                        register.hasExtension(Password::class.java)) {
                    return@transformAsync Futures.immediateFuture<Registration>(
                        SimpleRegistration()
                    )
                }
                // find bits of binary and get captcha from there
                val data = register.getExtension(Data::class.java)
                // note that the captcha namespace is incorrect here. That namespace is only
                // used in message challenges. ejabberd uses the incorrect namespace though
                if (data != null &&
                        Arrays.asList(Namespace.REGISTER, Namespace.CAPTCHA)
                            .contains(data.getFormType())) {
                    return@transformAsync getExtendedRegistration(register, data)
                }
                val oob = register.getExtension(OutOfBandData::class.java)
                val instructions = register.getExtension(Instructions::class.java)
                val instructionsText = instructions?.getContent()
                val redirectUrl = oob?.getURL()
                if (redirectUrl != null) {
                    return@transformAsync Futures.immediateFuture(
                        RedirectRegistration.ifValid(redirectUrl)
                    )
                }
                if (instructionsText != null) {
                    val matcher = Patterns.WEB_URL.matcher(instructionsText)
                    if (matcher.find()) {
                        val instructionsUrl =
                            instructionsText.substring(matcher.start(), matcher.end())
                        return@transformAsync Futures.immediateFuture(
                            RedirectRegistration.ifValid(instructionsUrl)
                        )
                    }
                }
                throw IllegalStateException("No supported registration method found")
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun getExtendedRegistration(
        register: Register,
        data: Data
    ): ListenableFuture<Registration> {
        val ocr =
            data.getFieldByName("ocr")
                ?: throw IllegalArgumentException("Missing OCR form field")
        val ocrMedia =
            ocr.getMedia() ?: throw IllegalArgumentException("OCR form field missing media")
        val uris = ocrMedia.getUris()
        val bobUri = Iterables.find(uris, { u -> "cid" == u.getScheme() }, null)
        val bob: Optional<im.conversations.android.xmpp.model.bob.Data>
        bob =
            if (bobUri != null) {
                im.conversations.android.xmpp.model.bob.Data.get(register, bobUri.getPath())
            } else {
                Optional.absent()
            }
        if (bob.isPresent) {
            val bytes = bob.get().asBytes()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            return Futures.immediateFuture(ExtendedRegistration(bitmap, data))
        }
        val captchaFallbackUrl =
            data.getValue("captcha-fallback-url")
                ?: throw IllegalStateException("No captcha fallback URL provided")
        val captchFallbackHttpUrl = captchaFallbackUrl.toHttpUrlOrNull()
        Log.d(Config.LOGTAG, "fallback url: $captchFallbackHttpUrl")
        throw IllegalStateException("Not implemented")
    }

    fun getRegistration(token: String): ListenableFuture<Registration> {
        val preAuthentication = sendPreAuthentication(token)
        val caught =
            Futures.catchingAsync(
                preAuthentication,
                IqErrorException::class.java,
                { ex ->
                    Log.d(Config.LOGTAG, "could not pre authenticate registration", ex)
                    val error = ex!!.getError()
                    val condition = error?.getCondition()
                    if (condition is Condition.ItemNotFound) {
                        Futures.immediateFailedFuture(InvalidTokenException(ex.getResponse()))
                    } else {
                        Futures.immediateFuture(ex)
                    }
                },
                MoreExecutors.directExecutor()
            )
        return Futures.transformAsync(
            caught,
            { _ -> getRegistration() },
            MoreExecutors.directExecutor()
        )
    }

    fun sendPreAuthentication(token: String): ListenableFuture<Void?> {
        val account = getAccount()
        val iq = Iq(Iq.Type.SET)
        iq.setTo(account.getJid().getDomain())
        val preAuthentication = iq.addExtension(PreAuth())
        preAuthentication.setToken(token)
        val future = connection.sendIqPacket(iq, true)
        return Futures.transform(future, { _: Iq? -> null }, MoreExecutors.directExecutor())
    }

    fun hasFeature(): Boolean =
        getManager(DiscoManager::class.java).hasServerFeature(Namespace.REGISTER)

    abstract class Registration

    // only requires Username + Password
    class SimpleRegistration : Registration()

    // Captcha as shown here: https://xmpp.org/extensions/xep-0158.html#register
    class ExtendedRegistration(val captcha: Bitmap, val data: Data) : Registration()

    // Redirection as shown here: https://xmpp.org/extensions/xep-0077.html#redirect
    class RedirectRegistration private constructor(@NonNull val url: HttpUrl) : Registration() {
        @NonNull
        fun getURL(): HttpUrl = this.url

        companion object {
            @JvmStatic
            fun ifValid(url: String): RedirectRegistration {
                val httpUrl = url.toHttpUrlOrNull()
                if (httpUrl != null && httpUrl.isHttps) {
                    return RedirectRegistration(httpUrl)
                }
                throw IllegalStateException(
                    "A URL found the registration instructions is not valid"
                )
            }
        }
    }

    class InvalidTokenException(response: Iq) : IqErrorException(response)

    class RegistrationFailedException(response: Iq) : IqErrorException(response) {
        fun asAccountState(): Account.State {
            val error = getError() ?: return Account.State.REGISTRATION_FAILED
            val condition = error.getCondition()
            val text = Strings.nullToEmpty(error.getTextAsString())
            return when (condition) {
                is Condition.Conflict -> Account.State.REGISTRATION_CONFLICT
                is Condition.ResourceConstraint -> Account.State.REGISTRATION_PLEASE_WAIT
                is Condition.NotAcceptable -> {
                    when {
                        text.lowercase(Locale.ROOT).contains("password") ->
                            Account.State.REGISTRATION_PASSWORD_TOO_WEAK
                        text.lowercase(Locale.ROOT).contains("captcha") ->
                            Account.State.REGISTRATION_INVALID_CAPTCHA
                        else -> Account.State.REGISTRATION_FAILED
                    }
                }
                is Condition.NotAllowed
                    if text.lowercase(Locale.ROOT).contains("captcha") ->
                    Account.State.REGISTRATION_INVALID_CAPTCHA
                else -> Account.State.REGISTRATION_FAILED
            }
        }
    }
}
