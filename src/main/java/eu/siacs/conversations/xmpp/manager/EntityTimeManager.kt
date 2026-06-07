package eu.siacs.conversations.xmpp.manager

import android.util.Log
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.collect.Collections2
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.model.error.Condition
import im.conversations.android.xmpp.model.stanza.Iq
import im.conversations.android.xmpp.model.time.Time
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Objects

class EntityTimeManager(private val service: XmppConnectionService, connection: XmppConnection) :
    AbstractManager(service.applicationContext, connection) {

    private val appSettings = AppSettings(context.applicationContext)

    private val entityTimeLoader: CacheLoader<Jid, ListenableFuture<EntityTime>> =
        object : CacheLoader<Jid, ListenableFuture<EntityTime>>() {
            override fun load(address: Jid): ListenableFuture<EntityTime> {
                return Futures.transform(
                    zonedDateTime(address),
                    { zonedDateTime ->
                        OffsetEntityTime(Objects.requireNonNull(zonedDateTime).offset)
                    },
                    MoreExecutors.directExecutor()
                )
            }
        }

    private val entityTimeCache: LoadingCache<Jid, ListenableFuture<EntityTime>> =
        CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofHours(3))
            .build(entityTimeLoader)

    fun request(request: Iq) {
        if (this.appSettings.isUseTor() || getAccount().isOnion()) {
            this.connection.sendErrorFor(request, Condition.Forbidden())
            return
        }
        val from = request.getFrom()
        if (from == null) {
            this.connection.sendErrorFor(request, Condition.NotAcceptable())
            return
        }
        val contact = getManager(RosterManager::class.java).getContactFromContactList(from)
        if (contact != null && contact.showInContactList() && this.appSettings.isEntityTime()) {
            Log.d(
                Config.LOGTAG,
                "${getAccount().getJid().asBareJid()}: responding to entity time request from ${request.getFrom()}"
            )
            this.connection.sendResultFor(request, Time(ZonedDateTime.now()))
        } else {
            this.connection.sendErrorFor(request, Condition.Forbidden())
        }
    }

    fun zonedDateTime(address: Jid): ListenableFuture<ZonedDateTime> {
        Log.d(Config.LOGTAG, "requesting entity time: $address")
        val iqFuture = this.connection.sendIqPacket(Iq(Iq.Type.GET, address, Time()))
        val zonedEntityTimeFuture = Futures.transform(
            iqFuture,
            { iq ->
                val time = Objects.requireNonNull(iq).getOnlyExtension(Time::class.java)
                    ?: throw IllegalArgumentException("No valid time extension in response")
                val zonedDateTime = time.asZonedDateTime()
                    ?: throw IllegalArgumentException("No valid zoned date time in response")
                zonedDateTime
            },
            MoreExecutors.directExecutor()
        )
        Futures.addCallback(
            zonedEntityTimeFuture,
            object : com.google.common.util.concurrent.FutureCallback<ZonedDateTime> {
                override fun onSuccess(result: ZonedDateTime) {
                    service.updateConversationUi()
                }

                override fun onFailure(t: Throwable) {
                    Log.d(Config.LOGTAG, "could not request entity time from$address", t)
                }
            },
            MoreExecutors.directExecutor()
        )
        return zonedEntityTimeFuture
    }

    private fun getEntityTimes(address: Jid): ListenableFuture<List<EntityTime>> {
        val presences = getManager(PresenceManager::class.java).getPresences(address)
        val futures = Collections2.transform(presences) { p ->
            val fullAddress = Objects.requireNonNull(p).getFrom()
            val infoQuery = getManager(DiscoManager::class.java).get(fullAddress)
            if (infoQuery == null || !infoQuery.hasFeature(Namespace.TIME)) {
                return@transform Futures.immediateFuture<EntityTime>(NoEntityTime())
            }
            val entityTimeFuture = entityTimeCache.getUnchecked(fullAddress)
            val entityTimeTimeoutFuture = Futures.withTimeout(
                entityTimeFuture,
                Duration.ofSeconds(10L),
                FUTURE_TIMEOUT_EXECUTOR
            )
            Futures.catching(
                entityTimeTimeoutFuture,
                Exception::class.java,
                { InvalidEntityTime() },
                MoreExecutors.directExecutor()
            )
        }
        return Futures.allAsList(futures)
    }

    fun getZonedDateTime(address: Jid): ListenableFuture<ZonedDateTime> {
        if (!this.appSettings.isEntityTime()) {
            return Futures.immediateFailedFuture(
                IllegalStateException("Requesting entity time is disabled")
            )
        }
        val contact = getManager(RosterManager::class.java).getContactFromContactList(address)
            ?: return Futures.immediateFailedFuture(
                IllegalStateException("Requesting entity time is limited to contacts")
            )
        return Futures.transform(
            getEntityTimes(address),
            { entityTimes ->
                if (entityTimes == null) {
                    throw IllegalStateException("No entity times available")
                }
                val unknownAsNulls = Collections2.transform(entityTimes) { entityTime ->
                    if (entityTime is OffsetEntityTime) entityTime.zoneOffset else null
                }
                val zoneOffsets: Set<ZoneOffset> =
                    ImmutableSet.copyOf(unknownAsNulls.filterNotNull())
                if (zoneOffsets.size == 1) {
                    Instant.now().atZone(Iterables.getOnlyElement(zoneOffsets))
                } else {
                    throw IllegalStateException("Ambiguous time zones")
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    sealed interface EntityTime

    class NoEntityTime : EntityTime

    class OffsetEntityTime(val zoneOffset: ZoneOffset) : EntityTime

    class InvalidEntityTime : EntityTime

    companion object {
        @JvmStatic
        fun isNightTime(zonedDateTime: ZonedDateTime): Boolean {
            val localTime = zonedDateTime.toLocalTime()
            return localTime.isAfter(LocalTime.of(22, 0)) || localTime.isBefore(LocalTime.of(8, 0))
        }

        @JvmStatic
        fun isDifferentTimeZone(zonedDateTime: ZonedDateTime): Boolean {
            val local = ZonedDateTime.now()
            return local.offset != zonedDateTime.offset
        }

        @JvmStatic
        fun noRecentMessages(conversation: Conversation): Boolean {
            val duration = Duration.between(conversation.getLastReceived(), Instant.now())
            return DURATION_RECENT_MESSAGE.compareTo(duration) < 0
        }

        private val DURATION_RECENT_MESSAGE: Duration = Duration.ofMinutes(12)
    }
}
