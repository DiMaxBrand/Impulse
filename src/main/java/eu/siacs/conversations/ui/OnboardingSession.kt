package eu.siacs.conversations.ui

import android.content.Context
import androidx.core.content.edit
import eu.siacs.conversations.update.UpdateChannel

/** Restart checkpoint; deliberately contains neither password nor challenge answers. */
class OnboardingSession internal constructor(context: Context) {
    private val prefs = context.getSharedPreferences("unified_onboarding_session", Context.MODE_PRIVATE)

    data class Checkpoint(
        val phase: Int,
        val tourPage: Int,
        val creating: Boolean?,
        val registrationDomain: String,
        val preAuth: String?,
        val address: String,
        val hostname: String,
        val port: String,
        val accountJid: String?,
        val inviteUri: String?,
        val channel: UpdateChannel,
        val channelInitialized: Boolean,
        val avatarUri: String?,
        val contactsOnly: Boolean,
        val privacyTouched: Boolean,
        val avatarStepAvailable: Boolean,
    )

    fun read(defaultChannel: UpdateChannel): Checkpoint? {
        if (!prefs.getBoolean("active", false)) return null
        val mode = if (prefs.contains("creating")) prefs.getBoolean("creating", false) else null
        return Checkpoint(
            prefs.getInt("phase", 0).coerceIn(0, 6),
            prefs.getInt("tour_page", 0).coerceIn(0, ONBOARDING_TOUR_PAGES - 1),
            mode, prefs.getString("registration_domain", "on-chat.ru").orEmpty(), prefs.getString("pre_auth", null), prefs.getString("address", "").orEmpty(), prefs.getString("hostname", "").orEmpty(),
            prefs.getString("port", "5222").orEmpty(), prefs.getString("account_jid", null), prefs.getString("invite_uri", null),
            prefs.getString("channel", null)?.let(UpdateChannel::fromId) ?: defaultChannel,
            prefs.getBoolean("channel_initialized", false), prefs.getString("avatar_uri", null),
            prefs.getBoolean("contacts_only", false), prefs.getBoolean("privacy_touched", false), prefs.getBoolean("avatar_step_available", false),
        )
    }

    fun save(checkpoint: Checkpoint) {
        prefs.edit {
            putBoolean("active", true)
            putInt("phase", checkpoint.phase)
            putInt("tour_page", checkpoint.tourPage)
            if (checkpoint.creating == null) remove("creating") else putBoolean("creating", checkpoint.creating)
            putString("registration_domain", checkpoint.registrationDomain)
            putString("pre_auth", checkpoint.preAuth)
            putString("address", checkpoint.address)
            putString("hostname", checkpoint.hostname)
            putString("port", checkpoint.port)
            putString("account_jid", checkpoint.accountJid)
            putString("invite_uri", checkpoint.inviteUri)
            putString("channel", checkpoint.channel.id)
            putBoolean("channel_initialized", checkpoint.channelInitialized)
            putString("avatar_uri", checkpoint.avatarUri)
            putBoolean("contacts_only", checkpoint.contactsOnly)
            putBoolean("privacy_touched", checkpoint.privacyTouched)
            putBoolean("avatar_step_available", checkpoint.avatarStepAvailable)
        }
    }

    fun clear() { prefs.edit { clear() } }

    companion object {
        @JvmStatic fun isInProgress(context: Context): Boolean =
            context.getSharedPreferences("unified_onboarding_session", Context.MODE_PRIVATE).getBoolean("active", false)

        /** Restoring a backup replaces the account set; a previous signup checkpoint is stale. */
        @JvmStatic fun clearForRestoredBackup(context: Context) {
            context.getSharedPreferences("unified_onboarding_session", Context.MODE_PRIVATE).edit().clear().apply()
        }
    }
}
