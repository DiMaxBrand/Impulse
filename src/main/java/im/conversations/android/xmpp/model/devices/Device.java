package im.conversations.android.xmpp.model.devices;

import com.google.common.base.Strings;
import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

/** One device of the account: see {@link Devices}. */
@XmlElement(namespace = Namespace.DEVICES)
public class Device extends Extension {

    public Device() {
        super(Device.class);
    }

    public String getDeviceId() {
        return Strings.nullToEmpty(this.getAttribute("id"));
    }

    public String getResource() {
        return Strings.nullToEmpty(this.getAttribute("resource"));
    }

    public String getDeviceName() {
        return Strings.nullToEmpty(this.getAttribute("name"));
    }

    public String getVersion() {
        return Strings.nullToEmpty(this.getAttribute("version"));
    }

    public boolean isExcluded() {
        return "true".equals(this.getAttribute("excluded"));
    }

    /** When a request that this device be left out lapses if the device has not claimed it. */
    public long getUntil() {
        return parse(this.getAttribute("until"));
    }

    public long getSeen() {
        return parse(this.getAttribute("seen"));
    }

    private static long parse(final String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (final NumberFormatException e) {
            return 0L;
        }
    }

    public static Device create(
            final String id,
            final String resource,
            final String name,
            final String version,
            final boolean excluded,
            final long until,
            final long seen) {
        final Device device = new Device();
        if (!Strings.isNullOrEmpty(id)) device.setAttribute("id", id);
        device.setAttribute("resource", resource);
        if (!Strings.isNullOrEmpty(name)) device.setAttribute("name", name);
        if (!Strings.isNullOrEmpty(version)) device.setAttribute("version", version);
        if (excluded) device.setAttribute("excluded", "true");
        if (until > 0) device.setAttribute("until", Long.toString(until));
        if (seen > 0) device.setAttribute("seen", Long.toString(seen));
        return device;
    }
}
