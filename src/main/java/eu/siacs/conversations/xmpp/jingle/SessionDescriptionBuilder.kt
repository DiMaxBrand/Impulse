package eu.siacs.conversations.xmpp.jingle

import com.google.common.collect.ArrayListMultimap

class SessionDescriptionBuilder {
    private var version: Int = 0
    private var name: String? = null
    private var connectionData: String? = null
    private var attributes: ArrayListMultimap<String, String>? = null
    private var media: List<SessionDescription.Media>? = null

    fun setVersion(version: Int): SessionDescriptionBuilder {
        this.version = version
        return this
    }

    fun setName(name: String?): SessionDescriptionBuilder {
        this.name = name
        return this
    }

    fun setConnectionData(connectionData: String?): SessionDescriptionBuilder {
        this.connectionData = connectionData
        return this
    }

    fun setAttributes(attributes: ArrayListMultimap<String, String>?): SessionDescriptionBuilder {
        this.attributes = attributes
        return this
    }

    fun setMedia(media: List<SessionDescription.Media>?): SessionDescriptionBuilder {
        this.media = media
        return this
    }

    fun createSessionDescription(): SessionDescription {
        return SessionDescription(version, name, connectionData, attributes, media)
    }
}
