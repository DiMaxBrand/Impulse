package eu.siacs.conversations.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButtonShapes
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
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import eu.siacs.conversations.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The two quick-reaction choices and their real Unicode variant sets. Heart variants are
 * distinct *colored* code points (there's no Fitzpatrick skin-tone modifier for a heart);
 * thumbs-up variants are the real skin-tone modifier sequence. Deliberately not identical
 * treatment for both -- matches what each emoji actually has, not a generic "tone picker". */
private const val HEART_DEFAULT = "❤️" // ❤️
private const val THUMBS_UP_DEFAULT = "👍" // 👍

private val HEART_VARIANTS = listOf(
    "❤️", // red ❤️
    "🧡", // orange 🧡
    "💛", // yellow 💛
    "💚", // green 💚
    "💙", // blue 💙
    "💜", // purple 💜
    "🤎", // brown 🤎
    "🖤", // black 🖤
    "🤍", // white 🤍
)

private val THUMBS_UP_VARIANTS = listOf(
    "👍", // default
    "👍🏻", // light
    "👍🏼", // medium-light
    "👍🏽", // medium
    "👍🏾", // medium-dark
    "👍🏿", // dark
)

/** The full variant set for whichever family [emoji] belongs to -- membership, not an exact
 * match against the family's *default* code point. Once a non-default variant is selected (e.g.
 * a dark-skin-tone thumbs-up), the button's own emoji becomes that variant, so matching only
 * THUMBS_UP_DEFAULT/HEART_DEFAULT here would fall through to the `else` branch and the popup
 * would only ever offer the single currently-selected variant back -- that was the bug: long-
 * press after picking a non-default variant showed just that one emoji instead of the full set. */
private fun variantsFor(emoji: String): List<String> = when {
    emoji == HEART_DEFAULT || emoji in HEART_VARIANTS -> HEART_VARIANTS
    emoji == THUMBS_UP_DEFAULT || emoji in THUMBS_UP_VARIANTS -> THUMBS_UP_VARIANTS
    else -> listOf(emoji)
}

/** A single quick-reaction button, scaled up to the picker's original oversized-circle size.
 * Uses Material3's own [FilledIconToggleButton]. [shapes] is passed in by the caller so the two
 * buttons can sit as one connected [ButtonGroup] (leading/trailing halves, not two separate
 * floating circles) while each still keeps the theme's real, built-in "checked" shape and its
 * animated shape-by-interaction transition for its selected state -- pops out of the connected
 * pair into that native silhouette rather than staying a plain rectangle half.
 *
 * Long-press opens a tertiary-colored popup with this emoji's real Unicode variants (see
 * [variantsFor]), detected by watching [interactionSource] for a press held past the platform's
 * long-press timeout -- purely additive, so it never interferes with the button's own built-in
 * click handling. One accepted trade-off from dropping the old combinedClickable: a long press
 * still also fires the normal click (selecting the base emoji) in addition to opening the variant
 * popup, rather than suppressing it -- picking a variant from the popup simply overrides that
 * selection immediately after. */
@Composable
private fun QuickReactionButton(
    emoji: String,
    selected: Boolean,
    onSelect: (String) -> Unit,
    shapes: IconToggleButtonShapes,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var showVariants by remember { mutableStateOf(false) }
    val longPressTimeoutMs = LocalViewConfiguration.current.longPressTimeoutMillis

    LaunchedEffect(interactionSource) {
        var pressJob: kotlinx.coroutines.Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressJob = launch {
                        delay(longPressTimeoutMs)
                        showVariants = true
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> pressJob?.cancel()
            }
        }
    }

    Box(contentAlignment = Alignment.Center) {
        FilledIconToggleButton(
            checked = selected,
            onCheckedChange = { checked -> if (checked) onSelect(emoji) },
            shapes = shapes,
            interactionSource = interactionSource,
            modifier = modifier.size(64.dp),
        ) {
            Text(text = emoji, fontSize = 28.sp)
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
                                        indication = null,
                                        onClick = {
                                            showVariants = false
                                            onSelect(variant)
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
 * The quick-reaction configuration screen: heart and thumbs-up (each with real Unicode variants
 * via long-press), a "Remember" checkbox, and a Save button. Shared content -- currently only
 * hosted by the Settings entry ("Quick Reactions" under Interface); the double-tap-on-message
 * trigger is expected to reuse this same composable later rather than a separate copy, per
 * TODO.md's build-order plan.
 *
 * Deliberately does not include the existing add-reaction dialog's "..." (full picker) and
 * keyboard-entry buttons here: both ultimately launch/target [AddReactionActivity], which is
 * message-bound (fetches the conversation/message/reaction restrictions) -- there's no message
 * in this settings-configuration context for them to act on. They belong once this is wired to
 * the double-tap trigger, where a real message exists.
 */
@Composable
fun QuickReactionPickerContent(
    initialEmoji: String?,
    initialRemember: Boolean,
    onSave: (emoji: String, remember: Boolean) -> Unit,
) {
    var selected by remember { mutableStateOf(initialEmoji ?: HEART_DEFAULT) }
    var remember_ by remember { mutableStateOf(initialRemember) }
    val choices = remember { listOf(HEART_DEFAULT, THUMBS_UP_DEFAULT) }

    fun isHeartFamily(e: String) = e == HEART_DEFAULT || e in HEART_VARIANTS
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            // A real connected ButtonGroup (matching the same leading/trailing-shape pattern used
            // for the delete-message action row) -- not two separate floating buttons.
            ButtonGroup(
                overflowIndicator = {},
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                choices.forEachIndexed { index, choice ->
                    val isLeading = index == 0
                    customItem(
                        buttonGroupContent = {
                            // ButtonGroupDefaults' connected-shape properties are @Composable
                            // themselves -- must be read here, inside buttonGroupContent, not
                            // hoisted above the ButtonGroup block (same gotcha as the delete-
                            // message action row above). The checked shape stays the theme's
                            // native pop-out silhouette regardless of which side of the group
                            // this button sits on -- only the resting/pressed shape is the
                            // connected leading/trailing half.
                            val shapes = IconToggleButtonShapes(
                                shape = if (isLeading) {
                                    ButtonGroupDefaults.connectedLeadingButtonShape
                                } else {
                                    ButtonGroupDefaults.connectedTrailingButtonShape
                                },
                                pressedShape = if (isLeading) {
                                    ButtonGroupDefaults.connectedLeadingButtonPressShape
                                } else {
                                    ButtonGroupDefaults.connectedTrailingButtonPressShape
                                },
                                checkedShape = IconButtonDefaults.toggleableShapes().checkedShape,
                            )
                            QuickReactionButton(
                                emoji = if (isSameFamily(selected, choice)) selected else choice,
                                selected = isSameFamily(selected, choice),
                                onSelect = { picked -> selected = picked },
                                shapes = shapes,
                            )
                        },
                        menuContent = {},
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.reaction_picker_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
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
            onClick = { onSave(selected, remember_) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.notification_setup_done))
        }
    }
}
