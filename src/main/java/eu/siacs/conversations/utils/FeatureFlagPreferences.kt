package eu.siacs.conversations.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import eu.siacs.conversations.FeatureFlag

/** Manual overrides for [FeatureFlag]s, set from Developer Options > Feature flags. Mirrors
 * UpdatePreferences/OnboardingPreferences' plain SharedPreferences-backed pattern.
 *
 * A flag's key being absent from the store means "Default" — resolve to [FeatureFlag.defaultValue]
 * — rather than defaulting to false itself, so a flag whose baked-in default later flips from
 * false to true doesn't silently get stuck off for someone who never touched this screen. */
class FeatureFlagPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("feature_flag_prefs", Context.MODE_PRIVATE)

    /** null = Default (no override stored), true = forced on, false = forced off. */
    fun getOverride(flag: FeatureFlag): Boolean? =
        if (prefs.contains(flag.key)) prefs.getBoolean(flag.key, false) else null

    /** Pass null to clear back to Default. */
    fun setOverride(flag: FeatureFlag, override: Boolean?) {
        prefs.edit {
            if (override == null) remove(flag.key) else putBoolean(flag.key, override)
        }
    }

    /** What app code actually asking "is this flag on" should call — resolves the override if
     * one is set, otherwise falls back to the flag's own current default. */
    fun isEnabled(flag: FeatureFlag): Boolean = getOverride(flag) ?: flag.defaultValue
}
