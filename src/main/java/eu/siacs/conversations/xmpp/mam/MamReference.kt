package eu.siacs.conversations.xmpp.mam

class MamReference @JvmOverloads constructor(
    val timestamp: Long,
    val reference: String? = null
) {

    fun greaterThan(b: MamReference): Boolean = timestamp > b.timestamp

    fun greaterThan(b: Long): Boolean = timestamp > b

    fun timeOnly(): MamReference = if (reference == null) this else MamReference(timestamp)

    companion object {
        @JvmStatic
        fun max(a: MamReference?, b: MamReference?): MamReference? {
            return when {
                a != null && b != null -> if (a.timestamp > b.timestamp) a else b
                a != null -> a
                else -> b
            }
        }

        @JvmStatic
        fun max(a: MamReference?, b: Long): MamReference? = max(a, MamReference(b))

        @JvmStatic
        fun fromAttribute(attr: String?): MamReference {
            if (attr == null) return MamReference(0)
            val attrs = attr.split(":")
            return try {
                val timestamp = attrs[0].toLong()
                if (attrs.size >= 2) MamReference(timestamp, attrs[1])
                else MamReference(timestamp)
            } catch (e: Exception) {
                MamReference(0)
            }
        }
    }
}
