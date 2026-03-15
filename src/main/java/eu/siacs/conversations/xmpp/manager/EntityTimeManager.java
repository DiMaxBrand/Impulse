package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import android.util.Log;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.time.Time;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class EntityTimeManager extends AbstractManager {

    private final LoadingCache<Jid, ListenableFuture<EntityTime>> entityTimeCache =
            CacheBuilder.newBuilder()
                    .expireAfterWrite(Duration.ofHours(3))
                    .build(
                            new CacheLoader<>() {
                                @Override
                                public ListenableFuture<EntityTime> load(final Jid address) {
                                    return Futures.transform(
                                            zonedDateTime(address),
                                            zonedDateTime ->
                                                    new OffsetEntityTime(
                                                            Objects.requireNonNull(zonedDateTime)
                                                                    .getOffset()),
                                            MoreExecutors.directExecutor());
                                }
                            });
    private final AppSettings appSettings;

    public EntityTimeManager(final Context context, final XmppConnection connection) {
        super(context, connection);
        this.appSettings = new AppSettings(context);
    }

    public void request(final Iq request) {
        if (this.appSettings.isUseTor() || getAccount().isOnion()) {
            this.connection.sendErrorFor(request, new Condition.Forbidden());
            return;
        }
        final var from = request.getFrom();
        if (from == null) {
            this.connection.sendErrorFor(request, new Condition.NotAcceptable());
            return;
        }
        final var contact = getManager(RosterManager.class).getContactFromContactList(from);
        if (contact != null && contact.showInContactList() && this.appSettings.isEntityTime()) {
            Log.d(
                    Config.LOGTAG,
                    getAccount().getJid().asBareJid()
                            + ": responding to entity time request from "
                            + request.getFrom());
            this.connection.sendResultFor(request, new Time(ZonedDateTime.now()));
        } else {
            this.connection.sendErrorFor(request, new Condition.Forbidden());
        }
    }

    // TODO add a zoneDateTimeWithTimeout and use that in the cache

    public ListenableFuture<ZonedDateTime> zonedDateTime(final Jid address) {
        Log.d(Config.LOGTAG, "requesting entity time: " + address);
        final var future = this.connection.sendIqPacket(new Iq(Iq.Type.GET, address, new Time()));
        return Futures.transform(
                future,
                iq -> {
                    final var time = Objects.requireNonNull(iq).getOnlyExtension(Time.class);
                    if (time == null) {
                        throw new IllegalArgumentException("No valid time extension in response");
                    }
                    final var zonedDateTime = time.asZonedDateTime();
                    if (zonedDateTime == null) {
                        throw new IllegalArgumentException("No valid zoned date time in response");
                    }
                    return zonedDateTime;
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<List<EntityTime>> getEntityTimes(final Jid address) {
        final var presences = getManager(PresenceManager.class).getPresences(address);
        final Collection<ListenableFuture<EntityTime>> futures =
                Collections2.transform(
                        presences,
                        p -> {
                            final var fullAddress = Objects.requireNonNull(p).getFrom();
                            final var infoQuery = getManager(DiscoManager.class).get(fullAddress);
                            if (infoQuery == null || !infoQuery.hasFeature(Namespace.TIME)) {
                                return Futures.immediateFuture(new NoEntityTime());
                            }
                            final var entityTimeFuture = entityTimeCache.getUnchecked(fullAddress);
                            return Futures.catching(
                                    entityTimeFuture,
                                    Exception.class,
                                    ex -> new InvalidEntityTime(),
                                    MoreExecutors.directExecutor());
                        });
        return Futures.allAsList(futures);
    }

    public ListenableFuture<ZonedDateTime> getZonedDateTime(final Jid address) {
        if (!this.appSettings.isEntityTime()) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("Requesting entity time is disabled"));
        }
        return Futures.transform(
                getEntityTimes(address),
                entityTimes -> {
                    if (entityTimes == null) {
                        throw new IllegalStateException("No entity times available");
                    }
                    final var unknownAsNulls =
                            Collections2.transform(
                                    entityTimes,
                                    entityTime -> {
                                        if (entityTime
                                                instanceof
                                                OffsetEntityTime(ZoneOffset zoneOffset)) {
                                            return zoneOffset;
                                        } else {
                                            return null;
                                        }
                                    });
                    final Set<ZoneOffset> zoneOffsets =
                            ImmutableSet.copyOf(
                                    Collections2.filter(unknownAsNulls, Objects::nonNull));
                    if (zoneOffsets.size() == 1) {
                        return Instant.now().atZone(Iterables.getOnlyElement(zoneOffsets));
                    } else {
                        throw new IllegalStateException("Ambiguous time zones");
                    }
                },
                MoreExecutors.directExecutor());
    }

    public static boolean isNightTime(final ZonedDateTime zonedDateTime) {
        final var localTime = zonedDateTime.toLocalTime();
        return localTime.isAfter(LocalTime.of(22, 0)) || localTime.isBefore(LocalTime.of(8, 0));
    }

    public static boolean isDifferentTimeZone(final ZonedDateTime zonedDateTime) {
        final var local = ZonedDateTime.now();
        return !local.getOffset().equals(zonedDateTime.getOffset());
    }

    public sealed interface EntityTime {}

    public record NoEntityTime() implements EntityTime {}

    public record OffsetEntityTime(ZoneOffset zoneOffset) implements EntityTime {}

    public record InvalidEntityTime() implements EntityTime {}
}
