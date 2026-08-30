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
    /** Gates the "Invite" entry in Start Chat's "+" menu (invite a contact by sharing a direct
     * download link to the latest stable release's universal APK). Off by default: the link only
     * resolves once a stable release actually exists — see [eu.siacs.conversations.ui.InviteContent]. */
    INVITE_CONTACTS(
        key = "invite_contacts",
        titleRes = R.string.feature_flag_invite_contacts_title,
        descriptionRes = R.string.feature_flag_invite_contacts_description,
        defaultValue = false,
    ),

    /** Auto-activates a reduced-data mode while any account is stuck at
     * [eu.siacs.conversations.entities.Account.State.SERVER_NOT_FOUND] or
     * [eu.siacs.conversations.entities.Account.State.CONNECTION_TIMEOUT] — reuses the existing
     * `isDataSaverDisabled()` gate (already checked by avatar fetching and file auto-download), so
     * activating this is equivalent to Android's own Data Saver being on, network-metered-or-not.
     * Off by default: this is the first slice only (skip new avatar fetches, disable
     * auto-download) — MAM catch-up limiting, disabling carbons, and longer timeouts are a
     * follow-up, see TODO.md. */
    EMERGENCY_MODE(
        key = "emergency_mode",
        titleRes = R.string.feature_flag_emergency_mode_title,
        descriptionRes = R.string.feature_flag_emergency_mode_description,
        defaultValue = false,
    ),
}
