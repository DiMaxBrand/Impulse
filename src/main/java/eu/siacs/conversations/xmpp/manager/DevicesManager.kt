package eu.siacs.conversations.xmpp.manager

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.BuildConfig
import eu.siacs.conversations.Config
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.utils.AccountDevices
import eu.siacs.conversations.utils.DeviceEntry
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.NodeConfiguration
import im.conversations.android.xmpp.model.devices.Device
import im.conversations.android.xmpp.model.devices.Devices
import im.conversations.android.xmpp.model.pubsub.Items
import java.util.concurrent.TimeUnit

/**
 * The devices of one account, and which of them take part in its status.
 *
 * A contact is shown as "on silent" when any one of a person's devices is, so a second phone left on
 * silent in a drawer silences the person. Every Impulse writes its own entry into one shared list
 * (PEP, access model whitelist: only the account itself reads or changes it) and any of them can
 * leave another out of the status. A device that is left out publishes "extended away", which never
 * beats a device in use and is never "on silent"; it still receives everything as before.
 *
 * A phone that runs a version without this list cannot be asked. For it an entry is written on its
 * behalf, with a time limit of [REQUEST_LIFETIME_MS]: when it updates, connects and finds the entry
 * with its own session name, it takes it over and applies it; if it has not by then, the request
 * lapses. It can be left out again at any time.
 */
class DevicesManager(private val appContext: Context, connection: XmppConnection) :
    AbstractManager(appContext, connection) {

    private val store = AccountDevices(appContext)

    private val accountKey: String get() = getAccount().getJid().asBareJid().toString()

    /** The last list seen for this account (also kept across restarts). */
    @Volatile private var entries: List<DeviceEntry> = store.load(accountKey)

    fun entries(): List<DeviceEntry> = entries

    /** This phone's permanent id in the list. */
    val ownDeviceId: String get() = store.ownId

    /** True when the list says this very phone is left out of the status. */
    fun isThisDeviceExcluded(): Boolean {
        val mine = entries.firstOrNull { it.id == store.ownId } ?: return false
        return mine.excluded
    }

    /** After login: read the list, add or refresh this phone's own entry, publish if it changed. */
    fun sync() {
        if (!getManager(PepManager::class.java).isAvailable()) return
        val future = getManager(PepManager::class.java).fetchMostRecentItem(Namespace.DEVICES, Devices::class.java)
        Futures.addCallback(
            future,
            object : FutureCallback<Devices> {
                override fun onSuccess(result: Devices) {
                    merge(parse(result), publishIfChanged = true)
                }

                override fun onFailure(t: Throwable) {
                    // No list yet (or none reachable): start from what was last seen.
                    merge(entries, publishIfChanged = true)
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun handleItems(items: Items) {
        val devices = items.getFirstItem(Devices::class.java) ?: return
        merge(parse(devices), publishIfChanged = false)
    }

    private fun parse(devices: Devices?): List<DeviceEntry> =
        devices?.getExtensions(Device::class.java)?.map {
            DeviceEntry(it.deviceId, it.resource, it.deviceName, it.version, it.isExcluded, it.until, it.seen)
        } ?: emptyList()

    /** The list with this phone's own entry in it (claimed from a pending one if there is one). */
    private fun merge(list: List<DeviceEntry>, publishIfChanged: Boolean) {
        val now = System.currentTimeMillis()
        val ownId = store.ownId
        val resource = getAccount().getResource()
        var result = list.filter { !(it.pending && it.until in 1 until now) }
        val byId = result.indexOfFirst { it.id == ownId }
        val byPending = result.indexOfFirst { it.pending && it.resource == resource }
        val index = if (byId >= 0) byId else byPending
        val mine = DeviceEntry(
            id = ownId,
            resource = resource,
            name = result.getOrNull(index)?.name?.ifBlank { null } ?: defaultName(),
            version = BuildConfig.VERSION_NAME.substringBefore('+'),
            excluded = result.getOrNull(index)?.excluded ?: false,
            until = 0,
            seen = now,
        )
        val old = result.getOrNull(index)
        result = if (index >= 0) result.toMutableList().also { it[index] = mine } else result + mine
        val changed = old == null || old.copy(seen = 0) != mine.copy(seen = 0) ||
            now - old.seen > TimeUnit.DAYS.toMillis(1)
        val before = isThisDeviceExcluded()
        entries = result
        store.save(accountKey, result)
        if (publishIfChanged && (changed || result.size != list.size)) publish(result)
        if (before != isThisDeviceExcluded()) {
            (appContext as? XmppConnectionService)?.refreshAllPresences()
        }
    }

    private fun defaultName(): String =
        listOf(Build.MANUFACTURER, Build.MODEL).filter { !it.isNullOrBlank() }.joinToString(" ").trim()

    private fun publish(list: List<DeviceEntry>): ListenableFuture<Void?> {
        val devices = Devices()
        list.forEach {
            devices.addExtension(Device.create(it.id, it.resource, it.name, it.version, it.excluded, it.until, it.seen))
        }
        Log.d(Config.LOGTAG, "${getAccount().getJid().asBareJid()}: publishing ${list.size} devices")
        return getManager(PepManager::class.java)
            .publishSingleton(devices, Namespace.DEVICES, NodeConfiguration.WHITELIST)
    }

    /** Result of [setExcluded]. */
    enum class Result { DONE, PENDING_UPDATE, LAST_DEVICE, NOT_AVAILABLE }

    /**
     * Leaves a device out of the status, or takes it back in. [resource] names it (the session name
     * the server shows for it). A device without an entry runs a version that does not know the
     * list: an entry is written for it that lapses after [REQUEST_LIFETIME_MS] ([Result.PENDING_UPDATE]).
     * The last device that still counts cannot be left out ([Result.LAST_DEVICE]).
     */
    fun setExcluded(resource: String, excluded: Boolean): Result {
        if (!getManager(PepManager::class.java).isAvailable()) return Result.NOT_AVAILABLE
        val now = System.currentTimeMillis()
        val list = entries.toMutableList()
        val index = list.indexOfFirst { it.resource == resource }
        if (excluded) {
            val others = onlineResources().filter { it != resource }
            val stillCounting = list.count { !it.excluded && it.resource != resource && it.resource in others } +
                others.count { r -> list.none { it.resource == r } }
            if (stillCounting < 1) return Result.LAST_DEVICE
        }
        val result: Result
        if (index >= 0 && !list[index].pending) {
            list[index] = list[index].copy(excluded = excluded)
            result = Result.DONE
        } else if (index >= 0) {
            list[index] = list[index].copy(excluded = excluded, until = if (excluded) now + REQUEST_LIFETIME_MS else 0)
            result = if (excluded) Result.PENDING_UPDATE else Result.DONE
        } else {
            list.add(DeviceEntry("", resource, "", "", excluded, if (excluded) now + REQUEST_LIFETIME_MS else 0, 0))
            result = if (excluded) Result.PENDING_UPDATE else Result.DONE
        }
        entries = list
        store.save(accountKey, list)
        publish(list)
        return result
    }

    fun rename(resource: String, name: String) {
        val list = entries.toMutableList()
        val index = list.indexOfFirst { it.resource == resource }
        if (index < 0) return
        list[index] = list[index].copy(name = name.trim().take(40))
        entries = list
        store.save(accountKey, list)
        publish(list)
    }

    /** The session names of the account's devices that are online, other than this one. */
    fun onlineResources(): List<String> =
        getManager(PresenceManager::class.java)
            .getPresences(getAccount().getJid().asBareJid())
            .mapNotNull { it.getFrom()?.resource }
            .distinct()

    companion object {
        /** How long a request for a device that has not updated yet stays open. */
        val REQUEST_LIFETIME_MS: Long = TimeUnit.HOURS.toMillis(24)
    }
}
