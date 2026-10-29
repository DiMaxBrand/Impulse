package eu.siacs.conversations.ui.glass

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.unit.Dp
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

// Narrower bubbles than they are tall, so the contact's pill gets room for its text: on a phone the
// four bubbles used to leave it a sliver and the "last seen" line was cut off. When that line still
// needs two lines, the pill grows to [TallHeight] and every bubble grows with it (they become
// capsules, the same height as the pill, not circles standing beside it).
private val BubbleWidth = 44.dp
private val TallHeight = 68.dp

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
    /** Draw the bubbles as one body of fluid glass (see FluidGlass.kt) instead of one glass node each. */
    merging: Boolean = false,
) {
    if (!merging) {
        LiquidTopBarBody(conversation, revision, listener, backdrop, menu, selectedCount, copyEnabled, onExitSelection, onForwardSelected, onCopySelected, onDeleteSelected, modifier)
        return
    }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val host = remember { FluidGlassHost(density, LiquidGlassStyle.Bar, reachDp = 5f, softnessDp = 10f, responseSeconds = 0.3f) }
    val stack = remember(backdrop, host) { backdrop.withFluid(host) }
    FluidGlassHost(backdrop.base, host, modifier.fillMaxWidth()) {
        LiquidTopBarBody(conversation, revision, listener, stack, menu, selectedCount, copyEnabled, onExitSelection, onForwardSelected, onCopySelected, onDeleteSelected, Modifier)
    }
}

@Composable
private fun LiquidTopBarBody(
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
    val header = rememberChatHeaderState(conversation, revision)
    val inSelection = selectedCount > 0
    // Whether the line under the name needs two lines (reported by the text itself); decides the
    // height of the pill and of the bubbles beside it.
    var wrapped by remember { mutableStateOf(false) }
    val grown = wrapped && !inSelection
    val pillHeight by animateDpAsState(if (grown) TallHeight else PillHeight, label = "topBarPill")
    val bubbleHeight by animateDpAsState(if (grown) TallHeight else BubbleSize, label = "topBarBubble")

    Row(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Back, or close while messages are selected.
        LiquidGlassButton(
            onClick = if (inSelection) onExitSelection else listener::onBackPressed,
            glass = backdrop,
            style = LiquidGlassStyle.Bar,
            modifier = Modifier.width(BubbleWidth).height(bubbleHeight),
        ) {
            Crossfade(targetState = inSelection, animationSpec = tween(160), label = "topBarBack") { selecting ->
                Icon(
                    painter = painterResource(if (selecting) R.drawable.ic_close_24dp else R.drawable.ic_arrow_back_24dp),
                    contentDescription = stringResource(if (selecting) R.string.close_selection else R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (inSelection) {
            LiquidGlassPanel(
                glass = backdrop,
                style = LiquidGlassStyle.Bar,
                shape = CircleShape,
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.weight(1f).height(bubbleHeight),
            ) {
                Text(
                    text = stringResource(R.string.messages_selected_count, selectedCount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }
            ActionBubble(backdrop, bubbleHeight, R.drawable.ic_forward_24dp, stringResource(R.string.forward_message), onForwardSelected)
            ActionBubble(backdrop, bubbleHeight, R.drawable.ic_delete_24dp, stringResource(R.string.delete_message), onDeleteSelected)
            // A bubble of its own for copy (the Material bar hides it in an overflow menu that has
            // only this one entry).
            if (copyEnabled) {
                ActionBubble(backdrop, bubbleHeight, R.drawable.ic_check_24dp, stringResource(android.R.string.copy), onCopySelected)
            }
        } else {
            // The contact: picture, name, and the line under it.
            LiquidGlassButton(
                onClick = listener::onOpenDetails,
                glass = backdrop,
                style = LiquidGlassStyle.Bar,
                modifier = Modifier.weight(1f).height(pillHeight),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 7.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val avatar = header.avatar.value
                    if (avatar != null) {
                        Image(
                            bitmap = avatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(36.dp).clip(CircleShape),
                        )
                    } else {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        InkRegion {
                            Text(
                                text = conversation?.getName()?.toString() ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val subtitle = chatSubtitle(conversation, header)
                        if (subtitle != null) {
                            InkRegion {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (header.isTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    // Two lines before it is cut; a second line makes the bar taller.
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    onTextLayout = { wrapped = it.lineCount > 1 },
                                )
                            }
                        }
                    }
                }
            }
            if (header.isSingle) {
                ActionBubble(backdrop, bubbleHeight, R.drawable.ic_call_24dp, stringResource(R.string.audio_call)) {
                    listener.onCall(false)
                }
                ActionBubble(backdrop, bubbleHeight, R.drawable.ic_videocam_24dp, stringResource(R.string.video_call)) {
                    listener.onCall(true)
                }
            }
            // The three dots: where the menu grows out of (see LiquidChatMenuOverlay).
            val anchorHidden by rememberAnchorHidden(menu)
            val dots: @Composable BoxScope.() -> Unit = {
                Icon(
                    painter = painterResource(R.drawable.ic_more_horiz_24dp),
                    contentDescription = stringResource(R.string.more_options),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            LiquidGlassButton(
                onClick = { menu.open = true },
                glass = backdrop,
                style = LiquidGlassStyle.Bar,
                recording = !anchorHidden,
                modifier = Modifier.width(BubbleWidth).height(bubbleHeight)
                    .liquidMenuAnchor(menu, dots)
                    .liquidMenuPull(menu)
                    .graphicsLayer { alpha = menu.anchorAlpha },
                content = dots,
            )
        }
    }
}

@Composable
private fun ActionBubble(backdrop: GlassStack, height: Dp, iconRes: Int, description: String, onClick: () -> Unit) {
    LiquidGlassButton(
        onClick = onClick,
        glass = backdrop,
        style = LiquidGlassStyle.Bar,
        modifier = Modifier.width(BubbleWidth).height(height),
    ) {
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
    /** The top bar's fluid host, when merging glass is on: the menu's glass is part of its body. */
    fluid: FluidGlassHost? = null,
) {
    val isSingle = conversation?.getMode() == Conversational.MODE_SINGLE
    // Blocking yourself makes no sense: no such entry in the chat with yourself.
    val isSelfChat = conversation?.getContact()?.isSelf == true
    val flags = rememberChatMenuFlags(conversation, revision, isSingle)
    LiquidMenuOverlay(menu, backdrop, menuWidth = 264.dp, fluid = fluid) {
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
        if (isSingle && !isSelfChat) {
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
