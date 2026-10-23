package im.conversations.android.xmpp.model.support;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

/**
 * Support asks the phone to look for an update and say what it found, as a visible message in the
 * chat. Carries no body itself; only honoured when it comes from the support address. It never
 * starts an update: the person does that.
 */
@XmlElement(namespace = Namespace.SUPPORT)
public class CheckUpdates extends Extension {

    public CheckUpdates() {
        super(CheckUpdates.class);
    }
}
