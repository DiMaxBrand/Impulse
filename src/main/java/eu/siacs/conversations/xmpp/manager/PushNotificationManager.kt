package eu.siacs.conversations.xmpp.manager

import android.content.Context
import com.google.common.base.Preconditions
import com.google.common.base.Strings
import com.google.common.collect.ImmutableMap
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.data.Data
import im.conversations.android.xmpp.model.push.Disable
import im.conversations.android.xmpp.model.push.Enable
import im.conversations.android.xmpp.model.stanza.Iq
import java.util.concurrent.atomic.AtomicInteger

class PushNotificationManager(context: Context, connection: XmppConnection) :
    AbstractManager(context, connection) {

    companion object {
        private const val COMMAND_NODE = "register-push-fcm"
    }

    private val pushNotificationCounter = AtomicInteger()

    fun register(
        appServer: Jid,
        fcmToken: String,
        androidId: String
    ): ListenableFuture<Registration> {
        val parameter: Map<String, Any> =
            ImmutableMap.of("token", fcmToken, "android-id", androidId)
        val future =
            getManager(AdHocCommandsManager::class.java)
                .commandComplete(appServer, COMMAND_NODE, parameter)
        return Futures.transform(
            future,
            { result ->
                Preconditions.checkNotNull(result)
                val node = result!!.getValue("node")
                val secret = result.getValue("secret")
                if (Strings.isNullOrEmpty(node) || Strings.isNullOrEmpty(secret)) {
                    throw IllegalStateException("Missing node or secret in response")
                }
                Registration(appServer, node!!, secret!!)
            },
            MoreExecutors.directExecutor()
        )
    }

    fun registerAndEnable(
        appServer: Jid,
        fmcToken: String,
        androidId: String
    ): ListenableFuture<Void?> {
        val future = register(appServer, fmcToken, androidId)
        return Futures.transformAsync(
            future,
            { registration ->
                Preconditions.checkNotNull(registration)
                enable(registration!!)
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun enable(registration: Registration): ListenableFuture<Void?> {
        val iq = Iq(Iq.Type.SET)
        val enable = iq.addExtension(Enable())
        enable.setJid(registration.address)
        enable.setNode(registration.node)
        enable.addExtension(
            Data.of(
                ImmutableMap.of<String, Any>("secret", registration.secret),
                Namespace.PUB_SUB_PUBLISH_OPTIONS
            )
        )
        return Futures.transform(
            connection.sendIqPacket(iq),
            { _: Iq? -> null },
            MoreExecutors.directExecutor()
        )
    }

    fun disable(appServer: Jid, node: String): ListenableFuture<Void?> {
        val iq = Iq(Iq.Type.SET)
        val disable = iq.addExtension(Disable())
        disable.setJid(appServer)
        disable.setNode(node)
        return Futures.transform(
            connection.sendIqPacket(iq),
            { _: Iq? -> null },
            MoreExecutors.directExecutor()
        )
    }

    fun hasFeature(): Boolean =
        getManager(DiscoManager::class.java).hasAccountFeature(Namespace.PUSH)

    fun incrementAndGetPushNotificationCounter(): Int =
        this.pushNotificationCounter.incrementAndGet()

    fun getPushNotificationCounter(): Int = this.pushNotificationCounter.get()

    data class Registration(val address: Jid, val node: String, val secret: String)
}
