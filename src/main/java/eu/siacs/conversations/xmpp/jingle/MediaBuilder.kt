package eu.siacs.conversations.xmpp.jingle

import com.google.common.base.Joiner
import com.google.common.collect.Multimap

class MediaBuilder {
    private var media: String? = null
    private var port: Int = 0
    private var protocol: String? = null
    private var format: String? = null
    private var connectionData: String? = null
    private var attributes: Multimap<String, String>? = null

    fun setMedia(media: String?): MediaBuilder {
        this.media = media
        return this
    }

    fun setPort(port: Int): MediaBuilder {
        this.port = port
        return this
    }

    fun setProtocol(protocol: String?): MediaBuilder {
        this.protocol = protocol
        return this
    }

    fun setFormats(formats: List<Int>): MediaBuilder {
        this.format = Joiner.on(' ').join(formats)
        return this
    }

    fun setFormat(format: String?): MediaBuilder {
        this.format = format
        return this
    }

    fun setConnectionData(connectionData: String?): MediaBuilder {
        this.connectionData = connectionData
        return this
    }

    fun setAttributes(attributes: Multimap<String, String>?): MediaBuilder {
        this.attributes = attributes
        return this
    }

    fun createMedia(): SessionDescription.Media {
        return SessionDescription.Media(media, port, protocol, format, connectionData, attributes)
    }
}
