package eu.siacs.conversations.xmpp.manager

import android.content.Context
import android.util.Log
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.Config
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.carbons.Enable
import im.conversations.android.xmpp.model.stanza.Iq

class CarbonsManager(context: Context, connection: XmppConnection) :
    AbstractManager(context, connection) {

    private var enabled = false

    fun setEnabledOnBind(enabledOnBind: Boolean) {
        this.enabled = enabledOnBind
    }

    fun enable() {
        val request = Iq(Iq.Type.SET)
        request.addExtension(Enable())
        val future = this.connection.sendIqPacket(request)
        Futures.addCallback(
            future,
            object : com.google.common.util.concurrent.FutureCallback<Iq> {
                override fun onSuccess(result: Iq) {
                    this@CarbonsManager.enabled = true
                    Log.d(
                        Config.LOGTAG,
                        "${getAccount().getJid().asBareJid()}: successfully enabled carbons"
                    )
                }

                override fun onFailure(throwable: Throwable) {
                    Log.d(
                        Config.LOGTAG,
                        "${getAccount().getJid().asBareJid()}: could not enable carbons",
                        throwable
                    )
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun isEnabled(): Boolean = this.enabled

    fun hasFeature(): Boolean =
        getManager(DiscoManager::class.java).hasServerFeature(Namespace.CARBONS)
}
