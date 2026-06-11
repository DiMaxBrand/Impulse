package eu.siacs.conversations.ui.util

import android.content.Context
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.ui.Activities
import im.conversations.android.xmpp.model.stanza.Presence

object SendButtonTool {

    @JvmStatic
    fun getAction(context: Context?, c: Conversation, text: String): SendButtonAction {
        if (context == null) {
            return SendButtonAction.TEXT
        }
        val empty = text.isEmpty()
        val conference = c.getMode() == Conversational.MODE_MULTI
        return if (c.correctingMessage != null
            && (empty || text == c.correctingMessage.body)
        ) {
            SendButtonAction.CANCEL
        } else if (conference && !c.getAccount().httpUploadAvailable()) {
            if (empty && c.nextCounterpart != null) {
                SendButtonAction.CANCEL
            } else {
                SendButtonAction.TEXT
            }
        } else {
            if (empty) {
                if (conference && c.nextCounterpart != null) {
                    SendButtonAction.CANCEL
                } else {
                    AppSettings(context).quickAction
                }
            } else {
                SendButtonAction.TEXT
            }
        }
    }

    @JvmStatic
    @DrawableRes
    fun getSendButtonImageResource(action: SendButtonAction): Int {
        return when (action) {
            SendButtonAction.TEXT -> R.drawable.ic_send_24dp
            SendButtonAction.TAKE_PHOTO -> R.drawable.ic_camera_alt_24dp
            SendButtonAction.SEND_LOCATION -> R.drawable.ic_location_pin_24dp
            SendButtonAction.CHOOSE_PICTURE -> R.drawable.ic_image_24dp
            SendButtonAction.RECORD_VIDEO -> R.drawable.ic_videocam_24dp
            SendButtonAction.RECORD_VOICE -> R.drawable.ic_mic_24dp
            SendButtonAction.CANCEL -> R.drawable.ic_cancel_24dp
        }
    }

    @JvmStatic
    @ColorInt
    fun getSendButtonColor(view: View, status: Presence.Availability): Int {
        val nightMode = Activities.isNightMode(view.context)
        return when (status) {
            Presence.Availability.OFFLINE ->
                MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface)
            Presence.Availability.ONLINE, Presence.Availability.CHAT ->
                MaterialColors.harmonizeWithPrimary(
                    view.context,
                    ContextCompat.getColor(
                        view.context,
                        if (nightMode) R.color.green_300 else R.color.green_800
                    )
                )
            Presence.Availability.AWAY ->
                MaterialColors.harmonizeWithPrimary(
                    view.context,
                    ContextCompat.getColor(
                        view.context,
                        if (nightMode) R.color.amber_300 else R.color.amber_800
                    )
                )
            Presence.Availability.XA ->
                MaterialColors.harmonizeWithPrimary(
                    view.context,
                    ContextCompat.getColor(
                        view.context,
                        if (nightMode) R.color.orange_300 else R.color.orange_800
                    )
                )
            Presence.Availability.DND ->
                MaterialColors.harmonizeWithPrimary(
                    view.context,
                    ContextCompat.getColor(
                        view.context,
                        if (nightMode) R.color.red_300 else R.color.red_800
                    )
                )
        }
    }
}
