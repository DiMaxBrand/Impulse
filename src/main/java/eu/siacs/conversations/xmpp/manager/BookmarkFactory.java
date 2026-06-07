package eu.siacs.conversations.xmpp.manager;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.model.Bookmark;
import im.conversations.android.model.ImmutableBookmark;
import im.conversations.android.xmpp.model.bookmark2.Extensions;
import org.jspecify.annotations.Nullable;

/** Java bridge for ImmutableBookmark (annotation-processor generated, not visible to Kotlin). */
public final class BookmarkFactory {
    private BookmarkFactory() {}

    public static Bookmark create(
            Account account,
            Jid address,
            @Nullable String name,
            boolean autoJoin,
            @Nullable String nick,
            @Nullable String password) {
        return ImmutableBookmark.builder()
                .account(account)
                .address(address)
                .name(name)
                .isAutoJoin(autoJoin)
                .nick(nick)
                .password(password)
                .build();
    }

    public static Bookmark createWithExtensions(
            Account account,
            Jid address,
            @Nullable String name,
            boolean autoJoin,
            @Nullable String nick,
            @Nullable String password,
            @Nullable Extensions extensions) {
        return ImmutableBookmark.builder()
                .account(account)
                .address(address)
                .name(name)
                .isAutoJoin(autoJoin)
                .nick(nick)
                .password(password)
                .extensions(extensions)
                .build();
    }
}
