package eu.siacs.conversations.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.utils.OnboardingPreferences
import eu.siacs.conversations.xmpp.manager.MultiUserChatManager
import net.fellbaum.jemoji.EmojiManager

/**
 * Compose port of the legacy `AddReactionDialog.java`/`dialog_add_reaction.xml` -- kept visually
 * close to the original rather than redesigned (matches TODO.md's build-order decision): a row of
 * shortcut emoji, a "..." button to the full [AddReactionActivity] picker, a keyboard button that
 * swaps the row for free-form emoji text entry, and the [R.string.reaction_picker_hint] line
 * explaining both.
 *
 * One real behavior change from the legacy dialog: the shortcut row is no longer the static
 * [eu.siacs.conversations.entities.Reaction.SUGGESTIONS] six -- it's [AppSettings]'s
 * most-recently-used list (seeded with those same six until something's actually been picked),
 * via [AppSettings.getRecentReactionEmojis]/[AppSettings.recordReactionEmojiUsed]. The one
 * exception: a MUC with a restricted reaction allow-list of 6 or fewer emoji pins the row to
 * exactly that list (same as legacy) -- restricted rooms don't get a personalized/dynamic row,
 * and lose the "...", keyboard, and hint entirely, since there's no full-picker or free-text entry
 * to point to when the room enforces a fixed set.
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
                        onReactionPicked = { emoji ->
                            val aggregated = message.getAggregatedReactions()
                            val updated = if (aggregated.ourReactions.contains(emoji)) {
                                aggregated.ourReactions
                            } else {
                                aggregated.ourReactions + emoji
                            }
                            activity.sendReactions(message, updated.toSet())
                            dismiss()
                        },
                        onReactionsTyped = { emojis ->
                            val aggregated = message.getAggregatedReactions()
                            activity.sendReactions(message, (aggregated.ourReactions + emojis).toSet())
                            dismiss()
                        },
                        onOpenMore = {
                            val intent = Intent(activity, AddReactionActivity::class.java)
                            intent.putExtra("conversation", conversationUuid)
                            intent.putExtra("message", messageUuid)
                            activity.startActivity(intent)
                            dismiss()
                        },
                        onDismiss = { dismiss() },
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
    onReactionPicked: (String) -> Unit,
    onReactionsTyped: (List<String>) -> Unit,
    onOpenMore: () -> Unit,
    onDismiss: () -> Unit,
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
            appSettings.getRecentReactionEmojis().forEach { list.add(it) }
        }
        list
    }

    var showKeyboardInput by remember { mutableStateOf(false) }
    var typedText by remember { mutableStateOf("") }
    var hasOpenedMore by remember { mutableStateOf(onboardingPrefs.hasOpenedMoreReactions) }

    fun pick(emoji: String) {
        if (!restricted) appSettings.recordReactionEmojiUsed(emoji)
        onReactionPicked(emoji)
    }

    fun submitTyped() {
        val emojis = EmojiManager.extractEmojisInOrder(typedText).map { it.emoji }
        if (emojis.isNotEmpty()) {
            emojis.forEach { if (!restricted) appSettings.recordReactionEmojiUsed(it) }
            onReactionsTyped(emojis)
        }
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            if (showKeyboardInput) {
                OutlinedTextField(
                    value = typedText,
                    onValueChange = { typedText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.emoji_reactions)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitTyped() }),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    ButtonGroup(
                        overflowIndicator = {},
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        shortcutEmojis.forEachIndexed { index, emoji ->
                            val isLeading = index == 0
                            val isTrailing = index == shortcutEmojis.lastIndex
                            customItem(
                                buttonGroupContent = {
                                    val shape = when {
                                        shortcutEmojis.size == 1 -> CircleShape
                                        isLeading -> ButtonGroupDefaults.connectedLeadingButtonShape
                                        isTrailing -> ButtonGroupDefaults.connectedTrailingButtonShape
                                        else -> RoundedCornerShape(12.dp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(shape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = ripple(),
                                                onClick = { pick(emoji) },
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(text = emoji, fontSize = 22.sp)
                                    }
                                },
                                menuContent = {},
                            )
                        }
                    }
                }
            }

            if (!restricted) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box {
                        OutlinedIconButton(
                            onClick = {
                                onboardingPrefs.hasOpenedMoreReactions = true
                                hasOpenedMore = true
                                onOpenMore()
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_horiz_24dp),
                                contentDescription = stringResource(R.string.more_reactions),
                            )
                        }
                        if (!hasOpenedMore) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (showKeyboardInput) {
                                submitTyped()
                            } else {
                                showKeyboardInput = true
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(
                                if (showKeyboardInput) R.drawable.ic_send_24dp else R.drawable.ic_keyboard_24dp,
                            ),
                            contentDescription = null,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.reaction_picker_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
