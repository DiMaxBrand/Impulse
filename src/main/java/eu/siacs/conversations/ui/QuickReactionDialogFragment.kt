package eu.siacs.conversations.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.entities.Message

/** Applies [emojis] to `message`'s own reactions (each via [toggledReactions], in order) and
 * sends the result -- the one place this logic lives, reused by both the double-tap trigger and
 * this dialog's own message-bound save. Normally a single emoji, but the custom third slot can
 * hold several (see [AppSettings.getQuickReactionCustomEmojis]). */
fun applyQuickReaction(activity: XmppActivity, message: Message, emojis: List<String>) {
    var updated = message.getAggregatedReactions().ourReactions
    emojis.forEach { updated = toggledReactions(updated, it) }
    activity.sendReactions(message, updated.toSet())
}

/** "Quick Reactions" as an actual overlay card over whatever screen launched it -- Settings →
 * Interface, or (via [newInstance]) the double-tap-on-message trigger -- not a separate
 * full-screen destination. Matches how the existing add-reaction dialog presents (an AlertDialog
 * card over the chat, not its own screen), and the spec's own "elevated card" language.
 *
 * The "..."/keyboard row (and the custom third slot they edit) behaves identically in both
 * contexts -- it's not about "acting on a message" at all, it's part of configuring what the
 * quick-reaction defaults *are*, which is exactly as relevant from Settings as from a live
 * double-tap. The only thing that differs by context is what picking/saving *also* does: from
 * Settings it only persists; via [newInstance] (double-tap) it persists *and* applies the result
 * to that specific message immediately, via [applyQuickReaction]. */
class QuickReactionDialogFragment : DialogFragment() {

    // Must be registered unconditionally before the fragment reaches CREATED -- a property
    // initializer runs at construction time, before any lifecycle callback, which satisfies that.
    private val pickEmojiLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val emoji = result.data?.getStringExtra(AddReactionActivity.EXTRA_PICKED_EMOJI)
            if (emoji != null) pickedCustomEmoji.value = listOf(emoji)
        }
    }

    // Bridges the launcher's callback (fires outside composition) into Compose state.
    private val pickedCustomEmoji = mutableStateOf<List<String>?>(null)

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
                val activity = activity as? XmppActivity
                val message = if (conversationUuid != null && messageUuid != null) {
                    activity?.xmppConnectionService
                        ?.findConversationByUuid(conversationUuid)
                        ?.findMessageWithUuid(messageUuid)
                } else {
                    null
                }
                QuickReactionDialogCard(
                    onDismiss = { dismiss() },
                    onSaved = { emojis ->
                        if (activity != null && message != null) {
                            applyQuickReaction(activity, message, emojis)
                        }
                    },
                    onOpenMore = {
                        // Approximate "smooth expand into the full picker" -- scales up from this
                        // card's own bounds (the whole ComposeView, not just the "..." button;
                        // a true shared-element morph across a Compose-dialog/Activity boundary
                        // isn't meaningful, they're separate view hierarchies) rather than the
                        // system's default cross-fade.
                        val options = androidx.core.app.ActivityOptionsCompat.makeScaleUpAnimation(
                            this@apply, 0, 0, this@apply.width, this@apply.height,
                        )
                        pickEmojiLauncher.launch(AddReactionActivity.pickerIntent(requireContext()), options)
                    },
                    pickedCustomEmoji = pickedCustomEmoji,
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
private fun QuickReactionDialogCard(
    onDismiss: () -> Unit,
    onSaved: (emojis: List<String>) -> Unit,
    onOpenMore: () -> Unit,
    pickedCustomEmoji: androidx.compose.runtime.MutableState<List<String>?>,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appSettings = androidx.compose.runtime.remember { AppSettings(context) }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(max = rememberMaxElevatedCardWidth()),
    ) {
        QuickReactionPickerContent(
            initialEmojis = appSettings.quickReactionEmojis,
            initialRemember = appSettings.isQuickReactionRemember,
            initialCustomEmojis = appSettings.quickReactionCustomEmojis,
            pickedCustomEmoji = pickedCustomEmoji,
            onSave = { emojis, customEmojis, remember ->
                appSettings.setQuickReaction(emojis, remember)
                appSettings.setQuickReactionCustomEmojis(customEmojis)
                onSaved(emojis)
                onDismiss()
            },
            onOpenMore = onOpenMore,
        )
    }
}
