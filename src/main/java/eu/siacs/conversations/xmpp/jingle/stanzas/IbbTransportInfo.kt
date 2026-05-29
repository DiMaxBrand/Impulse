package eu.siacs.conversations.xmpp.jingle.stanzas

import com.google.common.base.Preconditions
import com.google.common.base.Strings
import com.google.common.primitives.Longs
import eu.siacs.conversations.xml.Element
import eu.siacs.conversations.xml.Namespace

class IbbTransportInfo : GenericTransportInfo {

    private constructor(name: String, xmlns: String) : super(name, xmlns)

    constructor(transportId: String, blockSize: Int) : super("transport", Namespace.JINGLE_TRANSPORTS_IBB) {
        Preconditions.checkNotNull(transportId, "Transport ID can not be null")
        Preconditions.checkArgument(blockSize > 0, "Block size must be larger than 0")
        this.setAttribute("block-size", blockSize)
        this.setAttribute("sid", transportId)
    }

    fun getTransportId(): String? {
        return this.getAttribute("sid")
    }

    fun getBlockSize(): Long? {
        val blockSize = this.getAttribute("block-size")
        return if (Strings.isNullOrEmpty(blockSize)) null else Longs.tryParse(blockSize)
    }

    companion object {
        @JvmStatic
        fun upgrade(element: Element): IbbTransportInfo {
            Preconditions.checkArgument(
                "transport" == element.getName(),
                "Name of provided element is not transport"
            )
            Preconditions.checkArgument(
                Namespace.JINGLE_TRANSPORTS_IBB == element.getNamespace(),
                "Element does not match ibb transport namespace"
            )
            val transportInfo = IbbTransportInfo("transport", Namespace.JINGLE_TRANSPORTS_IBB)
            transportInfo.setAttributes(element.getAttributes())
            transportInfo.setChildren(element.getChildren())
            return transportInfo
        }
    }
}
