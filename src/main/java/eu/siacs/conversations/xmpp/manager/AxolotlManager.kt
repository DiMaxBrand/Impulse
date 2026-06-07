package eu.siacs.conversations.xmpp.manager

import android.content.Context
import android.util.Log
import eu.siacs.conversations.Config
import eu.siacs.conversations.crypto.axolotl.AxolotlService
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.axolotl.DeviceList
import im.conversations.android.xmpp.model.pubsub.Items

class AxolotlManager(context: Context, connection: XmppConnection) :
    AbstractManager(context, connection) {

    fun handleItems(from: Jid, items: Items) {
        val account = account
        val deviceList = items.getFirstItem(DeviceList::class.java) ?: return
        val deviceIds = deviceList.deviceIds
        Log.d(
            Config.LOGTAG,
            "${AxolotlService.getLogprefix(account)}Received PEP device list $deviceIds update from $from, processing... ",
        )
        account.axolotlService.registerDevices(from, HashSet(deviceIds))
    }
}
