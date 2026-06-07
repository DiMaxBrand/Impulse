package eu.siacs.conversations.xmpp.manager

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.persistance.UnifiedPushDatabase
import eu.siacs.conversations.receiver.UnifiedPushDistributor
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.error.Condition
import im.conversations.android.xmpp.model.stanza.Iq
import im.conversations.android.xmpp.model.up.Push
import im.conversations.android.xmpp.model.up.Register
import im.conversations.android.xmpp.model.up.Registered
import java.time.Instant
import java.util.Objects
import okhttp3.HttpUrl

class UnifiedPushManager(private val service: XmppConnectionService, connection: XmppConnection) :
    AbstractManager(service, connection) {

    fun push(packet: Iq) {
        val transport = packet.getFrom()
        val push = packet.getOnlyExtension(Push::class.java)
        if (push == null || transport == null) {
            connection.sendErrorFor(packet, Condition.BadRequest())
            return
        }
        if (service.processUnifiedPushMessage(getAccount(), transport, push)) {
            connection.sendResultFor(packet)
        } else {
            connection.sendErrorFor(packet, Condition.ItemNotFound())
        }
    }

    fun register(
        transport: Jid,
        renewal: UnifiedPushDatabase.PushTarget
    ): ListenableFuture<Registration> {
        val uuid = getAccount().getUuid()!!
        val hashedApplication = UnifiedPushDistributor.hash(uuid, renewal.application())
        val hashedInstance = UnifiedPushDistributor.hash(uuid, renewal.instance())
        val iq = Iq(Iq.Type.SET)
        iq.setTo(transport)
        val register = iq.addExtension(Register())
        register.setApplication(hashedApplication)
        register.setInstance(hashedInstance)
        val future = this.connection.sendIqPacket(iq)
        return Futures.transform(
            future,
            { response ->
                val registered =
                    Objects.requireNonNull(response).getExtension(Registered::class.java)
                        ?: throw IllegalStateException("Registered missing from response")
                val endpoint = registered.getEndpoint()
                val expiration = registered.getExpiration()
                if (endpoint == null || expiration == null) {
                    throw IllegalStateException("endpoint or expiration missing")
                }
                if (expiration.isBefore(Instant.now())) {
                    throw IllegalStateException("Expiration is in the past")
                }
                Registration(endpoint, expiration)
            },
            MoreExecutors.directExecutor()
        )
    }

    data class Registration(val endpoint: HttpUrl, val expiration: Instant)
}
