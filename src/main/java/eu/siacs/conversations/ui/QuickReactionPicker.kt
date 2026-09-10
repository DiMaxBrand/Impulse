package eu.siacs.conversations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R

/**
 * The quick-reaction configuration screen: heart and thumbs-up (each with real Unicode variants
 * via long-press), a "Remember" checkbox, and a Save button. Shared content -- hosted both by the
 * Settings entry ("Quick Reactions" under Interface, [onOpenMore]/[onSubmitTyped] left null there:
 * there's no message in that context for the full picker or free-text entry to act on) and,
 * bound to a specific message, by the double-tap-on-message trigger, where those two callbacks
 * are supplied and the "..."/keyboard row + hint text appear underneath the two choices -- the
 * same affordances [R.string.reaction_picker_hint] describes in [AddReactionDialogFragment].
 * Showing that hint without the buttons it refers to (as the Settings-only entry briefly did) is
 * exactly the bug this split fixes: the hint only renders when there's something for it to point
 * to.
 */
@Composable
fun QuickReactionPickerContent(
    initialEmoji: String?,
    initialRemember: Boolean,
    onSave: (emoji: String, remember: Boolean) -> Unit,
    onOpenMore: (() -> Unit)? = null,
    onSubmitTyped: ((List<String>) -> Unit)? = null,
) {
    var selected by remember { mutableStateOf(initialEmoji ?: HEART_DEFAULT) }
    var remember_ by remember { mutableStateOf(initialRemember) }
    val choices = remember { listOf(HEART_DEFAULT, THUMBS_UP_DEFAULT) }
    var showKeyboardInput by remember { mutableStateOf(false) }
    var hasOpenedMore by remember { mutableStateOf(false) }

    fun isSameFamily(a: String, b: String) = isHeartFamily(a) == isHeartFamily(b)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.pref_quick_reactions),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.pref_quick_reactions_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (!showKeyboardInput) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                ReactionShortcutRow(
                    emojis = choices.map { choice -> if (isSameFamily(selected, choice)) selected else choice },
                    isChecked = { it == selected },
                    onPicked = { picked -> selected = picked },
                    buttonSize = 64.dp,
                )
            }
        }

        if (onOpenMore != null && onSubmitTyped != null) {
            Spacer(modifier = Modifier.height(16.dp))
            ReactionMoreAndKeyboardRow(
                onOpenMore = onOpenMore,
                onSubmitTyped = onSubmitTyped,
                hasOpenedMore = hasOpenedMore,
                onMoreOpened = { hasOpenedMore = true },
                showKeyboardInput = showKeyboardInput,
                onShowKeyboardInputChange = { showKeyboardInput = it },
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = remember_, onCheckedChange = { remember_ = it })
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.pref_quick_reactions_remember),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onSave(selected, remember_) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.notification_setup_done))
        }
    }
}
