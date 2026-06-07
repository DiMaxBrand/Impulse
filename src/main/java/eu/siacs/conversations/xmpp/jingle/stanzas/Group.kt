package eu.siacs.conversations.xmpp.jingle.stanzas

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import eu.siacs.conversations.xml.Element
import eu.siacs.conversations.xml.Namespace

class Group : Element {

    private constructor() : super("group", Namespace.JINGLE_APPS_GROUPING)

    constructor(semantics: String, identificationTags: Collection<String>) : super("group", Namespace.JINGLE_APPS_GROUPING) {
        this.setAttribute("semantics", semantics)
        for (tag in identificationTags) {
            this.addChild(Element("content").setAttribute("name", tag))
        }
    }

    fun getSemantics(): String? {
        return this.getAttribute("semantics")
    }

    fun getIdentificationTags(): List<String> {
        val builder = ImmutableList.builder<String>()
        for (child in this.children) {
            if ("content" == child.getName()) {
                val name = child.getAttribute("name")
                if (name != null) {
                    builder.add(name)
                }
            }
        }
        return builder.build()
    }

    companion object {
        @JvmStatic
        fun ofSdpString(input: String): Group? {
            val tagBuilder = ImmutableList.builder<String>()
            val parts = input.split(" ")
            if (parts.size >= 2) {
                val semantics = parts[0]
                for (i in 1 until parts.size) {
                    tagBuilder.add(parts[i])
                }
                return Group(semantics, tagBuilder.build())
            }
            return null
        }

        @JvmStatic
        fun upgrade(element: Element): Group {
            Preconditions.checkArgument("group" == element.getName())
            Preconditions.checkArgument(Namespace.JINGLE_APPS_GROUPING == element.getNamespace())
            val group = Group()
            group.setAttributes(element.getAttributes())
            group.setChildren(element.getChildren())
            return group
        }
    }
}
