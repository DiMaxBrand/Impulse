package im.conversations.android.xmpp.model.support;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

/** Ephemeral conversation status; no message body, receipt or unread count. */
@XmlElement(namespace = "urn:impulse:reply-status:0")
public class ReplyStatus extends Extension {
    public ReplyStatus() { super(ReplyStatus.class); }
    public static ReplyStatus create(String code, long stamp, long expires, boolean clearOnSend, String peer) {
        final ReplyStatus status = new ReplyStatus();
        status.setAttribute("state", code);
        status.setAttribute("updated", Long.toString(stamp));
        status.setAttribute("expires", Long.toString(expires));
        status.setAttribute("clear-on-send", Boolean.toString(clearOnSend));
        if (peer != null) status.setAttribute("peer", peer);
        return status;
    }
}
