package im.conversations.android.xmpp.model.commands;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

public abstract sealed class Action extends Extension {
    public Action(Class<? extends Action> clazz) {
        super(clazz);
    }

    @XmlElement
    public final class Prev extends Action {

        public Prev() {
            super(Prev.class);
        }
    }

    @XmlElement
    public final class Next extends Action {

        public Next() {
            super(Next.class);
        }
    }

    @XmlElement
    public final class Complete extends Action {

        public Complete() {
            super(Complete.class);
        }
    }
}
