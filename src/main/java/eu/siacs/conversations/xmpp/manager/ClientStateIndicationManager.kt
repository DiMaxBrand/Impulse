package eu.siacs.conversations.xmpp.manager

import android.content.Context
import android.util.Log
import eu.siacs.conversations.Config
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.csi.Active
import im.conversations.android.xmpp.model.csi.Inactive

class ClientStateIndicationManager(context: Context, connection: XmppConnection) :
    AbstractManager(context, connection) {

    fun hasFeature(): Boolean {
        val streamFeatures = connection.streamFeatures
        return streamFeatures != null && streamFeatures.clientStateIndication()
    }

    fun indicateActive() {
        Log.d(Config.LOGTAG, "${account.jid.asBareJid()} sending csi//active")
        connection.send(Active())
    }

    fun indicateInactive() {
        Log.d(Config.LOGTAG, "${account.jid.asBareJid()} sending csi//inactive")
        connection.send(Inactive())
    }
}
