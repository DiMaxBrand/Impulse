package eu.siacs.conversations.ui.util

import eu.siacs.conversations.ui.ConversationFragment.ATTACHMENT_CHOICE_CHOOSE_IMAGE
import eu.siacs.conversations.ui.ConversationFragment.ATTACHMENT_CHOICE_LOCATION
import eu.siacs.conversations.ui.ConversationFragment.ATTACHMENT_CHOICE_RECORD_VIDEO
import eu.siacs.conversations.ui.ConversationFragment.ATTACHMENT_CHOICE_RECORD_VOICE
import eu.siacs.conversations.ui.ConversationFragment.ATTACHMENT_CHOICE_TAKE_PHOTO

enum class SendButtonAction {
    TEXT,
    TAKE_PHOTO,
    SEND_LOCATION,
    RECORD_VOICE,
    CANCEL,
    CHOOSE_PICTURE,
    RECORD_VIDEO;

    fun toChoice(): Int =
        when (this) {
            TAKE_PHOTO -> ATTACHMENT_CHOICE_TAKE_PHOTO
            SEND_LOCATION -> ATTACHMENT_CHOICE_LOCATION
            RECORD_VOICE -> ATTACHMENT_CHOICE_RECORD_VOICE
            CHOOSE_PICTURE -> ATTACHMENT_CHOICE_CHOOSE_IMAGE
            RECORD_VIDEO -> ATTACHMENT_CHOICE_RECORD_VIDEO
            else -> 0
        }

    companion object {
        @JvmStatic
        fun of(attachmentChoice: Int): SendButtonAction =
            when (attachmentChoice) {
                ATTACHMENT_CHOICE_LOCATION -> SEND_LOCATION
                ATTACHMENT_CHOICE_RECORD_VOICE -> RECORD_VOICE
                ATTACHMENT_CHOICE_RECORD_VIDEO -> RECORD_VIDEO
                ATTACHMENT_CHOICE_TAKE_PHOTO -> TAKE_PHOTO
                ATTACHMENT_CHOICE_CHOOSE_IMAGE -> CHOOSE_PICTURE
                else -> throw IllegalArgumentException("Not a known attachment choice")
            }
    }
}
