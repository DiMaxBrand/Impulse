package eu.siacs.conversations.update

import android.content.Context
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.utils.SupportAccounts
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Something support tracks for a person: a bug fix, an improvement or a new feature. Support (a
 * phone with the support account connected) picks "Track for this person" in a chat's attach menu
 * and fills in what it is, in English and in Russian; that sends the person a message with a
 * tracking ID. The person's Impulse, seeing the message come from the support address in a
 * one-to-one chat, files the ID as one of its own waiting items ([BugReportRegistry.adopt]), so the
 * Updates screen shows it by the title typed for it and a notice arrives when a release's notes
 * name the ID. The message can also make the phone look for an update right away.
 *
 * The message is ordinary readable text, so any other chat client just shows it; the first lines
 * are machine-read, and only when the message comes from the support address (the server vouches
 * for who sent a message, so nobody else can plant an item this way, whatever a modified copy of the
 * app shows on its own screen).
 */
object SupportTracking {

    private const val HEAD = "\uD83D\uDCCC Tracked for you: "
    private val HEAD_LINE = Regex("^\\s*" + Regex.escape(HEAD) + "([A-Z]{3}-[A-Z0-9]{4})\\s*$")
    private const val MAX_TITLE = 160

    class Item(
        val id: String,
        val kind: String,
        val titleEn: String,
        val titleRu: String,
        /** Make the phone look for an update right away. */
        val checkNow: Boolean,
    )

    /** The text to send: the machine-read lines, an optional note for the person, and a closing
     * line in both languages (the sender cannot know which one the person reads). */
    fun messageBody(item: Item, note: String): String = buildString {
        append(HEAD).append(item.id).append('\n')
        append("kind: ").append(item.kind).append('\n')
        if (item.titleEn.isNotBlank()) append("en: ").append(oneLine(item.titleEn)).append('\n')
        if (item.titleRu.isNotBlank()) append("ru: ").append(oneLine(item.titleRu)).append('\n')
        append("check: ").append(if (item.checkNow) "yes" else "no")
        if (note.isNotBlank()) append("\n\n").append(note.trim())
        append("\n\nImpulse will tell you when a release has it.")
        append("\n\u0418\u043c\u043f\u0443\u043b\u044c\u0441 \u0441\u043e\u043e\u0431\u0449\u0438\u0442, \u043a\u043e\u0433\u0434\u0430 \u0432\u044b\u0439\u0434\u0435\u0442 \u0440\u0435\u043b\u0438\u0437 \u0441 \u044d\u0442\u0438\u043c.")
    }

    private fun oneLine(s: String) = s.replace(Regex("\\s+"), " ").trim().take(MAX_TITLE)

    /** The item in a message body that follows the format, or null. */
    fun parse(body: String?): Item? {
        if (body == null) return null
        val lines = body.lines()
        val id = HEAD_LINE.find(lines.firstOrNull() ?: return null)?.groupValues?.get(1) ?: return null
        val fields = HashMap<String, String>()
        for (line in lines.drop(1)) {
            if (line.isBlank()) break
            val colon = line.indexOf(':')
            if (colon <= 0) break
            fields[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val titleEn = fields["en"].orEmpty().take(MAX_TITLE)
        val titleRu = fields["ru"].orEmpty().take(MAX_TITLE)
        if (titleEn.isBlank() && titleRu.isBlank()) return null
        val kind = fields["kind"]?.takeIf { it in BugReportRegistry.KINDS } ?: BugReportRegistry.KIND_BUG
        return Item(id, kind, titleEn, titleRu, fields["check"] != "no")
    }

    /** True when the phone's language is Russian: the title in that language is shown. */
    fun isRussian(): Boolean = Locale.getDefault().language == "ru"

    private val checkExecutor = Executors.newSingleThreadExecutor()

    /** Called for every message just stored. Files the item if this is such a message, from the
     * support address, in a one-to-one chat; posts one quiet notice when it was new, and looks for
     * an update right away when asked to. */
    @JvmStatic
    fun maybeAdopt(context: Context, message: Message) {
        if (message.getStatus() != Message.STATUS_RECEIVED) return
        if (message.getConversation().getMode() != Conversational.MODE_SINGLE) return
        if (!SupportAccounts.isSupportAddress(message.getCounterpart())) return
        val item = parse(message.getBody()) ?: return
        val channel = UpdatePreferences(context).selectedChannel.id
        val isNew =
            BugReportRegistry(context)
                .adopt(item.id, channel, item.kind, item.titleEn, item.titleRu)
        if (!isNew) return
        val title = if (isRussian()) item.titleRu.ifBlank { item.titleEn } else item.titleEn.ifBlank { item.titleRu }
        UpdateNotifications.postTracked(context, item.kind, title)
        if (item.checkNow) {
            // Off the thread that is parsing messages: this is a network call. "forced": support asked,
            // so it goes ahead even with the auto-check off or the wait-between-updates window open.
            val app = context.applicationContext
            checkExecutor.execute { UpdateCheckHelper.performCheck(app, forced = true) }
        }
    }
}
