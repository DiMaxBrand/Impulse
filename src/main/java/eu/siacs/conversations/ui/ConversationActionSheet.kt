package eu.siacs.conversations.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.utils.TimeFrameUtils
import kotlinx.coroutines.launch

/** What the long-press sheet in the chat list can do to one conversation. */
interface ConversationListActions {
    fun togglePinned(conversation: Conversation)
    fun setMutedTill(conversation: Conversation, till: Long)
    fun markRead(conversation: Conversation)
    fun archive(conversation: Conversation)
    fun openContact(conversation: Conversation)
}

/** Nothing happens: for previews, where no row is ever pressed. */
object NoConversationListActions : ConversationListActions {
    override fun togglePinned(conversation: Conversation) {}
    override fun setMutedTill(conversation: Conversation, till: Long) {}
    override fun markRead(conversation: Conversation) {}
    override fun archive(conversation: Conversation) {}
    override fun openContact(conversation: Conversation) {}
}

/** One line in the sheet. Rows are grouped, so each one knows where it sits in its group. */
private class SheetRow(val icon: Int, val label: String, val onClick: () -> Unit)

/**
 * Long-press on a chat in the list. Pin, mute (with the same durations as the chat screen), mark
 * as read, archive, and the contact's details for a one-to-one chat. Every action closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationActionSheet(
    conversation: Conversation,
    actions: ConversationListActions,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // The mute durations open inline under "Disable notifications" rather than in a second sheet.
    var muteOpen by remember { mutableStateOf(false) }

    // Slide the sheet away first, then take it out of the list's state.
    fun finish(action: () -> Unit) {
        action()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val pinned = conversation.getBooleanAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, false)
    val muted = conversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL, 0) >= System.currentTimeMillis()
    val hasUnread = conversation.unreadCount() > 0
    val oneToOne = conversation.getMode() == Conversational.MODE_SINGLE

    val rows = buildList {
        add(
            SheetRow(
                icon = if (pinned) R.drawable.ic_push_pin_off_24dp else R.drawable.ic_push_pin_24dp,
                label = stringResource(if (pinned) R.string.remove_from_favorites else R.string.add_to_favorites),
            ) { finish { actions.togglePinned(conversation) } },
        )
        if (muted) {
            add(
                SheetRow(R.drawable.ic_notifications_24dp, stringResource(R.string.enable_notifications)) {
                    finish { actions.setMutedTill(conversation, 0) }
                },
            )
        } else {
            add(
                SheetRow(R.drawable.ic_notifications_off_24dp, stringResource(R.string.disable_notifications)) {
                    muteOpen = !muteOpen
                },
            )
            if (muteOpen) {
                val durations = context.resources.getIntArray(R.array.mute_options_durations)
                for (seconds in durations) {
                    val label = if (seconds == -1) stringResource(R.string.until_further_notice)
                    else TimeFrameUtils.resolve(context, 1000L * seconds).toString()
                    add(
                        SheetRow(R.drawable.ic_notifications_paused_24dp, label) {
                            val till = if (seconds == -1) Long.MAX_VALUE else System.currentTimeMillis() + seconds * 1000L
                            finish { actions.setMutedTill(conversation, till) }
                        },
                    )
                }
            }
        }
        if (hasUnread) {
            add(
                SheetRow(R.drawable.ic_mark_chat_read_24dp, stringResource(R.string.mark_as_read)) {
                    finish { actions.markRead(conversation) }
                },
            )
        }
        add(
            SheetRow(R.drawable.ic_archive_24dp, stringResource(R.string.action_archive_chat)) {
                finish { actions.archive(conversation) }
            },
        )
        if (oneToOne) {
            add(
                SheetRow(R.drawable.ic_person_24dp, stringResource(R.string.action_contact_details)) {
                    finish { actions.openContact(conversation) }
                },
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                text = conversation.getName().toString(),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                rows.forEachIndexed { index, row ->
                    val position = when {
                        rows.size == 1 -> GroupPosition.SINGLE
                        index == 0 -> GroupPosition.TOP
                        index == rows.lastIndex -> GroupPosition.BOTTOM
                        else -> GroupPosition.MIDDLE
                    }
                    SheetRowItem(row, position)
                }
            }
        }
    }
}

@Composable
private fun SheetRowItem(row: SheetRow, position: GroupPosition) {
    ExpressiveGroupRow(position = position) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = row.onClick).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(row.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Text(row.label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
