package eu.siacs.conversations.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R

/** Top-level singleton (same pattern as ThumbnailCache/ListenStatusManager/ViewStatusManager)
 * flipped by tapping any status icon in MessageFooter, which has no path back up to
 * ConversationScreenState. */
object StatusLegendSheetState {
    val visible = mutableStateOf(false)
}

private val VIEWED_GREEN = Color(0xFF2E7D32)
private val VIEW_HALF_BLUE = Color(0xFF42A5F5)
private val VIEW_UNKNOWN_AMBER = Color(0xFFF9A825)

@Composable
fun StatusLegendSheet(onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars),
        ) {
            Text(
                text = stringResource(R.string.status_legend_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            LegendSection(stringResource(R.string.status_legend_section_text)) {
                LegendItem(R.drawable.ic_more_horiz_24dp, null, R.string.status_legend_dots)
                LegendItem(R.drawable.ic_done_24dp, null, R.string.status_legend_sent)
                LegendItem(R.drawable.ic_done_all_24dp, null, R.string.status_legend_delivered)
                LegendItem(R.drawable.ic_done_all_bold_24dp, VIEWED_GREEN, R.string.status_legend_read)
            }

            LegendSection(stringResource(R.string.status_legend_section_files)) {
                LegendItem(R.drawable.ic_upload_24dp, null, R.string.status_legend_uploading)
                LegendItem(R.drawable.ic_p2p_24dp, null, R.string.status_legend_p2p)
                LegendItem(R.drawable.ic_cancel_24dp, null, R.string.status_legend_cancelled)
                Text(
                    text = stringResource(R.string.status_legend_files_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                )
            }

            LegendSection(stringResource(R.string.status_legend_section_media)) {
                LegendItem(R.drawable.ic_visibility_24dp, null, R.string.status_legend_eye_viewing)
                LegendItem(R.drawable.ic_visibility_24dp, VIEWED_GREEN, R.string.status_legend_eye_viewed)
                LegendItem(R.drawable.ic_visibility_24dp, VIEW_HALF_BLUE, R.string.status_legend_eye_half)
                LegendItem(R.drawable.ic_visibility_24dp, VIEW_UNKNOWN_AMBER, R.string.status_legend_eye_unknown)
            }

            LegendSection(stringResource(R.string.status_legend_section_voice)) {
                LegendItem(R.drawable.ic_headphones_24dp, null, R.string.status_legend_listening)
                LegendItem(R.drawable.ic_headphones_24dp, VIEWED_GREEN, R.string.status_legend_listened)
                LegendItem(R.drawable.ic_headphones_24dp, MaterialTheme.colorScheme.error, R.string.status_legend_listen_unknown)
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LegendSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
    )
    content()
}

@Composable
private fun LegendItem(iconRes: Int, tint: Color?, textRes: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
