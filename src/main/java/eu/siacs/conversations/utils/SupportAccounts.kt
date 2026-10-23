package eu.siacs.conversations.utils

import android.content.Context
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.xmpp.Jid

/**
 * Knows which address is Impulse support ([Config.BUG_REPORTS]), and whether this phone has it among
 * its accounts. Only that exact address counts: a look-alike (the same name on another domain, say
 * "support@onchat.ru" without the hyphen) belongs to whoever runs that domain, and must never be
 * able to pass for support.
 */
object SupportAccounts {

    private val SUPPORT = Config.BUG_REPORTS.asBareJid().toString().lowercase()

    @JvmStatic
    fun isSupportAddress(jid: Jid?): Boolean =
        jid != null && jid.asBareJid().toString().lowercase() == SUPPORT

    /** Stores the addresses of the connected accounts (for [FlagAudience] targeting and for
     * [hasSupportAccount]). Call it whenever the account list is loaded or changes. */
    @JvmStatic
    fun refresh(context: Context, accounts: List<Account>) {
        val addresses = accounts.map { it.getJid().asBareJid().toString().lowercase() }.toSet()
        val prefs = FeatureFlagPreferences(context)
        if (prefs.connectedAccounts != addresses) prefs.connectedAccounts = addresses
    }

    /** True while a support account is connected on this phone: what shows the support-only tools.
     * Not a feature flag and not switchable by hand. (Showing the tools on a phone that has no such
     * account, by editing a copy of the app, achieves nothing: the person who gets the message only
     * accepts it from the support address.) */
    @JvmStatic
    fun hasSupportAccount(context: Context): Boolean = SUPPORT in FeatureFlagPreferences(context).connectedAccounts
}
