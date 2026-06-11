package eu.siacs.conversations.xmpp.manager

import android.util.Log
import com.google.common.base.Strings
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Collections2
import com.google.common.collect.ImmutableList
import com.google.common.collect.Multimap
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.Config
import eu.siacs.conversations.android.Device
import eu.siacs.conversations.crypto.PgpEngine
import eu.siacs.conversations.entities.Contact
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.Entity
import im.conversations.android.xmpp.EntityCapabilities
import im.conversations.android.xmpp.EntityCapabilities2
import im.conversations.android.xmpp.ServiceDescription
import im.conversations.android.xmpp.model.Extension
import im.conversations.android.xmpp.model.capabilties.Capabilities
import im.conversations.android.xmpp.model.capabilties.LegacyCapabilities
import im.conversations.android.xmpp.model.idle.Idle
import im.conversations.android.xmpp.model.nick.Nick
import im.conversations.android.xmpp.model.pars.PreAuth
import im.conversations.android.xmpp.model.pgp.Signed
import im.conversations.android.xmpp.model.stanza.Presence
import im.conversations.android.xmpp.model.vcard.update.VCardUpdate
import java.time.Instant
import java.util.HashMap
import java.util.concurrent.TimeoutException
import org.openintents.openpgp.util.OpenPgpUtils

class PresenceManager(private val service: XmppConnectionService, connection: XmppConnection) :
    AbstractManager(service.getApplicationContext(), connection) {

    private val appSettings = AppSettings(service.getApplicationContext())

    private val presences: Multimap<Jid, Presence> = ArrayListMultimap.create()

    private val serviceDescriptions: MutableMap<EntityCapabilities.Hash, ServiceDescription> =
        HashMap()

    fun handlePresence(presence: Presence) {
        val from = presence.getFrom()
        val type = presence.getType()
        if (from == null || from == getAccount().getJid()) {
            return
        }
        if (from.isBareJid() &&
                getManager(MultiUserChatManager::class.java).isMuc(from.asBareJid())) {
            // the old vCard updates will end up here
            return
        }
        if (type == null) {
            this.handleAvailablePresence(presence)
        } else if (type == Presence.Type.UNAVAILABLE) {
            this.handleUnavailablePresence(presence)
        } else if (type == Presence.Type.SUBSCRIBE) {
            this.handleSubscribePresence(presence)
        } else {
            // TODO we can do something with error presence and show a tag on contacts who's server
            // is not found or something
        }
        this.service.updateRosterUi()
    }

    private fun handleAvailablePresence(presence: Presence) {
        val from = presence.getFrom()
        val account = getAccount()
        val contact = getManager(RosterManager::class.java).getContact(from)
        val sizeBefore = contact.getPresences().size

        synchronized(this.presences) {
            remove(this.presences, from)
            this.presences.put(from.asBareJid(), presence)
        }

        val nodeHash = presence.getCapabilities()
        if (nodeHash != null) {
            val discoFuture =
                this.getManager(DiscoManager::class.java)
                    .infoOrCache(Entity.presence(from), nodeHash.node, nodeHash.hash)
            awaitDiscoFuture(contact, discoFuture)
        }

        val pgp: PgpEngine? = this.service.getPgpEngine()
        val x = presence.getExtension(Signed::class.java)
        if (pgp != null && x != null) {
            val status = presence.getStatus()
            val keyId = pgp.fetchKeyId(account, status, x.getContent())
            if (keyId != 0L && contact.setPgpKeyId(keyId)) {
                Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid().toString() +
                        ": found OpenPGP key id for " +
                        contact.getAddress() +
                        " " +
                        OpenPgpUtils.convertKeyIdToHex(keyId)
                )
                this.connection.getManager(RosterManager::class.java).writeToDatabaseAsync()
            }
        }
        val online = sizeBefore < contact.getPresences().size
        this.service.onContactStatusChanged.onContactStatusChanged(contact, online)
    }

    private fun handleUnavailablePresence(packet: Presence) {
        val account = getAccount().getJid().asBareJid()
        val from = packet.getFrom()
        if (from == null || from == account.getDomain() || from == account) {
            // Snikket sends unavailable presence from the server domain. We ignore this mostly in
            // order to avoid executing DiscoManager.clear() which would have caused server disco
            // features to go away
            // the operation on the 'Contact' object will also be ignored but those are irrelevant
            // anyway.
            Log.d(Config.LOGTAG, "ignoring unavailable presence from $from")
            val vCardUpdate = packet.getExtension(VCardUpdate::class.java)
            if (vCardUpdate != null && account.getDomain() == from) {
                // Snikket special feature
                getManager(AvatarManager::class.java).handleVCardUpdate(from, vCardUpdate)
            }
            return
        }
        val contact = this.getManager(RosterManager::class.java).getContact(from)

        // the clear function will be a no-op in case the unavailable presence is coming from an
        // item listed in disco#item. why that would be the case who knows but we are also
        // deliberately ignoring presence from the server
        getManager(DiscoManager::class.java).clear(from)

        synchronized(this.presences) {
            if (from.isBareJid()) {
                this.presences.removeAll(from.asBareJid())
            } else {
                remove(this.presences, from)
            }
        }
        this.service.onContactStatusChanged.onContactStatusChanged(contact, false)
    }

    private fun handleSubscribePresence(packet: Presence) {
        val from = packet.getFrom()
        val account = getAccount()
        val contact = this.getManager(RosterManager::class.java).getContact(from)
        if (contact.isBlocked()) {
            Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid().toString() +
                    ": ignoring 'subscribe' presence from blocked " +
                    from
            )
            return
        }
        val nick = packet.getExtension(Nick::class.java)
        if (nick != null && contact.setPresenceName(nick.getContent())) {
            getManager(RosterManager::class.java).writeToDatabaseAsync()
            this.service.getAvatarService().clear(contact)
        }
        if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
            connection.getManager(PresenceManager::class.java).subscribed(
                contact.getAddress().asBareJid()
            )
        } else {
            contact.setOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)
            val conversation: Conversation =
                this.service.findOrCreateConversation(
                    account,
                    contact.getAddress().asBareJid(),
                    false,
                    false
                )
            val statusMessage = packet.findChildContent("status")
            if (statusMessage != null &&
                    statusMessage.isNotEmpty() &&
                    conversation.countMessages() == 0) {
                conversation.add(
                    Message(
                        conversation,
                        statusMessage,
                        Message.ENCRYPTION_NONE,
                        Message.STATUS_RECEIVED
                    )
                )
            }
        }
    }

    private fun awaitDiscoFuture(contact: Contact, discoFuture: ListenableFuture<Void?>) {
        Futures.addCallback(
            discoFuture,
            object : FutureCallback<Void?> {
                override fun onSuccess(result: Void?) {
                    if (contact.refreshRtpCapability()) {
                        getManager(RosterManager::class.java).writeToDatabaseAsync()
                    }
                }

                override fun onFailure(throwable: Throwable) {
                    if (throwable is TimeoutException) {
                        return
                    }
                    Log.d(
                        Config.LOGTAG,
                        "could not retrieve disco from ${contact.getAddress()}",
                        throwable
                    )
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun clear() {
        synchronized(this.presences) {
            for (presence in this.presences.values()) {
                getManager(DiscoManager::class.java).clear(presence.getFrom())
            }
            this.presences.clear()
        }
    }

    fun getPresences(address: Jid): List<Presence> {
        synchronized(this.presences) {
            return ImmutableList.copyOf(this.presences.get(address))
        }
    }

    fun subscribe(address: Jid) {
        subscribe(address, null)
    }

    fun subscribe(address: Jid, preAuth: String?) {
        val presence = Presence(Presence.Type.SUBSCRIBE)
        presence.setTo(address)
        val displayName = getAccount().getDisplayName()
        if (!Strings.isNullOrEmpty(displayName)) {
            presence.addExtension(Nick(displayName))
        }
        if (preAuth != null) {
            presence.addExtension(PreAuth()).setToken(preAuth)
        }
        this.connection.sendPresencePacket(presence)
    }

    fun unsubscribe(address: Jid) {
        val presence = Presence(Presence.Type.UNSUBSCRIBE)
        presence.setTo(address)
        this.connection.sendPresencePacket(presence)
    }

    fun unsubscribed(address: Jid) {
        val presence = Presence(Presence.Type.UNSUBSCRIBED)
        presence.setTo(address)
        this.connection.sendPresencePacket(presence)
    }

    fun subscribed(address: Jid) {
        val presence = Presence(Presence.Type.SUBSCRIBED)
        presence.setTo(address)
        this.connection.sendPresencePacket(presence)
    }

    fun available() {
        available(service.checkListeners() && appSettings.isBroadcastLastActivity())
    }

    fun available(withIdle: Boolean) {
        val account = this.getAccount()
        val serviceDiscoveryFeatures = getManager(DiscoManager::class.java).getServiceDescription()
        val infoQuery = serviceDiscoveryFeatures.asInfoQuery()
        val capsHash = EntityCapabilities.hash(infoQuery)
        val caps2Hash: EntityCapabilities2.EntityCaps2Hash
        try {
            caps2Hash = EntityCapabilities2.hash(infoQuery)
        } catch (e: EntityCapabilities2.IllegalInfoQueryException) {
            Log.e(Config.LOGTAG, "could not compute caps2 hash of outgoing info query")
            return
        }
        serviceDescriptions[capsHash] = serviceDiscoveryFeatures
        serviceDescriptions[caps2Hash] = serviceDiscoveryFeatures
        val capabilities = Capabilities()
        capabilities.setHash(caps2Hash)
        val legacyCapabilities = LegacyCapabilities()
        legacyCapabilities.setNode(DiscoManager.CAPABILITY_NODE)
        legacyCapabilities.setHash(capsHash)
        val presence = Presence()
        presence.addExtension(capabilities)
        presence.addExtension(legacyCapabilities)
        val pgpSignature = account.getPgpSignature()
        val message = account.getPresenceStatusMessage()
        val availability: Presence.Availability =
            if (appSettings.isUserManagedAvailability()) {
                account.getPresenceStatus()
            } else {
                getTargetPresence()
            }
        presence.setAvailability(availability)
        presence.setStatus(message)
        if (pgpSignature != null) {
            presence.addExtension(Signed(pgpSignature))
        }
        val lastActivity = service.getLastActivity()
        if (lastActivity > 0 && withIdle) {
            val since =
                Math.min(lastActivity, System.currentTimeMillis()) // don't send future dates
            presence.addExtension(Idle(Instant.ofEpochMilli(since)))
        }
        connection.sendPresencePacket(presence)
    }

    fun unavailable() {
        val presence = Presence(Presence.Type.UNAVAILABLE)
        this.connection.sendPresencePacket(presence)
    }

    fun available(to: Jid, vararg extensions: Extension) {
        available(to, null, *extensions)
    }

    fun available(to: Jid, message: String?, vararg extensions: Extension) {
        val presence = Presence()
        presence.setTo(to)
        presence.setStatus(message)
        for (extension in extensions) {
            presence.addExtension(extension)
        }
        connection.sendPresencePacket(presence)
    }

    fun unavailable(to: Jid) {
        val presence = Presence(Presence.Type.UNAVAILABLE)
        presence.setTo(to)
        connection.sendPresencePacket(presence)
    }

    private fun getTargetPresence(): Presence.Availability {
        val device = Device(context)
        return if (appSettings.isDndSyncSystem() &&
                device.isPhoneSilenced(appSettings.isDndIncludeSilentMode())) {
            Presence.Availability.DND
        } else if (appSettings.isAwayWhenScreenLocked() && device.isScreenLocked()) {
            Presence.Availability.AWAY
        } else {
            Presence.Availability.ONLINE
        }
    }

    fun getCachedServiceDescription(hash: EntityCapabilities.Hash): ServiceDescription? {
        return this.serviceDescriptions[hash]
    }

    companion object {
        private fun remove(map: Multimap<Jid, Presence>, address: Jid) {
            val existing = map.get(address.asBareJid())
            val existingWithAddressRemoved =
                ImmutableList.copyOf(
                    Collections2.filter(existing) { p -> address != p.getFrom() }
                )
            map.replaceValues(address.asBareJid(), existingWithAddressRemoved)
        }
    }
}
