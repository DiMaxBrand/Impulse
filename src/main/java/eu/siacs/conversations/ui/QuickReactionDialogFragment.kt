package eu.siacs.conversations.ui

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.entities.Message

/** Toggles [emoji] into `message`'s own reactions (unchanged if already present, matching
 * AddReactionDialog/AddReactionActivity's existing "double-tap doesn't toggle off" behavior) and
 * sends it -- the one place this logic lives, reused by both the double-tap trigger and this
 * dialog's own message-bound save. */
fun applyQuickReaction(activity: XmppActivity, message: Message, emoji: String) {
    val aggregated = message.getAggregatedReactions()
    val updated = if (aggregated.ourReactions.contains(emoji)) {
        aggregated.ourReactions
    } else {
        aggregated.ourReactions + emoji
    }
    activity.sendReactions(message, updated.toSet())
}

/** "Quick Reactions" as an actual overlay card over whatever screen launched it -- Settings →
 * Interface, or (via [newInstance]) the double-tap-on-message trigger -- not a separate
 * full-screen destination. Matches how the existing add-reaction dialog presents (an AlertDialog
 * card over the chat, not its own screen), and the spec's own "elevated card" language.
 *
 * When shown with no arguments (the Settings entry), saving just persists the default choice.
 * When shown via [newInstance] (double-tap, first time or "keep asking"), saving both persists
 * the choice *and* applies it to that specific message immediately, via [applyQuickReaction]. */
class QuickReactionDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        // Transparent window background so only the Compose-drawn rounded Surface below shows —
        // otherwise the default dialog window paints its own opaque rectangle behind it.
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val conversationUuid = arguments?.getString(ARG_CONVERSATION_UUID)
        val messageUuid = arguments?.getString(ARG_MESSAGE_UUID)
        setContent {
            ImpulseExpressiveTheme {
                QuickReactionDialogCard(
                    onDismiss = { dismiss() },
                    onSaved = { emoji ->
                        if (conversationUuid != null && messageUuid != null) {
                            val activity = activity as? XmppActivity
                            val service = activity?.xmppConnectionService
                            val message = service
                                ?.findConversationByUuid(conversationUuid)
                                ?.findMessageWithUuid(messageUuid)
                            if (activity != null && message != null) {
                                applyQuickReaction(activity, message, emoji)
                            }
                        }
                    },
                )
            }
        }
    }

    companion object {
        const val TAG = "quick_reaction_dialog"
        private const val ARG_CONVERSATION_UUID = "conversation_uuid"
        private const val ARG_MESSAGE_UUID = "message_uuid"

        /** Bound to a specific message -- picking a choice both saves it as the remembered
         * default and sends it as a reaction on this message. */
        fun newInstance(conversationUuid: String, messageUuid: String): QuickReactionDialogFragment {
            return QuickReactionDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONVERSATION_UUID, conversationUuid)
                    putString(ARG_MESSAGE_UUID, messageUuid)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun QuickReactionDialogCard(onDismiss: () -> Unit, onSaved: (emoji: String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appSettings = androidx.compose.runtime.remember { AppSettings(context) }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(max = 400.dp),
    ) {
        QuickReactionPickerContent(
            initialEmoji = appSettings.quickReactionEmoji,
            initialRemember = appSettings.isQuickReactionRemember,
            onSave = { emoji, remember ->
                appSettings.setQuickReaction(emoji, remember)
                onSaved(emoji)
                onDismiss()
            },
        )
    }
}
