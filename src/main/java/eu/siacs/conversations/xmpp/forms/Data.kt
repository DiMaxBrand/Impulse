package eu.siacs.conversations.xmpp.forms

import android.os.Bundle
import eu.siacs.conversations.xml.Element
import eu.siacs.conversations.xml.Namespace
import java.util.HashMap

class Data : Element("x", Namespace.DATA) {

    fun getFields(): List<Field> {
        val fields = mutableListOf<Field>()
        for (child in getChildren()) {
            if (child.getName() == "field" && FORM_TYPE != child.getAttribute("var")) {
                fields.add(Field.parse(child))
            }
        }
        return fields
    }

    fun getFieldByName(needle: String?): Field? {
        for (child in getChildren()) {
            if (child.getName() == "field" && needle == child.getAttribute("var")) {
                return Field.parse(child)
            }
        }
        return null
    }

    fun put(name: String, value: String?): Field {
        var field = getFieldByName(name)
        if (field == null) {
            field = Field(name)
            this.addChild(field)
        }
        field.setValue(value)
        return field
    }

    fun put(name: String, values: Collection<String?>) {
        var field = getFieldByName(name)
        if (field == null) {
            field = Field(name)
            this.addChild(field)
        }
        field.setValues(values)
    }

    fun submit(options: Bundle) {
        for (field in getFields()) {
            if (options.containsKey(field.getFieldName())) {
                field.setValue(options.getString(field.getFieldName()))
            }
        }
        submit()
    }

    private fun submit() {
        this.setAttribute("type", "submit")
        removeUnnecessaryChildren()
        for (field in getFields()) {
            field.removeNonValueChildren()
        }
    }

    private fun removeUnnecessaryChildren() {
        val iterator = this.children.iterator()
        while (iterator.hasNext()) {
            val element = iterator.next()
            if (element.getName() != "field" && element.getName() != "title") {
                iterator.remove()
            }
        }
    }

    fun setFormType(formType: String?) {
        val field = this.put(FORM_TYPE, formType)
        field.setAttribute("type", "hidden")
    }

    fun getValue(name: String?): String? {
        val field = this.getFieldByName(name)
        return field?.getValue()
    }

    fun getTitle(): String? = findChildContent("title")

    companion object {
        const val FORM_TYPE = "FORM_TYPE"

        @JvmStatic
        fun parse(element: Element): Data {
            val data = Data()
            data.setAttributes(HashMap(element.getAttributes()))
            data.setChildren(element.getChildren())
            return data
        }

        @JvmStatic
        fun create(type: String, bundle: Bundle): Data {
            val data = Data()
            data.setFormType(type)
            data.setAttribute("type", "submit")
            for (key in bundle.keySet()) {
                data.put(key, bundle.getString(key))
            }
            return data
        }
    }
}
