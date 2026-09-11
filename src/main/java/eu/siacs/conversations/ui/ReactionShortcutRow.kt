package eu.siacs.conversations.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import eu.siacs.conversations.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.fellbaum.jemoji.EmojiManager

// Middle items in a connected row have no single published "resting shape" token in
// ButtonGroupDefaults (only the leading/trailing rest shapes and a middle *press* shape are
// exposed) -- same small-corner treatment already used for the equivalent case in
// ConversationScreen.kt's delete-message action row.
private val CONNECTED_MIDDLE_SHAPE = RoundedCornerShape(16.dp)
private val BUTTON_SPACING = 2.dp

/**
 * A row of emoji buttons plus a trailing "..." button, rendered as one connected group that
 * shrinks to fit rather than either overflowing the card's bounds or hiding items with no way to
 * reach them (both real bugs in earlier versions of this row). Not Material3's
 * [androidx.compose.material3.ButtonGroup] -- that component collapses overflowing items behind
 * a menu of just those items, which duplicated what "..." already does (it opens the full
 * [AddReactionActivity] picker, where literally any emoji, overflowed shortcuts included, is
 * reachable anyway). So instead, within [modifier]'s real available width (measured via
 * [BoxWithConstraints], not assumed):
 * 1. Every button (shortcuts + trailing "...") shrinks together, uniformly, from [preferredSize]
 *    down to [minSize] before anything is dropped.
 * 2. Only if [minSize] still doesn't fit everything are the *lowest-priority* shortcuts (the end
 *    of [emojis] -- callers should order it accordingly, e.g. [buildShortcutEmojis]'s pinned
 *    heart/thumbs-up first) dropped -- "..." itself is never dropped, since it's the fallback for
 *    anything that was.
 *
 * "..." sits inside the same connected group (trailing position) rather than as a separate button
 * below -- it's not a distinct concern from the shortcuts, just the overflow valve for all of
 * them, so it belongs in the same row rather than duplicated as its own affordance underneath.
 *
 * Each visible emoji button is a real [FilledIconToggleButton] using [IconButtonDefaults
 * .toggleableShapes]'s native checked-state shape and the connected group's own press shapes --
 * the built-in shape-by-interaction morph (toward a circle-like squircle on press) comes for free
 * from Material3 rather than a bespoke [androidx.graphics.shapes.Morph].
 *
 * Long-press (on a still-visible emoji button) opens a tertiary-colored popup with [emoji]'s real
 * variants (see [variantsFor]) -- covers every item in the row, not just specific hardcoded ones.
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun ReactionShortcutRow(
    emojis: List<String>,
    isChecked: (String) -> Boolean,
    onPicked: (emoji: String) -> Unit,
    onOpenMore: () -> Unit,
    hasOpenedMore: Boolean,
    onMoreOpened: () -> Unit,
    modifier: Modifier = Modifier,
    showMoreButton: Boolean = true,
    preferredSize: Dp = 56.dp,
    minSize: Dp = 40.dp,
) {
    BoxWithConstraints(modifier = modifier) {
        // +1 slot reserved for the trailing "..." button (unless there isn't one), never dropped.
        val moreSlots = if (showMoreButton) 1 else 0
        val totalSlotsAtPreferred = emojis.size + moreSlots
        val neededAtPreferred = preferredSize * totalSlotsAtPreferred + BUTTON_SPACING * (totalSlotsAtPreferred - 1)

        val visibleCount: Int
        val buttonSize: Dp
        if (neededAtPreferred <= maxWidth) {
            visibleCount = emojis.size
            buttonSize = preferredSize
        } else {
            // How many slots (shortcuts + "...") fit at all, at minSize -- then how many
            // shortcuts that leaves once "..." keeps its one reserved slot.
            val maxSlotsAtMin = ((maxWidth + BUTTON_SPACING) / (minSize + BUTTON_SPACING))
                .toInt()
                .coerceAtLeast(1)
            visibleCount = (maxSlotsAtMin - moreSlots).coerceIn(0, emojis.size)
            val totalSlots = visibleCount + moreSlots
            // Shrink uniformly to fill the real available width at this item count, never below
            // minSize and never above preferredSize.
            buttonSize = ((maxWidth - BUTTON_SPACING * (totalSlots - 1)) / totalSlots)
                .coerceIn(minSize, preferredSize)
        }
        val visibleEmojis = emojis.take(visibleCount)

        Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_SPACING)) {
            visibleEmojis.forEachIndexed { index, emoji ->
                val isLeading = index == 0
                val (shape, pressShape) = when {
                    isLeading -> ButtonGroupDefaults.connectedLeadingButtonShape to
                        ButtonGroupDefaults.connectedLeadingButtonPressShape
                    else -> CONNECTED_MIDDLE_SHAPE to ButtonGroupDefaults.connectedMiddleButtonPressShape
                }
                ReactionShortcutButton(
                    emoji = emoji,
                    checked = isChecked(emoji),
                    onPicked = onPicked,
                    shapes = IconToggleButtonShapes(
                        shape = shape,
                        pressedShape = pressShape,
                        checkedShape = IconButtonDefaults.toggleableShapes().checkedShape,
                    ),
                    size = buttonSize,
                )
            }
            if (showMoreButton) {
            val (moreShape, morePressShape) = if (visibleEmojis.isEmpty()) {
                CircleShape to CircleShape
            } else {
                ButtonGroupDefaults.connectedTrailingButtonShape to
                    ButtonGroupDefaults.connectedTrailingButtonPressShape
            }
            Box {
                FilledIconButton(
                    onClick = {
                        onMoreOpened()
                        onOpenMore()
                    },
                    shapes = IconButtonDefaults.shapes(
                        shape = moreShape,
                        pressedShape = morePressShape,
                    ),
                    modifier = Modifier.size(buttonSize),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_horiz_24dp),
                        contentDescription = stringResource(R.string.more_reactions),
                    )
                }
                if (!hasOpenedMore) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun ReactionShortcutButton(
    emoji: String,
    checked: Boolean,
    onPicked: (String) -> Unit,
    shapes: IconToggleButtonShapes,
    size: Dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var showVariants by remember { mutableStateOf(false) }
    val longPressTimeoutMs = LocalViewConfiguration.current.longPressTimeoutMillis

    LaunchedEffect(interactionSource) {
        var pressJob: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressJob = launch {
                        delay(longPressTimeoutMs)
                        if (variantsFor(emoji).size > 1) showVariants = true
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> pressJob?.cancel()
            }
        }
    }

    Box(contentAlignment = Alignment.Center) {
        FilledIconToggleButton(
            checked = checked,
            onCheckedChange = { onPicked(emoji) },
            shapes = shapes,
            interactionSource = interactionSource,
            modifier = Modifier.size(size),
        ) {
            Text(text = emoji, fontSize = 22.sp)
        }

        if (showVariants) {
            Popup(
                alignment = Alignment.BottomCenter,
                onDismissRequest = { showVariants = false },
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        variantsFor(emoji).forEach { variant ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = androidx.compose.material3.ripple(),
                                        onClick = {
                                            showVariants = false
                                            onPicked(variant)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = variant, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The keyboard-entry affordance (free-form emoji text entry) and [R.string.reaction_picker_hint]
 * underneath the shortcut row -- shared by [AddReactionDialogFragment] and
 * [QuickReactionPickerContent]. The "..." button the hint text also mentions now lives inside
 * [ReactionShortcutRow] itself (see its doc) rather than here.
 */
@Composable
fun ReactionKeyboardRow(
    onSubmitTyped: (List<String>) -> Unit,
    showKeyboardInput: Boolean,
    onShowKeyboardInputChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var typedText by remember { mutableStateOf("") }

    fun submitTyped() {
        val emojis = EmojiManager.extractEmojisInOrder(typedText).map { it.emoji }
        if (emojis.isNotEmpty()) {
            onSubmitTyped(emojis)
            typedText = ""
        }
    }

    if (showKeyboardInput) {
        OutlinedTextField(
            value = typedText,
            onValueChange = { typedText = it },
            modifier = modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.emoji_reactions)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submitTyped() }),
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        FilledIconButton(
            onClick = {
                when {
                    !showKeyboardInput -> onShowKeyboardInputChange(true)
                    typedText.isEmpty() -> onShowKeyboardInputChange(false)
                    else -> submitTyped()
                }
            },
            modifier = Modifier.size(56.dp),
        ) {
            val iconRes = when {
                !showKeyboardInput -> R.drawable.ic_keyboard_24dp
                typedText.isEmpty() -> R.drawable.ic_close_24dp
                else -> R.drawable.ic_send_24dp
            }
            Icon(painter = painterResource(iconRes), contentDescription = null)
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.reaction_picker_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}
