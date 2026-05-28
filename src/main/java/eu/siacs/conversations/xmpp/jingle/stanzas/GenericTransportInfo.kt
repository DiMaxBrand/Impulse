package eu.siacs.conversations.xmpp.jingle.stanzas

import com.google.common.base.Preconditions
import eu.siacs.conversations.xml.Element

open class GenericTransportInfo protected constructor(name: String, xmlns: String) : Element(name, xmlns) {
    companion object {
        @JvmStatic
        fun upgrade(element: Element): GenericTransportInfo {
            Preconditions.checkArgument("transport" == element.name)
            return GenericTransportInfo("transport", element.namespace).apply {
                setAttributes(element.attributes)
                setChildren(element.children)
            }
        }
    }
}
