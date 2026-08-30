package im.conversations.android.xmpp.model.vcard;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "NUMBER")
public class TelNumber extends Extension {
    public TelNumber() {
        super(TelNumber.class);
    }
}
