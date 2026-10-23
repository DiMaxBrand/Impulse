package eu.siacs.conversations.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.utils.SupportAccounts
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

private class AttachTile(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val action: () -> Unit,
)

private val TileHeight = 84.dp
private val Gap = 8.dp
private val Pad = 16.dp
private val HeaderHeight = 24.dp

/**
 * The attach card of the Material chat layout: the paperclip grows into a card above the message
 * bar, in the tertiary colour, with everything that can be attached (and, on a phone with the support
 * account, the support tools below).
 *
 * It is a container transform done by hand, with two springs of different speed (this is what M3
 * Expressive calls for when a control becomes a surface): the colour switches first, on a stiff
 * spring, and the container's position and size follow a moment later on a softer one. The paperclip
 * itself is preserved through the motion: it travels from the bar into the card's header, as the
 * shared element would. (The bar has a transition scope of its own, so a shared-bounds transform
 * could not reach a card above it; the glass layout's menu uses the same hand-made morph.)
 */
@Composable
internal fun AttachCardOverlay(state: ConversationScreenState, listener: ConversationScreenListener) {
    val open = state.attachCardOpen.value
    val progress = state.attachProgress
    val tint = remember { Animatable(0f) }
    LaunchedEffect(open) {
        coroutineScope {
            // The background colour: fast, and first.
            launch {
                tint.animateTo(if (open) 1f else 0f, spring(dampingRatio = 1f, stiffness = 1600f))
            }
            // The expansion: the same moment, a little slower and with a bounce.
            launch {
                delay(if (open) 40 else 0)
                progress.animateTo(
                    if (open) 1f else 0f,
                    spring(dampingRatio = 0.8f, stiffness = 380f),
                )
            }
        }
    }
    BackHandler(enabled = open) { state.attachCardOpen.value = false }
    if (progress.value <= 0.002f && !open) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val conversation = state.conversation.value
    val showSupport =
        remember(conversation) {
            conversation != null &&
                conversation.getMode() == Conversational.MODE_SINGLE &&
                SupportAccounts.hasSupportAccount(context)
        }
    val close = { state.attachCardOpen.value = false }
    fun act(action: () -> Unit): () -> Unit = {
        state.attachCardOpen.value = false
        action()
    }
    val tiles =
        listOf(
            AttachTile(R.drawable.ic_image_24dp, R.string.attachment_choice_gallery, act(listener::onAttachImage)),
            AttachTile(R.drawable.ic_camera_alt_24dp, R.string.attachment_choice_camera, act(listener::onTakePhoto)),
            AttachTile(R.drawable.ic_attach_file_24dp, R.string.attachment_choice_file, act(listener::onAttachFile)),
            AttachTile(R.drawable.ic_location_pin_24dp, R.string.attachment_choice_location, act(listener::onShareLocation)),
            AttachTile(R.drawable.ic_contacts_24dp, R.string.attach_phone_contact, act(listener::onSharePhoneContact)),
            AttachTile(R.drawable.ic_person_24dp, R.string.attach_impulse_contact, act(listener::onShareImpulseContact)),
        )
    val supportTiles =
        if (showSupport) {
            listOf(
                AttachTile(R.drawable.ic_report_24dp, R.string.track_menu_label) {
                    state.attachCardOpen.value = false
                    state.trackCardVisible.value = true
                },
                AttachTile(R.drawable.ic_system_update_24dp, R.string.support_check_menu_label, act(listener::onSupportCheckUpdates)),
            )
        } else emptyList()

    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            // Anything outside the card closes it while it is open.
            .then(
                if (open) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = close,
                    )
                } else Modifier
            )
    ) {
        val cardWidth = min(maxWidth.value - 16f, 360f).dp
        val tileWidth = (cardWidth - Pad * 2 - Gap * 2) / 3
        val rows = (tiles.size + 2) / 3
        val supportBlock = if (supportTiles.isEmpty()) 0.dp else 12.dp + 16.dp + Gap + TileHeight
        val cardHeight =
            Pad * 2 + HeaderHeight + 12.dp + TileHeight * rows + Gap * (rows - 1) + supportBlock

        val widthPx = with(density) { cardWidth.toPx() }
        val heightPx = with(density) { cardHeight.toPx() }
        val margin = with(density) { 8.dp.toPx() }
        val barHeight = state.inputBarHeightPx.intValue.toFloat()
        val targetLeft = margin
        val targetTop = with(density) { maxHeight.toPx() } - barHeight - margin - heightPx

        // Where the paperclip button is, in this overlay's own coordinates (or a guess at the bar's
        // left end before it has been measured).
        val anchor = state.attachAnchor.value
        val a =
            if (anchor.isEmpty) {
                androidx.compose.ui.geometry.Rect(
                    margin,
                    with(density) { maxHeight.toPx() } - barHeight,
                    margin + with(density) { 48.dp.toPx() },
                    with(density) { maxHeight.toPx() } - barHeight + with(density) { 48.dp.toPx() },
                )
            } else anchor.translate(-origin)

        val p = progress.value
        val left = lerp(a.left, targetLeft, p)
        val top = lerp(a.top, targetTop, p)
        val w = lerp(a.width, widthPx, p).coerceAtLeast(1f)
        val h = lerp(a.height, heightPx, p).coerceAtLeast(1f)
        val corner = lerp(24f, 28f, p.coerceIn(0f, 1f)).dp
        val shape = RoundedCornerShape(corner)
        val colors = MaterialTheme.colorScheme
        val container = lerpColor(colors.surfaceContainer, colors.tertiaryContainer, tint.value)
        val onContainer = lerpColor(colors.onSurface, colors.onTertiaryContainer, tint.value)

        Box(
            Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                .shadow(6.dp * p.coerceIn(0f, 1f), shape)
                .clip(shape)
                .background(container)
                // Touches inside the card must not reach the dismiss layer below.
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
        ) {
            // The content is laid out at its final size and revealed as the card grows into it.
            Column(
                Modifier.requiredSize(cardWidth, cardHeight)
                    .graphicsLayer { alpha = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f) }
                    .padding(Pad)
            ) {
                Row(Modifier.height(HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
                    // The paperclip itself is drawn above, travelling into this spot.
                    Spacer(Modifier.width(HeaderHeight + 12.dp))
                    Text(
                        text = stringResource(R.string.attach_file),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onTertiaryContainer,
                    )
                }
                Spacer(Modifier.height(12.dp))
                TileRows(tiles, tileWidth, colors.onTertiaryContainer)
                if (supportTiles.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.attach_support_section),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onTertiaryContainer.copy(alpha = 0.72f),
                        modifier = Modifier.height(16.dp),
                    )
                    Spacer(Modifier.height(Gap))
                    TileRows(supportTiles, tileWidth, colors.onTertiaryContainer)
                }
            }
        }
        // The paperclip, preserved: from where the button is to the card's header.
        val iconSize = 24.dp
        val iconPx = with(density) { iconSize.toPx() }
        val fromX = a.center.x - iconPx / 2f
        val fromY = a.center.y - iconPx / 2f
        val toX = targetLeft + with(density) { Pad.toPx() }
        val toY = targetTop + with(density) { Pad.toPx() }
        Icon(
            painter = painterResource(R.drawable.ic_attach_file_24dp),
            contentDescription = null,
            tint = onContainer,
            modifier =
                Modifier.offset { IntOffset(lerp(fromX, toX, p).roundToInt(), lerp(fromY, toY, p).roundToInt()) }
                    .size(iconSize),
        )
    }
}

@Composable
private fun TileRows(tiles: List<AttachTile>, tileWidth: androidx.compose.ui.unit.Dp, content: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Gap), modifier = Modifier.fillMaxWidth()) {
                row.forEach { tile ->
                    Surface(
                        onClick = tile.action,
                        shape = RoundedCornerShape(20.dp),
                        color = content.copy(alpha = 0.10f),
                        contentColor = content,
                        modifier = Modifier.width(tileWidth).height(TileHeight),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        ) {
                            Icon(painter = painterResource(tile.icon), contentDescription = null)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(tile.label),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
