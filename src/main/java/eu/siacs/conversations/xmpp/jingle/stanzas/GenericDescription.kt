package eu.siacs.conversations.xmpp.jingle.stanzas

import com.google.common.base.Preconditions
import eu.siacs.conversations.xml.Element

open class GenericDescription internal constructor(name: String, namespace: String) : Element(name, namespace) {
    init {
        Preconditions.checkArgument("description" == name)
    }

    companion object {
        @JvmStatic
        fun upgrade(element: Element): GenericDescription {
            Preconditions.checkArgument("description" == element.name)
            return GenericDescription("description", element.namespace).apply {
                setAttributes(element.attributes)
                setChildren(element.children)
            }
        }
    }
}
