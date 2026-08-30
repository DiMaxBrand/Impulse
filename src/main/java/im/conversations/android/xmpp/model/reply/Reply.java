package im.conversations.android.xmpp.model.reply;

import com.google.common.base.Strings;
import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(namespace = Namespace.REPLIES)
public class Reply extends Extension {

    public Reply() {
        super(Reply.class);
    }

    public String getId() {
        return Strings.emptyToNull(this.getAttribute("id"));
    }

    public String getTo() {
        return Strings.emptyToNull(this.getAttribute("to"));
    }

    public static Reply create(final String to, final String id) {
        final Reply reply = new Reply();
        reply.setAttribute("id", id);
        if (to != null) {
            reply.setAttribute("to", to);
        }
        return reply;
    }
}
