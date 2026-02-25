package eu.siacs.conversations.xml;

import android.util.Log;
import android.util.Xml;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closeables;
import eu.siacs.conversations.Config;
import im.conversations.android.xmpp.ExtensionFactory;
import im.conversations.android.xmpp.model.StreamElement;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.jspecify.annotations.NonNull;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class XmlReader implements Closeable {

    private static final int XML_ELEMENT_MAX_DEPTH = 128;

    private final XmlPullParser parser;
    private InputStream is;

    public XmlReader() {
        this.parser = Xml.newPullParser();
        try {
            this.parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        } catch (XmlPullParserException e) {
            Log.d(Config.LOGTAG, "error setting namespace feature on parser");
        }
    }

    public void setInputStream(final InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IOException();
        }
        this.is = inputStream;
        try {
            parser.setInput(new InputStreamReader(this.is));
        } catch (XmlPullParserException e) {
            throw new IOException("error resetting parser");
        }
    }

    public void reset() throws IOException {
        if (this.is == null) {
            throw new IOException();
        }
        try {
            parser.setInput(new InputStreamReader(this.is));
        } catch (XmlPullParserException e) {
            throw new IOException("error resetting parser");
        }
    }

    @Override
    public void close() {
        final var current = this.is;
        Closeables.closeQuietly(current);
        this.is = null;
    }

    public @NonNull Tag readTag() throws IOException {
        try {
            while (this.is != null && parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    final var id = new ExtensionFactory.Id(parser.getName(), parser.getNamespace());
                    final var attrBuilder = new ImmutableMap.Builder<String, String>();
                    for (int i = 0; i < parser.getAttributeCount(); ++i) {
                        // TODO we would also look at parser.getAttributeNamespace()
                        final var value = parser.getAttributeValue(i);
                        final var prefix = parser.getAttributePrefix(i);
                        final String name;
                        if (Strings.isNullOrEmpty(prefix)) {
                            name = parser.getAttributeName(i);
                        } else {
                            name = prefix + ":" + parser.getAttributeName(i);
                        }
                        if (name != null && value != null) {
                            attrBuilder.put(name, value);
                        }
                    }
                    return new Tag.Start(id, attrBuilder.buildKeepingLast());
                } else if (parser.getEventType() == XmlPullParser.END_TAG) {
                    final var id = new ExtensionFactory.Id(parser.getName(), parser.getNamespace());
                    return new Tag.End(id);
                } else if (parser.getEventType() == XmlPullParser.TEXT) {
                    return new Tag.No(parser.getText());
                }
            }

        } catch (final IOException e) {
            throw e;
        } catch (final Throwable throwable) {
            throw new IOException(
                    "xml parser mishandled "
                            + throwable.getClass().getSimpleName()
                            + "("
                            + throwable.getMessage()
                            + ")",
                    throwable);
        }
        throw new EOFException();
    }

    public <T extends StreamElement> T readElement(final Tag.Start current, final Class<T> clazz)
            throws IOException {
        final Element element = readElement(current);
        if (clazz.isInstance(element)) {
            return clazz.cast(element);
        }
        throw new IOException(
                String.format("Read unexpected {%s}%s", element.getNamespace(), element.getName()));
    }

    public Element readElement(final Tag currentTag) throws IOException {
        return readElement(currentTag, 0);
    }

    private Element readElement(final Tag currentTag, final int depth) throws IOException {
        if (depth >= XML_ELEMENT_MAX_DEPTH) {
            throw new XmlMaxDepthReachedException();
        }
        final ExtensionFactory.Id id;
        final Element element;
        if (currentTag instanceof Tag.Start start) {
            id = start.getId();
            element = ExtensionFactory.create(id);
            element.setAttributes(start.getAttributes());
        } else {
            throw new IOException("Cannot start reading element at tag other than start");
        }
        while (true) {
            final var tag = this.readTag();
            System.out.println("encountered inner tag: " + tag.getClass());
            switch (tag) {
                case Tag.Start innerStart -> {
                    final var child = this.readElement(innerStart, depth + 1);
                    element.addChild(child);
                }
                case Tag.No no -> {
                    if (element.getChildren().isEmpty()) {
                        element.setContent(no.getText());
                    }
                }
                case Tag.End end -> {
                    if (end.getId().equals(id)) {
                        return element;
                    } else {
                        throw new IOException("End tag did not match start tag");
                    }
                }
                default -> throw new IOException("Read invalid tag " + tag.getClass());
            }
        }
    }

    public static class XmlMaxDepthReachedException extends IOException {
        public XmlMaxDepthReachedException() {
            super("Reached maximum depth of XML stream");
        }
    }
}
