package im.conversations.android.xmpp.model.vcard;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "TEL")
public class Tel extends Extension {
    public Tel() {
        super(Tel.class);
    }

    public String getNumber() {
        final var number = this.getExtension(TelNumber.class);
        return number == null ? null : number.getContent();
    }
}
