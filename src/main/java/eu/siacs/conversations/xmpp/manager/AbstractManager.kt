package eu.siacs.conversations.xmpp.manager

import android.content.Context
import eu.siacs.conversations.xmpp.XmppConnection
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

abstract class AbstractManager(context: Context, connection: XmppConnection) :
    XmppConnection.Delegate(context.applicationContext, connection) {

    companion object {
        @JvmField
        val FUTURE_TIMEOUT_EXECUTOR: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor()
    }
}
