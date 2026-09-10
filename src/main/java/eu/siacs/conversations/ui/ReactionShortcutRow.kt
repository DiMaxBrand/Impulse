package eu.siacs.conversations.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.material3.OutlinedIconButton
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

/**
 * A row of emoji buttons rendered as one connected group -- not a Material3 [androidx.compose
 * .material3.ButtonGroup] (that component collapses overflowing items behind an indicator, which
 * is exactly wrong for a shortcut row that must always show every item) but a plain [Row] with
 * each button's shape hand-assigned from [ButtonGroupDefaults]' leading/middle/trailing tokens,
 * so nothing is ever hidden. Each button is a real [FilledIconToggleButton] using
 * [IconButtonDefaults.toggleableShapes]'s native checked-state shape and the connected group's
 * own press shapes -- the built-in shape-by-interaction morph (toward a circle-like squircle on
 * press) comes for free from Material3 rather than a bespoke [androidx.graphics.shapes.Morph].
 *
 * Long-press opens a tertiary-colored popup with [emoji]'s real variants (see [variantsFor]) for
 * whichever button was pressed -- covers every item in the row, not just specific hardcoded ones.
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun ReactionShortcutRow(
    emojis: List<String>,
    isChecked: (String) -> Boolean,
    onPicked: (emoji: String) -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 56.dp,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        emojis.forEachIndexed { index, emoji ->
            val isLeading = index == 0
            val isTrailing = index == emojis.lastIndex
            val (shape, pressShape) = when {
                emojis.size == 1 -> CircleShape to CircleShape
                isLeading -> ButtonGroupDefaults.connectedLeadingButtonShape to
                    ButtonGroupDefaults.connectedLeadingButtonPressShape
                isTrailing -> ButtonGroupDefaults.connectedTrailingButtonShape to
                    ButtonGroupDefaults.connectedTrailingButtonPressShape
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
 * The "..." (full picker) + keyboard-entry affordances [R.string.reaction_picker_hint] promises,
 * shared by [AddReactionDialogFragment] and (when bound to a real message) [QuickReactionPickerContent]
 * -- both dialogs' hint text says the same thing, so both need the same two buttons underneath it,
 * not just one of them. Faithful to the legacy `AddReactionDialog`'s three-icon-state keyboard
 * button rather than a simplified one: keyboard (idle) -> close (typing, empty) -> send (typing,
 * has content), with the shortcut row/text-field swap above it staying in sync.
 */
@Composable
fun ReactionMoreAndKeyboardRow(
    onOpenMore: () -> Unit,
    onSubmitTyped: (List<String>) -> Unit,
    hasOpenedMore: Boolean,
    onMoreOpened: () -> Unit,
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
        Box {
            OutlinedIconButton(
                onClick = {
                    onMoreOpened()
                    onOpenMore()
                },
                modifier = Modifier.size(56.dp),
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
        Spacer(modifier = Modifier.width(8.dp))
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
