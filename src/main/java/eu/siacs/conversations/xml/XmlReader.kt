package eu.siacs.conversations.xml

import android.util.Xml
import com.google.common.base.Strings
import com.google.common.collect.ImmutableMap
import com.google.common.io.Closeables
import im.conversations.android.xmpp.ExtensionFactory
import im.conversations.android.xmpp.model.StreamElement
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

class XmlReader : Closeable {

    private var parserInputStream: ParserInputStream? = null

    fun setInputStream(inputStream: InputStream) {
        this.parserInputStream = of(inputStream)
    }

    fun reset() {
        val current = this.parserInputStream
            ?: throw IOException("Unable to reset. No current parser")
        this.parserInputStream = of(current.inputStream)
    }

    override fun close() {
        val current = this.parserInputStream
        if (current != null) {
            this.parserInputStream = null
            Closeables.closeQuietly(current.inputStream)
        }
    }

    @Throws(IOException::class)
    fun readTag(): Tag {
        try {
            while (parserInputStream != null &&
                parserInputStream!!.parser.next() != XmlPullParser.END_DOCUMENT) {
                val parser = parserInputStream!!.parser
                if (parserInputStream!!.parser.eventType == XmlPullParser.START_TAG) {
                    val id = ExtensionFactory.Id(parser.name, parserInputStream!!.parser.namespace)
                    val attrBuilder = ImmutableMap.Builder<String, String>()
                    for (i in 0 until parser.attributeCount) {
                        val value = parser.getAttributeValue(i)
                        val prefix = parser.getAttributePrefix(i)
                        val name: String =
                            if (Strings.isNullOrEmpty(prefix)) {
                                parser.getAttributeName(i)
                            } else {
                                "$prefix:${parser.getAttributeName(i)}"
                            }
                        if (value != null) {
                            attrBuilder.put(name, value)
                        }
                    }
                    return Tag.Start(id, attrBuilder.buildKeepingLast())
                } else if (parser.eventType == XmlPullParser.END_TAG) {
                    val id = ExtensionFactory.Id(parser.name, parser.namespace)
                    return Tag.End(id)
                } else if (parser.eventType == XmlPullParser.TEXT) {
                    return Tag.No(parser.text)
                }
            }
        } catch (e: IOException) {
            throw e
        } catch (throwable: Throwable) {
            throw IOException(
                "xml parser mishandled ${throwable.javaClass.simpleName}(${throwable.message})",
                throwable
            )
        }
        throw EOFException()
    }

    @Throws(IOException::class)
    fun <T : StreamElement> readElement(current: Tag.Start, clazz: Class<T>): T {
        val element = readElement(current)
        if (clazz.isInstance(element)) {
            return clazz.cast(element)
        }
        throw IOException(
            String.format("Read unexpected {%s}%s", element.namespace, element.name)
        )
    }

    @Throws(IOException::class)
    fun readElement(currentTag: Tag.Start): Element {
        return readElement(currentTag, 0)
    }

    @Throws(IOException::class)
    private fun readElement(parent: Tag.Start, depth: Int): Element {
        if (depth >= XML_ELEMENT_MAX_DEPTH) {
            throw XmlMaxDepthReachedException()
        }
        val id = parent.id
        val element = ExtensionFactory.create(id)
        element.setAttributes(parent.attributes)
        while (true) {
            when (val tag = this.readTag()) {
                is Tag.Start -> {
                    val child = this.readElement(tag, depth + 1)
                    element.addChild(child)
                }
                is Tag.No -> {
                    if (element.getChildren().isEmpty()) {
                        element.setContent(tag.text)
                    }
                }
                is Tag.End -> {
                    if (tag.id == id) {
                        return element
                    } else {
                        throw IOException("End tag did not match start tag")
                    }
                }
                else -> throw IOException("Read invalid tag ${tag.javaClass}")
            }
        }
    }

    class XmlMaxDepthReachedException : IOException("Reached maximum depth of XML stream")

    private data class ParserInputStream(val parser: XmlPullParser, val inputStream: InputStream)

    companion object {
        private const val XML_ELEMENT_MAX_DEPTH = 128

        @Throws(IOException::class)
        private fun of(inputStream: InputStream): ParserInputStream {
            val parser = Xml.newPullParser()
            try {
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                parser.setInput(InputStreamReader(inputStream))
                return ParserInputStream(parser, inputStream)
            } catch (e: XmlPullParserException) {
                throw IOException(e)
            }
        }
    }
}
