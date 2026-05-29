package eu.siacs.conversations.xmpp.manager

import android.content.Context
import android.util.Log
import com.google.common.base.Optional
import com.google.common.base.Preconditions
import com.google.common.base.Strings
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Maps
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import de.gultsch.common.MiniUri
import eu.siacs.conversations.Config
import eu.siacs.conversations.utils.EasyOnboardingInvite
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.commands.Command
import im.conversations.android.xmpp.model.data.Data
import java.time.Duration
import java.util.Arrays
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class EasyOnboardingManager(context: Context, connection: XmppConnection) :
    AbstractManager(context, connection) {

    companion object {
        private val INVITE_URI_ALLOWED_PARAMETERS: Collection<String> =
            Arrays.asList(
                MiniUri.Xmpp.ACTION_ROSTER,
                MiniUri.Xmpp.PARAMETER_PRE_AUTH,
                MiniUri.Xmpp.PARAMETER_IBR
            )
        private val INVITE_TIMEOUT: Duration = Duration.ofSeconds(2)
    }

    fun invitationUrl(): ListenableFuture<MiniUri.Http> {
        val optional = getAddressForInviteCommand()
        if (optional.isPresent) {
            val future =
                this.getManager(AdHocCommandsManager::class.java)
                    .commandComplete(optional.get(), Namespace.EASY_ONBOARDING_INVITE)
            val asHttpUri: ListenableFuture<MiniUri.Http> =
                Futures.transform(
                    future,
                    { data ->
                        Preconditions.checkNotNull(data)
                        val landingUri = MiniUri.getOrNull(data!!.getValue("landing-url"))
                        if (landingUri is MiniUri.Http) {
                            landingUri
                        } else {
                            Log.w(Config.LOGTAG, "server provided landing url not found")
                            validatedXmppUri(data, false).asInvitationUri()
                        }
                    },
                    MoreExecutors.directExecutor()
                )
            return Futures.catching(
                Futures.withTimeout(asHttpUri, INVITE_TIMEOUT, FUTURE_TIMEOUT_EXECUTOR),
                Throwable::class.java,
                { t ->
                    Log.d(Config.LOGTAG, "could not retrieve easy invite uri", t)
                    getAccount().getShareableUri().asInvitationUri()
                },
                MoreExecutors.directExecutor()
            )
        } else {
            return Futures.immediateFuture(getAccount().getShareableUri().asInvitationUri())
        }
    }

    fun inviteOrFallback(): ListenableFuture<MiniUri.Xmpp> {
        if (hasInviteFeature()) {
            val inviteFuture = invite()
            val timeoutFuture =
                Futures.withTimeout(inviteFuture, INVITE_TIMEOUT, FUTURE_TIMEOUT_EXECUTOR)
            return Futures.catching(
                timeoutFuture,
                Throwable::class.java,
                { t ->
                    Log.d(Config.LOGTAG, "could not retrieve easy invite uri", t)
                    getAccount().getShareableUri()
                },
                MoreExecutors.directExecutor()
            )
        } else {
            return Futures.immediateFuture(getAccount().getShareableUri())
        }
    }

    private fun invite(): ListenableFuture<MiniUri.Xmpp> {
        val optional = getAddressForInviteCommand()
        val address: Jid
        if (optional.isPresent) {
            address = optional.get()
        } else {
            return Futures.immediateFailedFuture(
                UnsupportedOperationException(
                    "Server does not support generating easy onboarding invites"
                )
            )
        }
        val future =
            this.getManager(AdHocCommandsManager::class.java)
                .commandComplete(address, Namespace.EASY_ONBOARDING_INVITE)
        return Futures.transform(
            future,
            { data ->
                Preconditions.checkNotNull(data)
                validatedXmppUri(data!!, true)
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun validatedXmppUri(data: Data, includeFingerprints: Boolean): MiniUri.Xmpp {
        val rawUri = MiniUri.getOrNull(data.getValue("uri"))
        val uri: MiniUri.Xmpp =
            if (rawUri is MiniUri.Xmpp) rawUri
            else throw IllegalStateException("Did not find valid XMPP uri")
        val account = getAccount().getJid().asBareJid()
        if (uri.isAddress && uri.asJid() == account) {
            val builder = ImmutableMultimap.Builder<String, String>()
            for (entry in
                Maps.filterKeys(uri.getParameterFlat()) {
                    INVITE_URI_ALLOWED_PARAMETERS.contains(it)
                }
                    .entries) {
                builder.put(entry)
            }
            if (includeFingerprints) {
                for (entry in getAccount().getFingerprints().entries) {
                    builder.putAll(entry.key, entry.value)
                }
            }
            return MiniUri.Xmpp(account, builder.build().asMap())
        } else {
            throw IllegalStateException("URI in invite response did not match our account")
        }
    }

    fun createAccount(): ListenableFuture<EasyOnboardingInvite> {
        val optional = getAddressForInviteCommand()
        val address: Jid
        if (optional.isPresent) {
            address = optional.get()
        } else {
            return Futures.immediateFailedFuture(
                UnsupportedOperationException("Server does not support account creation")
            )
        }
        val future =
            this.getManager(AdHocCommandsManager::class.java)
                .command(address, Namespace.EASY_ONBOARDING_CREATE_ACCOUNT)
        return Futures.transformAsync(
            future,
            { stage ->
                Preconditions.checkNotNull(stage)
                when (stage) {
                    is AdHocCommandsManager.Executing -> {
                        // ejabberd uses a two-step process where we supply the username first
                        val data = stage.data()
                            ?: throw IllegalStateException("Missing data in executing stage")
                        val sessionId = stage.sessionId()
                        if (Strings.isNullOrEmpty(sessionId)) {
                            throw IllegalStateException("Missing sessionId in executing stage")
                        }
                        val username = data.getFieldByName("username")
                        if (username != null && username.isRequired) {
                            throw IllegalStateException("Username is required")
                        }
                        val rosterSubscription =
                            data.getFieldByName("roster-subscription") != null
                        createAccount(address, sessionId!!, rosterSubscription)
                    }
                    is AdHocCommandsManager.Completed -> {
                        // prosody gives us 'completed' directly
                        Futures.immediateFuture(getEasyOnboardingInvite(stage))
                    }
                    else ->
                        throw IllegalStateException(
                            "Unexpected stage: ${stage!!.javaClass.simpleName}"
                        )
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun createAccount(
        address: Jid,
        sessionId: String,
        rosterSubscription: Boolean
    ): ListenableFuture<EasyOnboardingInvite> {
        val data: Map<String, Any> =
            if (rosterSubscription) mapOf("roster-subscription" to true) else emptyMap()
        val future =
            getManager(AdHocCommandsManager::class.java)
                .command(
                    address,
                    Namespace.EASY_ONBOARDING_CREATE_ACCOUNT,
                    Command.Action.EXECUTE,
                    sessionId,
                    data
                )
        return Futures.transform(
            future,
            { stage -> getEasyOnboardingInvite(stage!!) },
            MoreExecutors.directExecutor()
        )
    }

    private fun getEasyOnboardingInvite(stage: AdHocCommandsManager.Stage): EasyOnboardingInvite {
        val data = AdHocCommandsManager.completedData(stage)
        val rawUri = MiniUri.getOrNull(data.getValue("uri"))
        val uri: MiniUri.Xmpp =
            if (rawUri is MiniUri.Xmpp) rawUri
            else throw IllegalStateException("Did not find valid XMPP uri")
        val landingUrl = data.getValue("landing-url")
        if (Strings.isNullOrEmpty(landingUrl)) {
            return EasyOnboardingInvite(getAccount().getDomain(), uri)
        }
        // HttpUrl.get will throw on invalid URL
        return EasyOnboardingInvite(getAccount().getDomain(), uri, landingUrl!!.toHttpUrl())
    }

    fun hasCreateAccountFeature(): Boolean = getAddressForCreateAccountCommand().isPresent

    private fun getAddressForCreateAccountCommand(): Optional<Jid> =
        getAddressForCommand(Namespace.EASY_ONBOARDING_CREATE_ACCOUNT)

    private fun hasInviteFeature(): Boolean = getAddressForInviteCommand().isPresent

    private fun getAddressForInviteCommand(): Optional<Jid> =
        getAddressForCommand(Namespace.EASY_ONBOARDING_INVITE)

    private fun getAddressForCommand(command: String): Optional<Jid> {
        val discoManager = this.getManager(DiscoManager::class.java)
        val address = discoManager.getAddressForCommand(command)
        return Optional.fromNullable(address)
    }
}
