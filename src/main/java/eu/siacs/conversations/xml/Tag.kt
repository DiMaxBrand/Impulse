package eu.siacs.conversations.xml

import im.conversations.android.xmpp.ExtensionFactory
import im.conversations.android.xmpp.model.Extension

sealed class Tag {

    sealed class IdentifiableTag(val id: ExtensionFactory.Id) : Tag() {

        fun `is`(clazz: Class<out Extension>): Boolean {
            return ExtensionFactory.id(clazz) == this.id
        }
    }

    sealed class StartOrEmpty(id: ExtensionFactory.Id, val attributes: Map<String, String>) :
        IdentifiableTag(id) {

        fun getAttribute(name: String): String? {
            return this.attributes[name]
        }
    }

    class Start(id: ExtensionFactory.Id, attributes: Map<String, String>) :
        StartOrEmpty(id, attributes)

    class End(id: ExtensionFactory.Id) : IdentifiableTag(id)

    class Empty(id: ExtensionFactory.Id, attributes: Map<String, String>) :
        StartOrEmpty(id, attributes)

    class No(val text: String) : Tag()
}
