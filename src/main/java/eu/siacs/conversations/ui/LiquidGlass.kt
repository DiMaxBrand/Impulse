package eu.siacs.conversations.ui

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import eu.siacs.conversations.R

/** The two looks of the app. Material 3 Expressive is the default; liquid glass is the other. */
enum class InterfaceStyle(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    @StringRes val infoRes: Int,
) {
    MATERIAL(
        "material",
        R.string.interface_style_material,
        R.string.interface_style_material_summary,
        R.string.interface_style_material_info,
    ),
    GLASS(
        "glass",
        R.string.interface_style_glass,
        R.string.interface_style_glass_summary,
        R.string.interface_style_glass_info,
    );

    companion object {
        fun of(key: String?): InterfaceStyle = entries.firstOrNull { it.key == key } ?: MATERIAL
    }
}

/**
 * The "liquid glass" look, one of the two [InterfaceStyle]s (Settings, then Appearance). Glass is
 * drawn with the Kyant0 backdrop library: whatever sits behind a glass piece is copied into it,
 * blurred and bent at the edges. The pieces themselves live in `ui/glass/` (the touch mechanics and
 * components ported from the library's demo app, the morphing containers, the message bar); the
 * screens that have a glass version check [rememberLiquidGlassEnabled] and render *that* instead of
 * the Material one: two separate versions, not a skin laid over the Material one.
 *
 * It used to be an experimental feature flag. People who had that flag on are moved to the glass
 * style once, so the move to a setting changes nothing for them.
 */
object LiquidGlass {
    /** The refraction is an AGSL runtime shader, which needs Android 13. Below that the glass style
     * does nothing and every screen draws its usual Material look. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    const val PREF_KEY = "interface_style"

    /** The chosen style (Material until somebody picks glass, or had the old flag on). */
    fun style(context: Context): InterfaceStyle {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.contains(PREF_KEY)) {
            val old = context.getSharedPreferences("feature_flag_prefs", Context.MODE_PRIVATE)
            val hadFlagOn = old.contains("liquid_glass") && old.getBoolean("liquid_glass", false)
            prefs.edit()
                .putString(PREF_KEY, if (hadFlagOn) InterfaceStyle.GLASS.key else InterfaceStyle.MATERIAL.key)
                .apply()
            old.edit().remove("liquid_glass").apply()
        }
        return InterfaceStyle.of(prefs.getString(PREF_KEY, null))
    }

    /** True when glass is the chosen style and the phone can draw it. */
    fun enabled(context: Context): Boolean = supported && style(context) == InterfaceStyle.GLASS
}

/** True when the glass style is chosen and the phone can draw it. Read once per screen opening. */
@Composable
fun rememberLiquidGlassEnabled(): Boolean {
    val context = LocalContext.current
    return remember { LiquidGlass.enabled(context) }
}
