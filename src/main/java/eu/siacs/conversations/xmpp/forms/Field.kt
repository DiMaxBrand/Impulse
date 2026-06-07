package eu.siacs.conversations.xmpp.forms

import eu.siacs.conversations.xml.Element
import eu.siacs.conversations.xml.Namespace
import java.util.HashMap

class Field : Element {

    constructor(name: String) : super("field", Namespace.DATA) {
        this.setAttribute("var", name)
    }

    private constructor() : super("field", Namespace.DATA)

    fun getFieldName(): String? = this.getAttribute("var")

    fun setValue(value: String?) {
        this.children.clear()
        this.addChild("value").setContent(value)
    }

    fun setValues(values: Collection<String?>) {
        this.children.clear()
        for (value in values) {
            this.addChild("value").setContent(value)
        }
    }

    fun removeNonValueChildren() {
        val iterator = this.children.iterator()
        while (iterator.hasNext()) {
            val element = iterator.next()
            if (element.getName() != "value") {
                iterator.remove()
            }
        }
    }

    fun getValue(): String? = findChildContent("value")

    fun getValues(): List<String?> {
        val values = mutableListOf<String?>()
        for (child in getChildren()) {
            if ("value" == child.getName()) {
                values.add(child.getContent())
            }
        }
        return values
    }

    fun getLabel(): String? = getAttribute("label")

    fun getType(): String? = getAttribute("type")

    fun isRequired(): Boolean = hasChild("required")

    companion object {
        @JvmStatic
        fun parse(element: Element): Field {
            val field = Field()
            field.setAttributes(HashMap(element.getAttributes()))
            field.setChildren(element.getChildren())
            return field
        }
    }
}
