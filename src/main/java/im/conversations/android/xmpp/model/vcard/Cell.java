package im.conversations.android.xmpp.model.vcard;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

/**
 * Empty marker element flagging a TEL entry as a mobile number, per vcard-temp's TYPE parameters.
 */
@XmlElement(name = "CELL")
public class Cell extends Extension {
    public Cell() {
        super(Cell.class);
    }
}
