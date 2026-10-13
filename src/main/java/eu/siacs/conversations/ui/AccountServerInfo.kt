package eu.siacs.conversations.ui

import android.content.Context
import com.google.common.base.Joiner
import com.google.common.base.Strings
import com.google.common.collect.ImmutableList
import eu.siacs.conversations.R
import eu.siacs.conversations.crypto.axolotl.AxolotlService
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.services.PushManagementService
import eu.siacs.conversations.utils.CryptoHelper
import eu.siacs.conversations.utils.UIHelper
import eu.siacs.conversations.xmpp.manager.BlockingManager
import eu.siacs.conversations.xmpp.manager.CarbonsManager
import eu.siacs.conversations.xmpp.manager.ClientStateIndicationManager
import eu.siacs.conversations.xmpp.manager.ExternalServiceDiscoveryManager
import eu.siacs.conversations.xmpp.manager.HttpUploadManager
import eu.siacs.conversations.xmpp.manager.MessageArchiveManager
import eu.siacs.conversations.xmpp.manager.PepManager
import eu.siacs.conversations.xmpp.manager.PushNotificationManager
import eu.siacs.conversations.xmpp.manager.RosterManager

/** One line of the "Server info" table. */
class InfoRow(val label: String, val value: String)

/** One of our own OMEMO devices, for the read-only device list. */
class DeviceKey(val fingerprint: String, val verified: Boolean)

/**
 * The same facts the old account-details screen printed under "Server info", gathered into plain
 * rows. Only meaningful while the account is online.
 */
internal fun serverInfoRows(context: Context, account: Account): List<InfoRow> {
    if (!account.isOnlineAndConnected()) return emptyList()
    val connection = account.getXmppConnection()
    val features = connection.getFeatures()
    fun yesNo(on: Boolean) =
        context.getString(if (on) R.string.server_info_available else R.string.server_info_unavailable)
    val rows = ArrayList<InfoRow>()
    rows += InfoRow(
        context.getString(R.string.server_info_roster_version),
        yesNo(connection.getManager(RosterManager::class.java).versioning()),
    )
    rows += InfoRow(
        context.getString(R.string.server_info_carbon_messages),
        yesNo(connection.getManager(CarbonsManager::class.java).isEnabled()),
    )
    rows += InfoRow(
        context.getString(R.string.server_info_mam),
        yesNo(connection.getManager(MessageArchiveManager::class.java).hasFeature()),
    )
    rows += InfoRow(
        context.getString(R.string.server_info_csi),
        yesNo(connection.getManager(ClientStateIndicationManager::class.java).hasFeature()),
    )
    rows += InfoRow(
        context.getString(R.string.server_info_blocking),
        yesNo(connection.getManager(BlockingManager::class.java).hasFeature()),
    )
    rows += InfoRow(context.getString(R.string.server_info_stream_management), yesNo(features.sm()))
    rows += InfoRow(
        context.getString(R.string.server_info_external_service_discovery),
        yesNo(connection.getManager(ExternalServiceDiscoveryManager::class.java).hasFeature()),
    )
    rows += InfoRow(context.getString(R.string.server_info_bind2), yesNo(features.bind2()))
    rows += InfoRow(context.getString(R.string.server_info_sasl2), yesNo(features.sasl2()))
    rows += InfoRow(
        context.getString(R.string.server_info_login_mechanism),
        Strings.nullToEmpty(features.loginMechanism()),
    )

    val stanzas = connection.getStanzaRxTx()
    val pushCount = connection.getManager(PushNotificationManager::class.java).getPushNotificationCounter()
    if (PushManagementService.isStub() || pushCount == 0 || !PushManagementService(context).available(account)) {
        rows += InfoRow(
            context.getString(R.string.server_info_stanzas_rx_tx),
            Joiner.on('/').join(ImmutableList.of(stanzas.rx(), stanzas.tx())),
        )
    } else {
        rows += InfoRow(
            context.getString(R.string.server_info_stanzas_rx_tx_push),
            Joiner.on('/').join(ImmutableList.of(stanzas.rx(), stanzas.tx(), pushCount)),
        )
    }

    val pep = connection.getManager(PepManager::class.java)
    val pepText = if (pep.isAvailable()) {
        val axolotl = account.getAxolotlService()
        when {
            axolotl != null && axolotl.isPepBroken() -> R.string.server_info_broken
            pep.hasPublishOptions() -> R.string.server_info_available
            else -> R.string.server_info_partial
        }
    } else {
        R.string.server_info_unavailable
    }
    rows += InfoRow(context.getString(R.string.server_info_pep), context.getString(pepText))

    val uploadService = connection.getManager(HttpUploadManager::class.java).getService()
    rows += InfoRow(
        context.getString(R.string.server_info_http_upload),
        if (uploadService == null) {
            context.getString(R.string.server_info_unavailable)
        } else {
            val max: Long? = uploadService.maxFileSize
            if (max == null) context.getString(R.string.server_info_available)
            else UIHelper.filesizeToString(max)
        },
    )
    if (!PushManagementService.isStub()) {
        rows += InfoRow(
            context.getString(R.string.server_info_push),
            yesNo(PushManagementService(context).available(account)),
        )
    }
    return rows
}

internal fun sessionEstablishedText(context: Context, account: Account): String =
    UIHelper.readableTimeDifferenceFull(context, account.getXmppConnection().getLastSessionEstablished())

/** Our own OMEMO fingerprint, spaced for reading, or null when there is none. */
internal fun ownFingerprintText(account: Account): String? {
    val own = account.getAxolotlService()?.getOwnFingerprint() ?: return null
    return CryptoHelper.prettifyFingerprint(own.substring(2))
}

internal fun otherDeviceKeys(account: Account): List<DeviceKey> {
    val service: AxolotlService = account.getAxolotlService() ?: return emptyList()
    return service.findOwnSessions().mapNotNull { session ->
        val trust = session.getTrust()
        if (trust.isCompromised()) null
        else DeviceKey(CryptoHelper.prettifyFingerprint(session.getFingerprint().substring(2)), trust.isVerified())
    }
}
