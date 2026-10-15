package eu.siacs.conversations.ui.glass

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.ui.ConversationScreenListener
import eu.siacs.conversations.ui.chatSubtitle
import eu.siacs.conversations.ui.rememberChatHeaderState
import eu.siacs.conversations.ui.rememberChatMenuFlags

/*
 * The chat's top bar in the liquid glass experiment, written for glass rather than the Material
 * bar recoloured: the back button, the contact (picture, name, and what the Material bar shows
 * under it) and each action are separate glass bubbles over the chat, not one bar. The three dots
 * are the start of the overflow menu: that bubble grows into the menu panel (LiquidChatMenuOverlay)
 * the way a button does on iOS. What the bar says about the contact is worked out by the same
 * helpers as the Material bar's (rememberChatHeaderState, chatSubtitle).
 */

private val BubbleSize = 48.dp
private val PillHeight = 54.dp

@Composable
fun LiquidTopBar(
    conversation: Conversation?,
    revision: Int,
    listener: ConversationScreenListener,
    backdrop: GlassStack,
    menu: LiquidMenuState,
    selectedCount: Int,
    copyEnabled: Boolean,
    onExitSelection: () -> Unit,
    onForwardSelected: () -> Unit,
    onCopySelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val header = rememberChatHeaderState(conversation, revision)
    val inSelection = selectedCount > 0

    Row(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Back, or close while messages are selected.
        LiquidGlassButton(
            onClick = if (inSelection) onExitSelection else listener::onBackPressed,
            glass = backdrop,
            modifier = Modifier.size(BubbleSize),
        ) {
            Crossfade(targetState = inSelection, animationSpec = tween(160), label = "topBarBack") { selecting ->
                Icon(
                    painter = painterResource(if (selecting) R.drawable.ic_close_24dp else R.drawable.ic_arrow_back_24dp),
                    contentDescription = stringResource(if (selecting) R.string.close_selection else R.string.back),
                    tint = colors.onSurface,
                )
            }
        }

        if (inSelection) {
            LiquidGlassPanel(
                glass = backdrop,
                shape = CircleShape,
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.weight(1f).height(BubbleSize),
            ) {
                Text(
                    text = stringResource(R.string.messages_selected_count, selectedCount),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }
            ActionBubble(backdrop, R.drawable.ic_forward_24dp, stringResource(R.string.forward_message), onForwardSelected)
            ActionBubble(backdrop, R.drawable.ic_delete_24dp, stringResource(R.string.delete_message), onDeleteSelected)
            // A bubble of its own for copy (the Material bar hides it in an overflow menu that has
            // only this one entry).
            if (copyEnabled) {
                ActionBubble(backdrop, R.drawable.ic_check_24dp, stringResource(android.R.string.copy), onCopySelected)
            }
        } else {
            // The contact: picture, name, and the line under it.
            LiquidGlassButton(
                onClick = listener::onOpenDetails,
                glass = backdrop,
                modifier = Modifier.weight(1f).height(PillHeight),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 7.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val avatar = header.avatar.value
                    if (avatar != null) {
                        Image(
                            bitmap = avatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                        )
                    } else {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.primaryContainer))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = conversation?.getName()?.toString() ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subtitle = chatSubtitle(conversation, header)
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (header.isTyping) colors.primary else colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (header.isSingle) {
                ActionBubble(backdrop, R.drawable.ic_call_24dp, stringResource(R.string.audio_call)) {
                    listener.onCall(false)
                }
                ActionBubble(backdrop, R.drawable.ic_videocam_24dp, stringResource(R.string.video_call)) {
                    listener.onCall(true)
                }
            }
            // The three dots: where the menu grows out of (see LiquidChatMenuOverlay).
            LiquidGlassButton(
                onClick = { menu.open = true },
                glass = backdrop,
                modifier = Modifier.size(BubbleSize)
                    .liquidMenuAnchor(menu)
                    .graphicsLayer { alpha = menu.anchorAlpha },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_horiz_24dp),
                    contentDescription = stringResource(R.string.more_options),
                    tint = colors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ActionBubble(backdrop: GlassStack, iconRes: Int, description: String, onClick: () -> Unit) {
    LiquidGlassButton(onClick = onClick, glass = backdrop, modifier = Modifier.size(BubbleSize)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The chat's overflow menu in the glass version: the same entries as the Material bar's, in the
 * panel the three-dots bubble grows into. Placed once over the chat screen.
 */
@Composable
fun LiquidChatMenuOverlay(
    menu: LiquidMenuState,
    backdrop: GlassStack,
    conversation: Conversation?,
    revision: Int,
    listener: ConversationScreenListener,
) {
    val isSingle = conversation?.getMode() == Conversational.MODE_SINGLE
    val flags = rememberChatMenuFlags(conversation, revision, isSingle)
    LiquidMenuOverlay(menu, backdrop, menuWidth = 264.dp) {
        @Composable
        fun entry(iconRes: Int, label: Int, action: () -> Unit) {
            LiquidMenuItem(iconRes, stringResource(label)) {
                menu.open = false
                action()
            }
        }
        entry(R.drawable.ic_search_24dp, R.string.search_messages, listener::onSearchMessages)
        if (flags.canInvite) {
            entry(
                R.drawable.ic_person_add_24dp,
                if (isSingle) R.string.start_group_chat else R.string.invite_contact,
                listener::onInviteContact,
            )
        }
        if (flags.showEncryption) {
            entry(R.drawable.ic_lock_24dp, R.string.choose_encryption, listener::onChooseEncryption)
        }
        if (flags.isMuted) {
            entry(R.drawable.ic_notifications_24dp, R.string.enable_notifications, listener::onUnmuteConversation)
        } else {
            entry(R.drawable.ic_notifications_off_24dp, R.string.disable_notifications, listener::onMuteConversation)
        }
        entry(
            R.drawable.ic_star_24dp,
            if (flags.isPinned) R.string.remove_from_favorites else R.string.add_to_favorites,
            listener::onTogglePinned,
        )
        entry(R.drawable.ic_delete_24dp, R.string.action_clear_history, listener::onClearHistory)
        if (isSingle) {
            entry(
                R.drawable.ic_cancel_24dp,
                if (flags.isBlocked) R.string.action_unblock_contact else R.string.action_block_contact,
                listener::onBlockContact,
            )
        }
        entry(R.drawable.ic_archive_24dp, R.string.action_archive_chat, listener::onArchiveConversation)
        entry(R.drawable.ic_help_24dp, R.string.help_and_support, listener::onOpenHelp)
    }
}
