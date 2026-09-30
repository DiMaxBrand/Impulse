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

/** One entry per legend row -- what the tapped status icon currently is, so the sheet can bold
 * exactly that row. */
enum class StatusLegendKey {
    DOTS, SENT, DELIVERED, READ, ERROR,
    UPLOADING, P2P, CANCELLED,
    EYE_VIEWING, EYE_VIEWED, EYE_HALF, EYE_UNKNOWN,
    LISTENING, LISTENED, LISTEN_UNKNOWN,
}

/** Maps the two things MessageFooter already derives for its icon (the eye view-status, else the
 * checkmark-family phase) to the legend row describing it. Null for anything the legend has no
 * row for (e.g. the generic error glyph) -- the sheet then simply highlights nothing. */
fun legendKeyFor(viewState: ViewStatusManager.State?, phase: CheckmarkPhase?): StatusLegendKey? {
    when (viewState) {
        ViewStatusManager.State.VIEWING -> return StatusLegendKey.EYE_VIEWING
        ViewStatusManager.State.VIEWED -> return StatusLegendKey.EYE_VIEWED
        ViewStatusManager.State.HALF_VIEWED -> return StatusLegendKey.EYE_HALF
        ViewStatusManager.State.UNKNOWN -> return StatusLegendKey.EYE_UNKNOWN
        else -> {}
    }
    return when (phase) {
        CheckmarkPhase.WAITING -> StatusLegendKey.DOTS
        CheckmarkPhase.SENT -> StatusLegendKey.SENT
        CheckmarkPhase.DELIVERED -> StatusLegendKey.DELIVERED
        CheckmarkPhase.READ -> StatusLegendKey.READ
        CheckmarkPhase.UPLOADING -> StatusLegendKey.UPLOADING
        CheckmarkPhase.OFFERED -> StatusLegendKey.P2P
        CheckmarkPhase.CANCELLED -> StatusLegendKey.CANCELLED
        // Paused is the same gray headphones as listening, just frozen.
        CheckmarkPhase.LISTENING, CheckmarkPhase.LISTEN_PAUSED -> StatusLegendKey.LISTENING
        CheckmarkPhase.LISTENED -> StatusLegendKey.LISTENED
        CheckmarkPhase.LISTEN_UNKNOWN -> StatusLegendKey.LISTEN_UNKNOWN
        null -> null
    }
}

/** Top-level singleton (same pattern as ThumbnailCache/ListenStatusManager/ViewStatusManager)
 * flipped by tapping any status icon in MessageFooter, which has no path back up to
 * ConversationScreenState. [current] is set by the same tap. */
object StatusLegendSheetState {
    val visible = mutableStateOf(false)
    val current = mutableStateOf<StatusLegendKey?>(null)
}

private val VIEWED_GREEN = Color(0xFF2E7D32)
private val VIEW_HALF_BLUE = Color(0xFF42A5F5)
private val VIEW_UNKNOWN_AMBER = Color(0xFFF9A825)

@Composable
fun StatusLegendSheet(onDismiss: () -> Unit) {
    val current = StatusLegendSheetState.current.value
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

            LegendSection(stringResource(R.string.status_legend_section_all)) {
                LegendItem(R.drawable.ic_more_horiz_24dp, null, R.string.status_legend_dots, current == StatusLegendKey.DOTS)
                LegendItem(R.drawable.ic_done_24dp, null, R.string.status_legend_sent, current == StatusLegendKey.SENT)
                LegendItem(R.drawable.ic_done_all_24dp, null, R.string.status_legend_delivered, current == StatusLegendKey.DELIVERED)
                LegendItem(R.drawable.ic_done_all_bold_24dp, VIEWED_GREEN, R.string.status_legend_read, current == StatusLegendKey.READ)
                LegendItem(R.drawable.ic_error_24dp, null, R.string.status_legend_error, current == StatusLegendKey.ERROR)
            }

            LegendSection(stringResource(R.string.status_legend_section_files)) {
                LegendItem(R.drawable.ic_upload_24dp, null, R.string.status_legend_uploading, current == StatusLegendKey.UPLOADING)
                LegendItem(R.drawable.ic_p2p_24dp, null, R.string.status_legend_p2p, current == StatusLegendKey.P2P)
                LegendItem(R.drawable.ic_cancel_24dp, null, R.string.status_legend_cancelled, current == StatusLegendKey.CANCELLED)
                Text(
                    text = stringResource(R.string.status_legend_files_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                )
            }

            LegendSection(stringResource(R.string.status_legend_section_media)) {
                LegendItem(R.drawable.ic_visibility_24dp, null, R.string.status_legend_eye_viewing, current == StatusLegendKey.EYE_VIEWING)
                LegendItem(R.drawable.ic_visibility_24dp, VIEWED_GREEN, R.string.status_legend_eye_viewed, current == StatusLegendKey.EYE_VIEWED)
                LegendItem(R.drawable.ic_visibility_24dp, VIEW_HALF_BLUE, R.string.status_legend_eye_half, current == StatusLegendKey.EYE_HALF)
                LegendItem(R.drawable.ic_visibility_24dp, VIEW_UNKNOWN_AMBER, R.string.status_legend_eye_unknown, current == StatusLegendKey.EYE_UNKNOWN)
            }

            LegendSection(stringResource(R.string.status_legend_section_voice)) {
                LegendItem(R.drawable.ic_headphones_24dp, null, R.string.status_legend_listening, current == StatusLegendKey.LISTENING)
                LegendItem(R.drawable.ic_headphones_24dp, VIEWED_GREEN, R.string.status_legend_listened, current == StatusLegendKey.LISTENED)
                LegendItem(
                    R.drawable.ic_headphones_24dp,
                    MaterialTheme.colorScheme.error,
                    R.string.status_legend_listen_unknown,
                    current == StatusLegendKey.LISTEN_UNKNOWN,
                )
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
private fun LegendItem(iconRes: Int, tint: Color?, textRes: Int, highlighted: Boolean) {
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
        // Bold marks "this is the state the icon you tapped is in right now."
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlighted) androidx.compose.ui.text.font.FontWeight.Bold else null,
        )
    }
}
