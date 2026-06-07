package im.conversations.android.xmpp

import im.conversations.android.xmpp.model.pubsub.error.PubSubError
import im.conversations.android.xmpp.model.stanza.Iq

class PreconditionNotMetException(response: Iq) : PubSubErrorException(response) {

    init {
        if (this.pubSubError !is PubSubError.PreconditionNotMet) {
            throw AssertionError(
                "This exception should only be constructed for PreconditionNotMet errors"
            )
        }
    }
}
