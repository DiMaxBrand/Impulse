package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import android.util.Log;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.time.Time;
import java.time.ZonedDateTime;
import java.util.Objects;

public class EntityTimeManager extends AbstractManager {

    public EntityTimeManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public void request(final Iq request) {
        final var appSettings = new AppSettings(this.context);
        if (appSettings.isUseTor() || getAccount().isOnion()) {
            this.connection.sendErrorFor(request, new Condition.Forbidden());
            return;
        }
        Log.d(
                Config.LOGTAG,
                getAccount().getJid().asBareJid()
                        + ": responding to entity time request from "
                        + request.getFrom());
        this.connection.sendResultFor(request, new Time(ZonedDateTime.now()));
    }

    public ListenableFuture<ZonedDateTime> zonedDateTime(final Jid address) {
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
}
