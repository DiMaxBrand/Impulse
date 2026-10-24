package im.conversations.android.xmpp.model.devices;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

/**
 * The list of the devices signed in to one account, published over PEP with the access model
 * "whitelist": only the account itself can read or change it. Each phone writes its own entry; any
 * of them may mark another one as left out of the account's status (see DevicesManager).
 */
@XmlElement(namespace = Namespace.DEVICES)
public class Devices extends Extension {

    public Devices() {
        super(Devices.class);
    }
}
