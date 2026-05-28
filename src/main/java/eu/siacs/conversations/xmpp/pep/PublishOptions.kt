package eu.siacs.conversations.xmpp.pep

import android.os.Bundle
import eu.siacs.conversations.xml.Namespace
import im.conversations.android.xmpp.model.stanza.Iq

object PublishOptions {
    @JvmStatic
    fun openAccess(): Bundle = Bundle().apply {
        putString("pubsub#access_model", "open")
    }

    @JvmStatic
    fun preconditionNotMet(response: Iq): Boolean {
        val error = if (response.type == Iq.Type.ERROR) response.findChild("error") else null
        return error != null && error.hasChild("precondition-not-met", Namespace.PUB_SUB_ERROR)
    }
}
