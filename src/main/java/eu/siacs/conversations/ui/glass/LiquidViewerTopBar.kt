package eu.siacs.conversations.ui.glass

import androidx.compose.runtime.getValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.ui.chatSubtitle
import eu.siacs.conversations.ui.rememberChatHeaderState
import kotlin.math.roundToInt

/*
 * The photo/video viewer's top bar in the liquid glass experiment, built to be the chat's top bar
 * ([LiquidTopBar]) continuing into the viewer. The two screens are two activities, so nothing is
 * literally shared, but the viewer's bar starts out looking exactly like the chat's (same place,
 * same bubbles, the contact's picture, call and video) and then turns into the viewer's while the
 * activity fades in without sliding:
 *   - the back bubble and the contact's name stay as they are;
 *   - the picture slides out to the left, out of the pill, so nothing competes with the photo;
 *   - call and video turn into share and download (same bubbles, the icons turn into the new ones);
 *   - the three dots stay and open another menu;
 *   - the line under the name changes from the contact's status to when the photo was sent.
 * [enter] runs from 0 (as the chat's bar) to 1 (the viewer's).
 */

private val BubbleSize = 48.dp
private val PillHeight = 54.dp
private val AvatarSize = 40.dp
private val AvatarGap = 12.dp

@Composable
fun LiquidViewerTopBar(
    conversation: Conversation,
    revision: Int,
    dateText: String,
    glass: GlassStack,
    menu: LiquidMenuState,
    enter: () -> Float,
    onBack: () -> Unit,
    onShare: () -> Unit,
    // The download bubble is built by the caller: it is the source of the save card's
    // shared-bounds transform, which needs the viewer's own transition scope.
    downloadBubble: @Composable (glass: GlassStack, icon: @Composable BoxScope.() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    /** The bar's fluid host (merging glass on): its bubbles are drawn as one body of glass; null: one glass node each. */
    fluidHost: FluidGlassHost? = null,
) {
    if (fluidHost == null) {
        LiquidViewerTopBarBody(conversation, revision, dateText, glass, menu, enter, onBack, onShare, downloadBubble, modifier)
        return
    }
    val stack = remember(glass, fluidHost) { glass.withFluid(fluidHost) }
    FluidGlassHost(glass.base, fluidHost, modifier.fillMaxWidth()) {
        LiquidViewerTopBarBody(conversation, revision, dateText, stack, menu, enter, onBack, onShare, downloadBubble, Modifier)
    }
}

@Composable
private fun LiquidViewerTopBarBody(
    conversation: Conversation,
    revision: Int,
    dateText: String,
    glass: GlassStack,
    menu: LiquidMenuState,
    enter: () -> Float,
    onBack: () -> Unit,
    onShare: () -> Unit,
    // The download bubble is built by the caller: it is the source of the save card's
    // shared-bounds transform, which needs the viewer's own transition scope.
    downloadBubble: @Composable (glass: GlassStack, icon: @Composable BoxScope.() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val header = rememberChatHeaderState(conversation, revision)
    val chatLine = chatSubtitle(conversation, header)

    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LiquidGlassButton(onClick = onBack, glass = glass, style = LiquidGlassStyle.Bar, modifier = Modifier.size(BubbleSize)) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back_24dp),
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        // The contact: the picture slides out, the name stays, the line under it changes.
        LiquidGlassButton(
            onClick = {},
            glass = glass,
            style = LiquidGlassStyle.Bar,
            modifier = Modifier.weight(1f).height(PillHeight),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 7.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Its width shrinks to nothing as it leaves; it is placed so that it moves left
                // with the edge, and the glass clips it, so it slides out of view.
                Box(
                    Modifier
                        .layout { measurable, _ ->
                            val avatar = AvatarSize.roundToPx()
                            val full = avatar + AvatarGap.roundToPx()
                            val w = ((1f - enter().coerceIn(0f, 1f)) * full).roundToInt()
                            val placeable = measurable.measure(Constraints.fixed(avatar, avatar))
                            layout(w, avatar) { placeable.place(w - full, 0) }
                        }
                        .graphicsLayer { alpha = 1f - enter().coerceIn(0f, 1f) },
                ) {
                    val bitmap = header.avatar.value
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(AvatarSize).clip(CircleShape),
                        )
                    } else {
                        Box(Modifier.size(AvatarSize).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = conversation.getName()?.toString() ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box {
                        if (chatLine != null) {
                            Text(
                                text = chatLine,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.graphicsLayer { alpha = 1f - enter().coerceIn(0f, 1f) },
                            )
                        }
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer { alpha = enter().coerceIn(0f, 1f) },
                        )
                    }
                }
            }
        }

        // Call becomes share...
        LiquidGlassButton(onClick = onShare, glass = glass, style = LiquidGlassStyle.Bar, modifier = Modifier.size(BubbleSize)) {
            SwapIcon(R.drawable.ic_call_24dp, R.drawable.ic_share_24dp, enter)
        }
        // ...and video becomes download.
        downloadBubble(glass) { SwapIcon(R.drawable.ic_videocam_24dp, R.drawable.ic_download_24dp, enter) }

        // The three dots stay; they open the viewer's own menu.
        val dots: @Composable BoxScope.() -> Unit = {
            Icon(
                painter = painterResource(R.drawable.ic_more_horiz_24dp),
                contentDescription = stringResource(R.string.more_options),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        val anchorHidden by rememberAnchorHidden(menu)
        LiquidGlassButton(
            onClick = { menu.open = true },
            glass = glass,
            style = LiquidGlassStyle.Bar,
            recording = !anchorHidden,
            modifier = Modifier.size(BubbleSize)
                .liquidMenuAnchor(menu, dots)
                .liquidMenuPull(menu)
                .graphicsLayer { alpha = menu.anchorAlpha },
            content = dots,
        )
    }
}

/** One icon turning into another as [enter] goes from 0 to 1. */
@Composable
private fun SwapIcon(from: Int, to: Int, enter: () -> Float) {
    val tint = MaterialTheme.colorScheme.onSurface
    Icon(
        painter = painterResource(from),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.graphicsLayer {
            alpha = 1f - enter().coerceIn(0f, 1f)
            rotationZ = -40f * enter()
        },
    )
    Icon(
        painter = painterResource(to),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.graphicsLayer {
            alpha = enter().coerceIn(0f, 1f)
            rotationZ = 40f * (1f - enter())
        },
    )
}

/** The viewer's overflow menu: where the three dots grow out to. */
@Composable
fun LiquidViewerMenuOverlay(
    menu: LiquidMenuState,
    glass: GlassStack,
    deleteLabel: String,
    onShowInChat: () -> Unit,
    onDelete: () -> Unit,
    /** The viewer bar's fluid host, when merging glass is on. */
    fluid: FluidGlassHost? = null,
) {
    LiquidMenuOverlay(menu, glass, menuWidth = 252.dp, fluid = fluid) {
        LiquidMenuItem(R.drawable.ic_visibility_24dp, stringResource(R.string.show_in_chat)) {
            menu.open = false
            onShowInChat()
        }
        LiquidMenuItem(R.drawable.ic_delete_24dp, deleteLabel) {
            menu.open = false
            onDelete()
        }
    }
}
