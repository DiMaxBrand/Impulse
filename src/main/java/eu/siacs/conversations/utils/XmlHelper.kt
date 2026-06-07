package eu.siacs.conversations.utils

import com.google.common.base.Joiner
import com.google.common.collect.Lists
import eu.siacs.conversations.xml.Element

object XmlHelper {
    @JvmStatic
    fun printElementNames(element: Element?): String {
        val features =
            if (element == null) emptyList<String?>()
            else Lists.transform(element.children) { child -> child?.name }
        return Joiner.on(", ").join(features)
    }
}
