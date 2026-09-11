package eu.siacs.conversations.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.utils.OnboardingPreferences
import eu.siacs.conversations.xmpp.manager.MultiUserChatManager

/**
 * Compose port of the legacy `AddReactionDialog.java`/`dialog_add_reaction.xml` -- kept visually
 * close to the original rather than redesigned (matches TODO.md's build-order decision): a
 * connected row of shortcut emoji, a "..." button to the full [AddReactionActivity] picker, a
 * keyboard button that swaps the row for free-form emoji text entry, and the
 * [R.string.reaction_picker_hint] line explaining both -- all via the shared [ReactionShortcutRow]
 * / [ReactionMoreAndKeyboardRow] components also used by [QuickReactionPickerContent], so both
 * dialogs behave identically instead of drifting apart.
 *
 * Two real behavior changes from the legacy dialog:
 * - The shortcut row is no longer the static `Reaction.SUGGESTIONS` six -- heart and thumbs-up
 *   are always pinned first (see [buildShortcutEmojis]), then filled from [AppSettings]'s
 *   most-recently-used list. The one exception: a MUC with a restricted reaction allow-list of 6
 *   or fewer emoji pins the row to exactly that list (same as legacy, no pinning/MRU) -- restricted
 *   rooms lose the "...", keyboard, and hint entirely, since there's no full-picker or free-text
 *   entry to point to when the room enforces a fixed set.
 * - Tapping an emoji that's already your reaction on this message removes it (matches the
 *   double-tap quick-reaction's toggle behavior -- see [toggledReactions]), instead of the legacy
 *   picker's silent no-op.
 */
class AddReactionDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val conversationUuid = requireArguments().getString(ARG_CONVERSATION_UUID)!!
        val messageUuid = requireArguments().getString(ARG_MESSAGE_UUID)!!
        setContent {
            ImpulseExpressiveTheme {
                val activity = activity as? XmppActivity
                val service = activity?.xmppConnectionService
                val message = service
                    ?.findConversationByUuid(conversationUuid)
                    ?.findMessageWithUuid(messageUuid)
                if (activity != null && message != null) {
                    AddReactionDialogCard(
                        message = message,
                        onReactionsApplied = { emojis ->
                            var updated = message.getAggregatedReactions().ourReactions
                            emojis.forEach { updated = toggledReactions(updated, it) }
                            activity.sendReactions(message, updated.toSet())
                            dismiss()
                        },
                        onOpenMore = {
                            val intent = Intent(activity, AddReactionActivity::class.java)
                            intent.putExtra("conversation", conversationUuid)
                            intent.putExtra("message", messageUuid)
                            // A real, if approximate, "smoothly expand into the full picker"
                            // transition -- scales up from this card's own bounds rather than the
                            // system's default cross-fade. Anchored on the whole card (this
                            // ComposeView), not the tiny "..." button specifically: a true shared-
                            // element morph from a Compose dialog into a separate Activity isn't
                            // meaningful (they're not the same view hierarchy), so this is the
                            // closest real system primitive to what was asked for.
                            val options = androidx.core.app.ActivityOptionsCompat.makeScaleUpAnimation(
                                this@apply, 0, 0, this@apply.width, this@apply.height,
                            )
                            activity.startActivity(intent, options.toBundle())
                            dismiss()
                        },
                    )
                }
            }
        }
    }

    companion object {
        const val TAG = "add_reaction_dialog"
        private const val ARG_CONVERSATION_UUID = "conversation_uuid"
        private const val ARG_MESSAGE_UUID = "message_uuid"

        fun newInstance(conversationUuid: String, messageUuid: String): AddReactionDialogFragment {
            return AddReactionDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONVERSATION_UUID, conversationUuid)
                    putString(ARG_MESSAGE_UUID, messageUuid)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun AddReactionDialogCard(
    message: Message,
    onReactionsApplied: (List<String>) -> Unit,
    onOpenMore: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appSettings = remember { AppSettings(context) }
    val onboardingPrefs = remember { OnboardingPreferences(context) }

    val restrictions = remember(message) {
        val conversation = message.getConversation()
        if (conversation.getMode() == Conversational.MODE_SINGLE) {
            null
        } else {
            val mucOptions = conversation.getAccount().getXmppConnection()
                .getManager(MultiUserChatManager::class.java)
                .getState(conversation.getAddress().asBareJid())
            mucOptions?.getReactionsRestrictions()
        }
    }
    val allowList = restrictions?.allowList()
    val restricted = allowList != null && allowList.isNotEmpty() && allowList.size <= 6

    // Built through a plain Kotlin MutableList in both branches rather than returning the Java
    // Collection/List types (allowList(), AppSettings' java.util.List) directly -- mixing those
    // platform types as the two branches of one expression confuses Kotlin's type inference here
    // (manifests as a bogus "Cannot access class 'List'" error, not the type mismatch it actually
    // is).
    val shortcutEmojis: List<String> = remember(restricted, allowList) {
        val list = mutableListOf<String>()
        if (restricted) {
            allowList?.forEach { list.add(it) }
        } else {
            buildShortcutEmojis(appSettings.getRecentReactionEmojis()).forEach { list.add(it) }
        }
        list
    }

    var showKeyboardInput by remember { mutableStateOf(false) }
    var hasOpenedMore by remember { mutableStateOf(onboardingPrefs.hasOpenedMoreReactions) }
    val ourReactions = message.getAggregatedReactions().ourReactions

    fun apply(emojis: List<String>) {
        if (!restricted) emojis.forEach { appSettings.recordReactionEmojiUsed(it) }
        onReactionsApplied(emojis)
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(max = rememberMaxElevatedCardWidth()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            if (!showKeyboardInput) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    ReactionShortcutRow(
                        emojis = shortcutEmojis,
                        isChecked = { it in ourReactions },
                        onPicked = { emoji -> apply(listOf(emoji)) },
                        onOpenMore = onOpenMore,
                        hasOpenedMore = hasOpenedMore,
                        onMoreOpened = {
                            onboardingPrefs.hasOpenedMoreReactions = true
                            hasOpenedMore = true
                        },
                        showMoreButton = !restricted,
                    )
                }
            }

            if (!restricted) {
                Spacer(modifier = Modifier.height(12.dp))
                ReactionKeyboardRow(
                    onSubmitTyped = { emojis -> apply(emojis) },
                    showKeyboardInput = showKeyboardInput,
                    onShowKeyboardInputChange = { showKeyboardInput = it },
                )
            }
        }
    }
}
