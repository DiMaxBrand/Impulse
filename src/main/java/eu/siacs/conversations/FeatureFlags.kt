package eu.siacs.conversations

/**
 * Central registry for feature flags. Add a new flag here — one entry — and it automatically
 * shows up in Developer Options > Feature flags with no other UI changes needed: that screen
 * iterates [FeatureFlag.entries] directly rather than hand-listing them, which is the whole point
 * of this file existing as the single source of truth.
 *
 * [audience] is who has the flag on while "Default" is selected, today — not a permanent guarantee. The flags
 * screen warns explicitly that a manually-set override can still be overtaken by a future code
 * change to a flag's own default, or by the flag being retired outright once whatever it gates
 * ships for real and the conditional is deleted. That's expected, just rare.
 */
enum class FeatureFlag(
    val key: String,
    val titleRes: Int,
    val descriptionRes: Int,
    /** Who has the flag on while nobody has set it by hand: see [FlagAudience]. */
    val audience: FlagAudience,
) {
    /** Gates the "Invite" entry in Start Chat's "+" menu (invite a contact by sharing a direct
     * download link to the latest stable release's arm64 APK). Graduated to ON by default now that
     * a stable release exists for the link to resolve against -- see
     * [eu.siacs.conversations.ui.InviteContent]. The flag itself stays for now, so it can still be
     * switched off from Feature flags; it is removed in a later cleanup. A value someone already
     * set by hand is untouched. */
    INVITE_CONTACTS(
        key = "invite_contacts",
        titleRes = R.string.feature_flag_invite_contacts_title,
        descriptionRes = R.string.feature_flag_invite_contacts_description,
        audience = FlagAudience.EVERYONE,
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
        audience = FlagAudience.NOBODY,
    ),

    /** Experiment: swaps some Material 3 Expressive pieces for "liquid glass" ones drawn with the
     * Kyant0 backdrop library -- the chat list's top row (search, QR and your picture as separate
     * stretchy bubbles) and the chat's message bar. Needs Android 13 or newer (the refraction is
     * a runtime shader); below that the flag has no effect. Off by default, early, may look rough.
     * See [eu.siacs.conversations.ui.LiquidGlass]. */
    LIQUID_GLASS(
        key = "liquid_glass",
        titleRes = R.string.feature_flag_liquid_glass_title,
        descriptionRes = R.string.feature_flag_liquid_glass_description,
        audience = FlagAudience.NOBODY,
    ),
}

/**
 * Who a feature flag is on for when nobody has set it by hand. A flag is not only "on for all" or
 * "off for all": it can be off for everyone but a few, or on for everyone but a few. "A few" is
 * decided on the phone by what it can see about its owner; today that is one thing, whether one of
 * the connected accounts is a support address ([eu.siacs.conversations.utils.SupportAccounts]).
 */
enum class FlagAudience {
    /** On for everybody. */
    EVERYONE,
    /** Off for everybody. */
    NOBODY,
    /** On only when a support address is among the connected accounts (a default only: it can be
     * overridden by hand like any flag; the support tools themselves are not flags, see
     * [eu.siacs.conversations.update.SupportTracking]). */
    SUPPORT_ONLY,
    /** On for everybody except those with a support address among their accounts. */
    EVERYONE_BUT_SUPPORT;

    fun resolve(hasSupportAccount: Boolean): Boolean =
        when (this) {
            EVERYONE -> true
            NOBODY -> false
            SUPPORT_ONLY -> hasSupportAccount
            EVERYONE_BUT_SUPPORT -> !hasSupportAccount
        }
}
