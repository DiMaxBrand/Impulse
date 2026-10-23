package im.conversations.android.xmpp.model.support;

import com.google.common.base.Strings;
import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

/**
 * Something support tracks for the person (a bug fix, improvement, new feature or change), as a
 * stanza with no body: nothing appears in the chat. Carries the tracking ID, the kind and a title
 * in English and in Russian. Only honoured when it comes from the support address.
 */
@XmlElement(namespace = Namespace.SUPPORT)
public class Track extends Extension {

    public Track() {
        super(Track.class);
    }

    public String getId() {
        return Strings.emptyToNull(this.getAttribute("id"));
    }

    public String getKind() {
        return Strings.emptyToNull(this.getAttribute("kind"));
    }

    public String getTitleEn() {
        return Strings.nullToEmpty(this.getAttribute("title-en"));
    }

    public String getTitleRu() {
        return Strings.nullToEmpty(this.getAttribute("title-ru"));
    }

    /** Whether the phone should look for an update right away. */
    public boolean checkNow() {
        return !"false".equals(this.getAttribute("check"));
    }

    public static Track create(
            final String id,
            final String kind,
            final String titleEn,
            final String titleRu,
            final boolean checkNow) {
        final Track track = new Track();
        track.setAttribute("id", id);
        track.setAttribute("kind", kind);
        if (!Strings.isNullOrEmpty(titleEn)) {
            track.setAttribute("title-en", titleEn);
        }
        if (!Strings.isNullOrEmpty(titleRu)) {
            track.setAttribute("title-ru", titleRu);
        }
        track.setAttribute("check", checkNow ? "true" : "false");
        return track;
    }
}
