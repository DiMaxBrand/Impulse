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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
 * via long-press), an optional custom third slot, a "Remember" checkbox, and a Save button.
 * Shared content -- hosted both by the Settings entry ("Quick Reactions" under Interface) and,
 * bound to a specific message, by the double-tap-on-message trigger.
 *
 * The "..." (full picker, via [onOpenMore]) and keyboard (free-form emoji entry) affordances edit
 * the custom third slot identically in *both* contexts -- this isn't about "acting on a message"
 * (there's nothing message-specific about choosing what your quick-reaction defaults are), so
 * unlike an earlier version of this dialog, it's not conditioned on being message-bound. Typing
 * more than one emoji into the keyboard entry makes the custom slot -- and so the quick reaction
 * itself -- apply multiple reactions at once on save/double-tap, not just the last one typed.
 */
@Composable
fun QuickReactionPickerContent(
    initialEmojis: List<String>?,
    initialRemember: Boolean,
    initialCustomEmojis: List<String>,
    pickedCustomEmoji: MutableState<List<String>?>,
    onSave: (emojis: List<String>, customEmojis: List<String>, remember: Boolean) -> Unit,
    onOpenMore: () -> Unit,
) {
    var selected by remember { mutableStateOf(initialEmojis ?: listOf(HEART_DEFAULT)) }
    var remember_ by remember { mutableStateOf(initialRemember) }
    var customEmojis by remember { mutableStateOf(initialCustomEmojis) }
    var showKeyboardInput by remember { mutableStateOf(false) }
    var hasOpenedMore by remember { mutableStateOf(false) }

    // Bridges the "..." picker Activity's result (delivered outside composition, into
    // pickedCustomEmoji by the hosting Fragment) back into local state, then consumes it.
    LaunchedEffect(pickedCustomEmoji.value) {
        val picked = pickedCustomEmoji.value
        if (picked != null) {
            customEmojis = picked
            selected = picked
            pickedCustomEmoji.value = null
        }
    }

    fun isHeartSlot(emojis: List<String>) = emojis.size == 1 && isHeartFamily(emojis[0])
    fun isThumbsSlot(emojis: List<String>) = emojis.size == 1 && isThumbsUpFamily(emojis[0])

    // Heart/thumbs-up stay pinned first and second regardless of selection -- only which variant
    // they display changes (the currently-selected one, if selection is within that family).
    val heartDisplay = selected.singleOrNull()?.takeIf { isHeartFamily(it) } ?: HEART_DEFAULT
    val thumbsDisplay = selected.singleOrNull()?.takeIf { isThumbsUpFamily(it) } ?: THUMBS_UP_DEFAULT
    val customDisplay = customEmojis.joinToString("")
    val rowEmojis = buildList {
        add(heartDisplay)
        add(thumbsDisplay)
        if (customEmojis.isNotEmpty()) add(customDisplay)
    }

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
                    emojis = rowEmojis,
                    isChecked = { candidate ->
                        when {
                            isHeartFamily(candidate) -> isHeartSlot(selected)
                            isThumbsUpFamily(candidate) -> isThumbsSlot(selected)
                            else -> selected == customEmojis
                        }
                    },
                    onPicked = { picked ->
                        selected = when {
                            isHeartFamily(picked) -> listOf(picked)
                            isThumbsUpFamily(picked) -> listOf(picked)
                            else -> customEmojis
                        }
                    },
                    onOpenMore = onOpenMore,
                    hasOpenedMore = hasOpenedMore,
                    onMoreOpened = { hasOpenedMore = true },
                    preferredSize = 64.dp,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        ReactionKeyboardRow(
            onSubmitTyped = { emojis ->
                customEmojis = emojis
                selected = emojis
                showKeyboardInput = false
            },
            showKeyboardInput = showKeyboardInput,
            onShowKeyboardInputChange = { showKeyboardInput = it },
        )
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
            onClick = { onSave(selected, customEmojis, remember_) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.notification_setup_done))
        }
    }
}
