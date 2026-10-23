package eu.siacs.conversations.services

import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Message

/**
 * Photos with a caption are two messages: the photos, then the text, linked by a reply (XEP-0461)
 * to one of the photos that actually went through. The text is held back ([Message.STATUS_PAUSED])
 * while its photos are on their way, and is released as soon as they all have a verdict:
 *  - at least one photo was sent: the text goes, replying to the last photo that was sent;
 *  - none was: the text fails, saying why ("photos above were not sent"), and nothing is sent;
 *  - on a retry of that failed text: photos still in flight pause it again (it links to them when
 *    they are done); photos that stayed failed let it go out on its own, without a reply link.
 */
object CaptionResolver {
    private const val WINDOW_MILLIS = 5 * 60 * 1000L

    private fun inFlight(m: Message) =
        m.status == Message.STATUS_UNSEND ||
            m.status == Message.STATUS_WAITING ||
            m.status == Message.STATUS_OFFERED

    private fun delivered(m: Message) =
        m.status == Message.STATUS_SEND ||
            m.status == Message.STATUS_SEND_RECEIVED ||
            m.status == Message.STATUS_SEND_DISPLAYED

    private fun isOwnMedia(m: Message) =
        m.status != Message.STATUS_RECEIVED &&
            m.status != Message.STATUS_PAUSED &&
            m.isFileOrImage &&
            !m.isDeleted

    /** The photos a caption belongs to, oldest first. */
    @JvmStatic
    fun photosOf(caption: Message): List<Message> {
        val conversation = caption.conversation as? Conversation ?: return emptyList()
        val known = caption.captionFor
        if (known != null) {
            return known.mapNotNull { conversation.findMessageWithUuid(it) }
        }
        // After a restart: the run of own media right above the caption.
        val all = conversation.snapshotMessages()
        val index = all.indexOfFirst { it.getUuid() == caption.getUuid() }
        if (index <= 0) return emptyList()
        val run = ArrayList<Message>()
        var i = index - 1
        while (i >= 0) {
            val m = all[i]
            if (!isOwnMedia(m) || caption.timeSent - m.timeSent > WINDOW_MILLIS) break
            run.add(0, m)
            i--
        }
        return run
    }

    @JvmStatic
    fun resolve(service: XmppConnectionService, caption: Message, retry: Boolean) {
        val photos = photosOf(caption)
        when {
            photos.isEmpty() -> release(service, caption, null)
            photos.any(::inFlight) -> pause(service, caption, photos)
            else -> {
                val sent = photos.lastOrNull(::delivered)
                if (sent != null) {
                    release(service, caption, sent.serverMsgId ?: sent.getUuid())
                } else if (retry) {
                    release(service, caption, null)
                } else {
                    service.markMessage(
                        caption,
                        Message.STATUS_SEND_FAILED,
                        if (photos.size > 1) Message.ERROR_CAPTION_PHOTOS
                        else Message.ERROR_CAPTION_PHOTO,
                    )
                }
            }
        }
    }

    private fun pause(service: XmppConnectionService, caption: Message, photos: List<Message>) {
        caption.captionFor = photos.map { it.getUuid() }
        if (caption.status != Message.STATUS_PAUSED) {
            caption.repliedTo = null
            service.markMessage(caption, Message.STATUS_PAUSED)
        }
    }

    private fun release(service: XmppConnectionService, caption: Message, replyId: String?) {
        caption.repliedTo = replyId
        caption.captionFor = null
        service.markMessage(caption, Message.STATUS_WAITING)
        service.resendMessage(caption, false)
    }

    private fun isWaitingOrFailedForPhotos(caption: Message) =
        caption.status == Message.STATUS_PAUSED ||
            (caption.status == Message.STATUS_SEND_FAILED &&
                (caption.errorMessage == Message.ERROR_CAPTION_PHOTOS ||
                    caption.errorMessage == Message.ERROR_CAPTION_PHOTO))

    /** A message changed status: a paused caption waiting for it may be ready. */
    @JvmStatic
    fun onMessageChanged(service: XmppConnectionService, message: Message) {
        if (!message.isFileOrImage) return
        val conversation = message.conversation as? Conversation ?: return
        for (caption in conversation.snapshotMessages()) {
            if (isWaitingOrFailedForPhotos(caption) && photosOf(caption).any { it === message }) {
                resolve(service, caption, false)
            }
        }
    }

    /** Captions left paused (the app was closed meanwhile) are looked at again. */
    @JvmStatic
    fun resolveAll(service: XmppConnectionService, conversation: Conversation) {
        for (caption in conversation.snapshotMessages()) {
            if (caption.status == Message.STATUS_PAUSED) resolve(service, caption, false)
        }
    }
}
