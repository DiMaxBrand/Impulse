package eu.siacs.conversations.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.ui.glass.GlassGroupRow
import eu.siacs.conversations.ui.glass.LocalGlassSkin

/** The two main welcome choices: a card's actual container shape springs under a finger. */
@Composable
internal fun OnboardingChoiceCard(
    title: String,
    detail: String,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 16.dp else 32.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "onboarding_choice_shape",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "onboarding_choice_scale",
    )
    val shape = RoundedCornerShape(corner)
    val color = if (emphasized) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                detail,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val target = Modifier.fillMaxWidth().scale(scale).clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
    val glass = LocalGlassSkin.current
    if (glass != null) {
        GlassGroupRow(glass, shape, color, target, content)
    } else {
        Surface(shape = shape, color = color, modifier = target) { content() }
    }
}
