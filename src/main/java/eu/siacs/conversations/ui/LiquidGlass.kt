package eu.siacs.conversations.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.siacs.conversations.FeatureFlag
import eu.siacs.conversations.utils.FeatureFlagPreferences

/**
 * The "liquid glass" experiment (feature flag [FeatureFlag.LIQUID_GLASS], off by default). Glass is
 * drawn with the Kyant0 backdrop library: whatever sits behind a glass piece is copied into it,
 * blurred and bent at the edges. The pieces themselves live in `ui/glass/` (the touch mechanics and
 * components ported from the library's demo app, the morphing containers, the message bar); the
 * screens that have a glass version check [rememberLiquidGlassEnabled] and render *that* instead of
 * the Material one: two separate versions, not a skin laid over the Material one.
 */
object LiquidGlass {
    /** The refraction is an AGSL runtime shader, which needs Android 13. Below that the flag does
     * nothing and every screen draws its usual Material look. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

/** True when the flag is on and the phone can draw the effect. Read once per screen opening. */
@Composable
fun rememberLiquidGlassEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        LiquidGlass.supported && FeatureFlagPreferences(context).isEnabled(FeatureFlag.LIQUID_GLASS)
    }
}
