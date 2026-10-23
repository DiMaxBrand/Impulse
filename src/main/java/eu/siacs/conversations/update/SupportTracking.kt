package eu.siacs.conversations.update

import android.content.Context
import eu.siacs.conversations.BuildConfig
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xmpp.Jid
import im.conversations.android.xmpp.model.support.Track
import java.util.Locale
import java.util.concurrent.Executors
import okhttp3.OkHttpClient

/**
 * What support does to a person's phone, from the support account (see
 * [eu.siacs.conversations.xmpp.manager.SupportCommandManager] for how it travels and is checked):
 *
 *  - **Track**: a bug fix, improvement, new feature or change support follows for this person. It is
 *    filed as one of the phone's own waiting items ([BugReportRegistry.adopt]), so the Updates screen
 *    shows it by the title support typed, in the phone's language, and a notice arrives when a
 *    release's notes name its ID. Nothing appears in the chat.
 *  - **Check for updates**: the phone looks for an update and says what it found, as a visible
 *    message in the chat, so both people see it happened ([answerCheck]). It never starts an update.
 */
object SupportTracking {

    private val ID = Regex("^[A-Z]{3}-[A-Z0-9]{4}$")
    private const val MAX_TITLE = 160

    class Item(
        val id: String,
        val kind: String,
        val titleEn: String,
        val titleRu: String,
        /** Make the phone look for an update right away (it still only nudges when it is ready). */
        val checkNow: Boolean,
    )

    /** True when the phone's language is Russian: the title in that language is shown. */
    fun isRussian(): Boolean = Locale.getDefault().language == "ru"

    private val executor = Executors.newSingleThreadExecutor()

    /** Files a tracked item that came from the support address. Idempotent: the same ID again (a
     * stored stanza replayed on a later catch-up) adds nothing and says nothing. */
    @JvmStatic
    fun adopt(context: Context, track: Track) {
        val id = track.id?.takeIf { ID.matches(it) } ?: return
        val titleEn = track.titleEn.trim().take(MAX_TITLE)
        val titleRu = track.titleRu.trim().take(MAX_TITLE)
        if (titleEn.isBlank() && titleRu.isBlank()) return
        // Anything that is not one of the known kinds is a plain "change".
        val kind = track.kind?.takeIf { it in BugReportRegistry.KINDS } ?: BugReportRegistry.KIND_CHANGE
        val channel = UpdatePreferences(context).selectedChannel.id
        val isNew = BugReportRegistry(context).adopt(id, channel, kind, titleEn, titleRu)
        if (!isNew) return
        val title =
            if (isRussian()) titleRu.ifBlank { titleEn } else titleEn.ifBlank { titleRu }
        UpdateNotifications.postTracked(context, kind, title)
        if (track.checkNow()) {
            // Off the thread that is parsing stanzas: it is a network call. "forced": support asked,
            // so it runs even with the auto-check off or the wait-between-updates window open. It
            // still only nudges when a release already has what is being tracked.
            val app = context.applicationContext
            executor.execute { UpdateCheckHelper.performCheck(app, forced = true, trackedOnly = true) }
        }
    }

    // The visible answer to a check request starts with one of these, so the chat can show an
    // "Update" button under the one that says a new version is available.
    const val MARK_UPDATE = "\uD83C\uDD95"        // squared NEW
    const val MARK_CURRENT = "\u2705"               // check mark
    const val MARK_FAILED = "\u26A0\uFE0F"         // warning

    /** What a chat message says about an automatic check, or null if it is not one. */
    class CheckAnswer(val updateAvailable: Boolean, val version: String?)

    private val VERSION_AT_END = Regex("(\\d+\\.\\d+\\.\\d+[A-Za-z0-9.\\-]*)\\)?$")

    fun parseCheckAnswer(body: String?): CheckAnswer? {
        if (body == null) return null
        val text = body.trim()
        val update = text.startsWith(MARK_UPDATE + " ")
        if (!update && !text.startsWith(MARK_CURRENT + " ") && !text.startsWith(MARK_FAILED + " ")) return null
        return CheckAnswer(update, VERSION_AT_END.find(text)?.groupValues?.get(1))
    }

    /** Looks for an update (changing nothing on the phone) and posts what it found in the chat with
     * [counterpart]. Runs in the background. */
    @JvmStatic
    fun answerCheck(service: XmppConnectionService, account: Account, counterpart: Jid) {
        executor.execute {
            val prefs = UpdatePreferences(service)
            val result =
                try {
                    UpdateChecker(OkHttpClient()).checkForUpdate(prefs.selectedChannel)
                } catch (_: Exception) {
                    UpdateChecker.CheckResult.CheckFailed
                }
            val current = UpdateChecker.stripBuildMeta(BuildConfig.VERSION_NAME)
            val text =
                when (result) {
                    is UpdateChecker.CheckResult.UpdateAvailable ->
                        MARK_UPDATE + " " +
                            service.getString(
                                R.string.auto_check_update,
                                UpdateChecker.stripBuildMeta(result.info.versionName),
                            )
                    is UpdateChecker.CheckResult.CheckFailed ->
                        MARK_FAILED + " " + service.getString(R.string.auto_check_failed)
                    else -> MARK_CURRENT + " " + service.getString(R.string.auto_check_current, current)
                }
            val conversation = service.findOrCreateConversation(account, counterpart, false, false)
            service.sendMessage(Message(conversation, text, conversation.nextEncryption))
        }
    }
}
