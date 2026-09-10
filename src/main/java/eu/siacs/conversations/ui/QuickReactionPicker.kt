package eu.siacs.conversations.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.graphics.shapes.Morph
import eu.siacs.conversations.R

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

/** One quick-reaction candidate: [baseEmoji] identifies which variant set applies (heart vs.
 * thumbs-up), [selectedEmoji] is the actual variant currently chosen (defaults to [baseEmoji]). */
private fun variantsFor(baseEmoji: String): List<String> = when (baseEmoji) {
    HEART_DEFAULT -> HEART_VARIANTS
    THUMBS_UP_DEFAULT -> THUMBS_UP_VARIANTS
    else -> listOf(baseEmoji)
}

/** A single circular emoji button whose background shape morphs (via the same [Morph]/
 * `RoundedPolygon` technique as [AutoMorphingShape] elsewhere in the app) between a plain circle
 * and a soft "cookie" shape when selected -- native shape-morphing, not just a color/scale
 * change, matching the app's established Expressive motion language. Long-press opens a
 * tertiary-colored popup with this emoji's real Unicode variants (see [variantsFor]). */
@Composable
private fun MorphingReactionButton(
    emoji: String,
    selected: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val circle = remember { MaterialShapeHelpers.circle() }
    val cookie = remember { MaterialShapeHelpers.cookie9Sided() }
    val morph = remember { Morph(circle, cookie) }
    val progress = remember { Animatable(0f) }
    var showVariants by remember { mutableStateOf(false) }

    LaunchedEffect(selected) {
        progress.animateTo(
            targetValue = if (selected) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = modifier
                .size(64.dp)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onSelect(emoji) },
                    onLongClick = { showVariants = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val reusedPath = remember { androidx.compose.ui.graphics.Path() }
            val reusedMatrix = remember { android.graphics.Matrix() }
            Canvas(modifier = Modifier.size(64.dp)) {
                reusedMatrix.reset()
                reusedMatrix.postScale(size.width, size.height)
                morph.toPath(progress.value, reusedPath)
                reusedPath.asAndroidPath().transform(reusedMatrix)
                clipPath(reusedPath) { drawRect(backgroundColor) }
            }
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
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            showVariants = false
                                            onSelect(variant)
                                        },
                                        onLongClick = {},
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
            for (choice in choices) {
                MorphingReactionButton(
                    emoji = if (isSameFamily(selected, choice)) selected else choice,
                    selected = isSameFamily(selected, choice),
                    onSelect = { picked -> selected = picked },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
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
