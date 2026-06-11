package eu.siacs.conversations.xmpp.manager

import android.content.Context
import com.google.common.collect.ImmutableSet
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.commands.Command
import im.conversations.android.xmpp.model.data.Data
import im.conversations.android.xmpp.model.stanza.Iq
import java.util.Objects

class AdHocCommandsManager(context: Context, connection: XmppConnection) :
    AbstractManager(context, connection) {

    fun command(address: Jid, node: String): ListenableFuture<Stage> =
        command(address, node, Command.Action.EXECUTE, null, null)

    fun command(
        address: Jid,
        node: String,
        action: Command.Action,
        sessionId: String?,
        data: Map<String, Any>?
    ): ListenableFuture<Stage> {
        val iq = Iq(Iq.Type.SET, address, Command(node, action, sessionId, data))
        return Futures.transform(
            this.connection.sendIqPacket(iq),
            { response ->
                val command = Objects.requireNonNull(response).getOnlyExtension(Command::class.java)
                    ?: throw IllegalStateException("Expected command in response")
                val resultSessionId = command.sessionId
                val status = command.status
                    ?: throw IllegalStateException("There must be a valid status")
                val resultData = command.data
                val actions = command.actions
                when (status) {
                    Command.Status.EXECUTING -> {
                        if (actions == null) {
                            // actions is only a SHOULD. default both execute and actions to 'next'
                            Executing(
                                resultSessionId,
                                Command.Action.NEXT,
                                ImmutableSet.of(Command.Action.NEXT),
                                resultData
                            )
                        } else {
                            Executing(
                                resultSessionId,
                                actions.execute,
                                actions.actions,
                                resultData
                            )
                        }
                    }
                    Command.Status.CANCELED -> Cancelled(resultSessionId)
                    Command.Status.COMPLETED -> Completed(resultSessionId, resultData)
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun commandComplete(address: Jid, node: String): ListenableFuture<Data> =
        commandComplete(address, node, null)

    fun commandComplete(
        address: Jid,
        node: String,
        data: Map<String, Any>?
    ): ListenableFuture<Data> {
        val future = command(address, node, Command.Action.COMPLETE, null, data)
        return Futures.transform(
            future,
            { stage -> completedData(stage) },
            MoreExecutors.directExecutor()
        )
    }

    sealed interface Stage {
        fun sessionId(): String?
    }

    data class Cancelled(private val sessionId: String?) : Stage {
        override fun sessionId(): String? = sessionId
    }

    data class Executing(
        private val sessionId: String?,
        val execute: Command.Action,
        val actions: Set<Command.Action>,
        val data: Data?
    ) : Stage {
        override fun sessionId(): String? = sessionId
    }

    data class Completed(private val sessionId: String?, val data: Data?) : Stage {
        override fun sessionId(): String? = sessionId
    }

    companion object {
        @JvmStatic
        fun completedData(stage: Stage): Data {
            if (stage is Completed) {
                val data = stage.data
                    ?: throw IllegalStateException("Missing data from completed stage")
                return data
            }
            throw IllegalStateException("Command did not complete")
        }
    }
}
