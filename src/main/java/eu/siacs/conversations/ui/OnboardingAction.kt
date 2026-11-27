package eu.siacs.conversations.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.ui.glass.LocalGlassSkin
import eu.siacs.conversations.ui.glass.LiquidGlassButton

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OnboardingAction(label: String, onClick: () -> Unit, enabled: Boolean = true, secondary: Boolean = false, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 1.05f else 1f, spring(dampingRatio = 0.8f, stiffness = 600f), label = "onboarding_press")
    val animated = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    val glass = LocalGlassSkin.current
    if (glass != null) {
        LiquidGlassButton(onClick = onClick, enabled = enabled, glass = glass.stack,
            shape = RoundedCornerShape(50), modifier = animated, tint = androidx.compose.ui.graphics.Color.Unspecified) {
            Text(label, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
    } else if (secondary) {
        OutlinedButton(onClick = onClick, enabled = enabled, interactionSource = interaction,
            shapes = ButtonDefaults.shapes(), modifier = animated) { Text(label) }
    } else {
        Button(onClick = onClick, enabled = enabled, interactionSource = interaction,
            shapes = ButtonDefaults.shapes(), modifier = animated) { Text(label) }
    }
}
