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
        audience = FlagAudience.Everyone,
    ),

    /** Auto-activates a reduced-data mode while any account is stuck at
     * [eu.siacs.conversations.entities.Account.State.SERVER_NOT_FOUND] or
     * [eu.siacs.conversations.entities.Account.State.CONNECTION_TIMEOUT] — reuses the existing
     * `isDataSaverDisabled()` gate (already checked by avatar fetching and file auto-download), so
     * activating this is equivalent to Android's own Data Saver being on, network-metered-or-not.
     * Off by default: this is the first slice only (skip new avatar fetches, disable
     * auto-download) — MAM catch-up limiting, disabling carbons, and longer timeouts are a
     * follow-up, see TODO.md. */
    /** The chat's top bar bubbles drawn as one body of fluid glass: elements side by side flow into
     * each other as they get close (see ui/glass/FluidGlass.kt). Off by default: a first slice. */
    MERGING_GLASS(
        key = "merging_glass",
        titleRes = R.string.feature_flag_merging_glass_title,
        descriptionRes = R.string.feature_flag_merging_glass_description,
        audience = FlagAudience.Nobody,
    ),

    EMERGENCY_MODE(
        key = "emergency_mode",
        titleRes = R.string.feature_flag_emergency_mode_title,
        descriptionRes = R.string.feature_flag_emergency_mode_description,
        audience = FlagAudience.Nobody,
    ),

}

/**
 * Who a feature flag is on for while nobody has set it by hand. A flag is not only "on for all" or
 * "off for all": it can be on for named people only, or on for everyone except named people.
 * A "person" is an address: the phone knows its owner by the accounts connected on it
 * ([eu.siacs.conversations.utils.SupportAccounts.refresh] keeps that list up to date), and a phone
 * counts as that person when one of its accounts is that address. Change a flag's audience in code
 * (here, in [FeatureFlag]) to roll it out to somebody first, or to hold it back from somebody.
 */
sealed class FlagAudience {
    /** [accounts]: the bare addresses of the accounts connected on this phone, in lower case. */
    abstract fun resolve(accounts: Set<String>): Boolean

    /** On for everybody. */
    object Everyone : FlagAudience() {
        override fun resolve(accounts: Set<String>) = true
    }

    /** Off for everybody. */
    object Nobody : FlagAudience() {
        override fun resolve(accounts: Set<String>) = false
    }

    /** On only for phones that have one of these accounts: one person, or several. */
    class OnlyFor(vararg addresses: String) : FlagAudience() {
        private val set = addresses.map { it.lowercase() }.toSet()
        override fun resolve(accounts: Set<String>) = accounts.any { it in set }
    }

    /** On for everybody except phones that have one of these accounts. */
    class EveryoneExcept(vararg addresses: String) : FlagAudience() {
        private val set = addresses.map { it.lowercase() }.toSet()
        override fun resolve(accounts: Set<String>) = accounts.none { it in set }
    }
}
