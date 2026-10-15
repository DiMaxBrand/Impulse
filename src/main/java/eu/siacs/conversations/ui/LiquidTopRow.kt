package eu.siacs.conversations.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.glass.GlassStack
import eu.siacs.conversations.ui.glass.LiquidGlassButton
import eu.siacs.conversations.ui.glass.LiquidMenuState
import eu.siacs.conversations.ui.glass.liquidMenuAnchor
import eu.siacs.conversations.ui.glass.liquidMenuPull

/** Height of [LiquidTopRow] including its padding: the list leaves this much room above its first row. */
val LiquidTopRowHeight = 68.dp

/**
 * The chat list's top row in the liquid glass experiment: the search field, the QR button and your
 * picture are three separate glass bubbles side by side. Each lights up under the finger and gives
 * a little to a drag, like the buttons of the library's demo ([LiquidGlassButton]). The picture does
 * not fill its bubble; a rim of glass is left showing around it. [onSearch] opens the same expanded
 * search as the ordinary bar.
 */
@Composable
fun LiquidTopRow(
    glass: GlassStack,
    host: SearchHostState,
    actions: SearchHostActions,
    onSearch: () -> Unit,
    qrMenu: LiquidMenuState,
    searchProgress: () -> Float,
    onSearchBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    host.menuRevision.intValue // re-read when the fragment refreshes the menu state
    val anyQr = host.showScanQr || host.showShowQr || host.showEasyInvite

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // While the search is opening, the glass container drawn by the host takes the pill's
        // place (growing out of exactly these bounds), so the pill itself gives way at once.
        LiquidGlassButton(
            onClick = onSearch,
            glass = glass,
            modifier = Modifier.weight(1f).height(52.dp)
                .onGloballyPositioned { onSearchBounds(it.boundsInWindow()) }
                .graphicsLayer { alpha = 1f - (searchProgress() * 8f).coerceIn(0f, 1f) },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // The QR bubble grows into its menu (drawn by the host, see LiquidMenuOverlay): this
        // button is only where it starts, and fades while the menu is its own bubble.
        if (anyQr) {
            LiquidGlassButton(
                onClick = { qrMenu.open = true },
                glass = glass,
                modifier = Modifier.size(52.dp)
                    .liquidMenuAnchor(qrMenu)
                    .liquidMenuPull(qrMenu)
                    .graphicsLayer { alpha = qrMenu.anchorAlpha * (1f - searchProgress()) },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_qr_code_scanner_24dp),
                    contentDescription = stringResource(R.string.qr_code_actions),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        LiquidGlassButton(
            onClick = actions.onManageAccount,
            glass = glass,
            modifier = Modifier.size(52.dp).graphicsLayer { alpha = 1f - searchProgress() },
        ) {
            // Smaller than the bubble: the glass rim around the picture stays visible.
            val avatar = host.avatar
            if (host.celebrating) {
                BirthdayAvatar(
                    avatar = avatar,
                    fallback = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(44.dp),
                )
            } else {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatar != null) {
                        Image(
                            bitmap = avatar,
                            contentDescription = stringResource(R.string.hub_title),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_person_24dp),
                            contentDescription = stringResource(R.string.hub_title),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}
