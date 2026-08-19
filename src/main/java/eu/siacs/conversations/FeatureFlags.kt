package eu.siacs.conversations

/**
 * Central registry for feature flags. Add a new flag here — one entry — and it automatically
 * shows up in Developer Options > Feature flags with no other UI changes needed: that screen
 * iterates [FeatureFlag.entries] directly rather than hand-listing them, which is the whole point
 * of this file existing as the single source of truth.
 *
 * [defaultValue] is what "Default" resolves to today — not a permanent guarantee. The flags
 * screen warns explicitly that a manually-set override can still be overtaken by a future code
 * change to a flag's own default, or by the flag being retired outright once whatever it gates
 * ships for real and the conditional is deleted. That's expected, just rare.
 */
enum class FeatureFlag(
    val key: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val defaultValue: Boolean,
) {
    /** Proves the picker/registry/persistence machinery end to end. Doesn't gate any real
     * behavior — remove once a genuine flag exists to replace it as the "does this screen still
     * work" sanity check. */
    EXAMPLE(
        key = "example",
        titleRes = R.string.feature_flag_example_title,
        descriptionRes = R.string.feature_flag_example_description,
        defaultValue = false,
    ),
}
