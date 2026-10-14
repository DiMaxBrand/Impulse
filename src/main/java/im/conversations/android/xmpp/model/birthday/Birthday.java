package im.conversations.android.xmpp.model.birthday;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

/**
 * A person's birthday, published over PEP with the access model "presence" so only people on the
 * roster can read it. The text is an ISO 8601 date, {@code 1950-10-15}, or {@code --10-15} when the
 * year is not shared. A node of our own rather than vCard4, so we never overwrite whatever other
 * clients keep in that person's vCard4 item.
 */
@XmlElement(namespace = Namespace.BIRTHDAY)
public class Birthday extends Extension {

    public Birthday() {
        super(Birthday.class);
    }

    public Birthday(final String value) {
        this();
        this.setContent(value);
    }
}
