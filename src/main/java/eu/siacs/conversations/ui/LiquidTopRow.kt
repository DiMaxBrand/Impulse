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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.glass.LiquidGlassButton

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
    backdrop: Backdrop,
    host: SearchHostState,
    actions: SearchHostActions,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    host.menuRevision.intValue // re-read when the fragment refreshes the menu state
    val anyQr = host.showScanQr || host.showShowQr || host.showEasyInvite

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidGlassButton(
            onClick = onSearch,
            backdrop = backdrop,
            modifier = Modifier.weight(1f).height(52.dp),
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

        if (anyQr) {
            var qrOpen by remember { mutableStateOf(false) }
            Box {
                LiquidGlassButton(
                    onClick = { qrOpen = true },
                    backdrop = backdrop,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_qr_code_scanner_24dp),
                        contentDescription = stringResource(R.string.qr_code_actions),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                ExpressiveDropdownMenu(expanded = qrOpen, onDismissRequest = { qrOpen = false }) {
                    if (host.showScanQr) ExpressiveMenuItem(
                        R.drawable.ic_qr_code_scanner_24dp, stringResource(R.string.scan_qr_code),
                    ) { qrOpen = false; actions.onScanQr() }
                    if (host.showShowQr) ExpressiveMenuItem(
                        R.drawable.ic_qr_code_24dp, stringResource(R.string.show_qr_code),
                    ) { qrOpen = false; actions.onShowQr() }
                    if (host.showEasyInvite) ExpressiveMenuItem(
                        R.drawable.ic_join_24dp, stringResource(R.string.server_invite),
                    ) { qrOpen = false; actions.onEasyInvite() }
                }
            }
        }

        LiquidGlassButton(
            onClick = actions.onManageAccount,
            backdrop = backdrop,
            modifier = Modifier.size(52.dp),
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
