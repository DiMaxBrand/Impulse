package eu.siacs.conversations.update

import android.content.Context
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.persistance.DatabaseBackend
import eu.siacs.conversations.utils.SupportAccounts

/** The conversation is the record. No second registry of support's outgoing items. */
object SupportChatTracking {
    private val idPattern = Regex("(?:BUG|IMP|NEW|CHG)-[A-Z0-9]{4}")
    private val reportHeader = Regex("^Report ID: ((?:BUG|IMP|NEW|CHG)-[A-Z0-9]{4})$")
    private const val HEADER = "Tracking ID: "

    data class Details(val id: String, val kind: String, val titleEn: String, val titleRu: String) {
        fun title(russian: Boolean) = (if (russian) titleRu else titleEn).ifBlank { titleEn.ifBlank { titleRu } }
    }

    data class Entry(
        val details: Details, val conversationUuid: String, val contact: String,
        val time: Long, val fixedIn: String? = null,
    )

    fun announcement(item: SupportTracking.Item): String {
        val enKind = when (item.kind) {
            BugReportRegistry.KIND_BUG -> "bug fix"
            BugReportRegistry.KIND_IMPROVEMENT -> "improvement"
            BugReportRegistry.KIND_FEATURE -> "new feature"
            else -> "change"
        }
        val ruHeading = when (item.kind) {
            BugReportRegistry.KIND_BUG -> "Исправление ошибки добавлено в отслеживание"
            BugReportRegistry.KIND_IMPROVEMENT -> "Улучшение добавлено в отслеживание"
            BugReportRegistry.KIND_FEATURE -> "Новая функция добавлена в отслеживание"
            else -> "Изменение добавлено в отслеживание"
        }
        val ruObject = when (item.kind) {
            BugReportRegistry.KIND_BUG -> "этим исправлением"
            BugReportRegistry.KIND_IMPROVEMENT -> "этим улучшением"
            BugReportRegistry.KIND_FEATURE -> "этой функцией"
            else -> "этим изменением"
        }
        // Both supplied translations travel with the message. Stable fields make the record
        // readable on another support device regardless of its interface language.
        val en = item.titleEn.ifBlank { item.titleRu }.replace('\n', ' ').replace('\r', ' ')
        val ru = item.titleRu.ifBlank { item.titleEn }.replace('\n', ' ').replace('\r', ' ')
        return "$ruHeading\n\n$ru\n\n" +
            "Ожидается обновление с $ruObject. Когда оно станет доступно, будет показано уведомление.\n\n" +
            "Номер отслеживания: ${item.id}\n\n" +
            "${enKind.replaceFirstChar { it.uppercase() }} added to tracking\n\n$en\n\n" +
            "An update containing this ${if (item.kind == BugReportRegistry.KIND_BUG) "fix" else enKind} is awaited. " +
            "A notification will be shown when it becomes available.\n\n" +
            "${HEADER}${item.id}"
    }

    fun parse(body: String, incoming: Boolean): Details? {
        val lines = body.lineSequence().toList()
        val first = lines.firstOrNull() ?: return null
        if (incoming) {
            val id = reportHeader.matchEntire(first)?.groupValues?.get(1) ?: return null
            // Only the documented report envelope, not arbitrary mentions/quoted IDs.
            if (lines.getOrNull(1)?.startsWith("Update channel: ") != true) return null
            val title = body.substringAfter("\n\n", "").lineSequence()
                .firstOrNull { it.isNotBlank() }?.take(160).orEmpty()
            return Details(id, BugReportRegistry.KIND_BUG, title, title)
        }
        val marker = lines.indexOfFirst { it.startsWith(HEADER) }
        if (marker < 0) return null
        val id = lines[marker].removePrefix(HEADER)
        if (!idPattern.matches(id)) return null
        val (kind, enHeading, ruHeading) = when (id.substringBefore('-')) {
            "BUG" -> Triple(BugReportRegistry.KIND_BUG, "Bug fix", "Исправление ошибки")
            "IMP" -> Triple(BugReportRegistry.KIND_IMPROVEMENT, "Improvement", "Улучшение")
            "NEW" -> Triple(BugReportRegistry.KIND_FEATURE, "New feature", "Новая функция")
            else -> Triple(BugReportRegistry.KIND_CHANGE, "Change", "Изменение")
        }
        val english = lines.indexOf("$enHeading added to tracking")
        val russian = lines.indexOf("$ruHeading ${if (kind == BugReportRegistry.KIND_FEATURE) "добавлена" else "добавлено"} в отслеживание")
        if (english < 0 || russian < 0) return null
        val en = lines.getOrNull(english + 2) ?: return null
        val ru = lines.getOrNull(russian + 2) ?: return null
        if (en.isBlank() && ru.isBlank()) return null
        return Details(id, kind, en.take(160), ru.take(160))
    }

    /** All locally stored history, including archived conversations; never just the loaded page. */
    fun read(context: Context): List<Entry> {
        if (!SupportAccounts.hasSupportAccount(context)) return emptyList()
        val support = Config.BUG_REPORTS.asBareJid()
        val db = DatabaseBackend.getInstance(context).readableDatabase
        return readDatabase(db, support.local, support.domain.toString())
    }

    internal fun readDatabase(db: android.database.sqlite.SQLiteDatabase, local: String, domain: String): List<Entry> {
        val rows = mutableListOf<Entry>()
        db.rawQuery(
            """SELECT m.body, m.status, m.timeSent, c.uuid, c.contactJid
               FROM messages m JOIN conversations c ON m.conversationUuid=c.uuid
               JOIN accounts a ON c.accountUuid=a.uuid
               WHERE lower(a.username)=? AND lower(a.server)=? AND c.mode=0
               AND m.type IN (0,3) AND (m.body LIKE 'Report ID: %' OR m.body LIKE '%Tracking ID: %')
               ORDER BY m.timeSent DESC""",
            arrayOf(local.lowercase(), domain.lowercase()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val details = parse(cursor.getString(0) ?: continue,
                    cursor.getInt(1) == Message.STATUS_RECEIVED) ?: continue
                rows += Entry(details, cursor.getString(3), cursor.getString(4), cursor.getLong(2))
            }
        }
        return rows.distinctBy { it.contact.substringBefore('/') to it.details.id }
    }

    fun match(entries: List<Entry>, releases: List<UpdateChecker.NewerRelease>): List<Entry> {
        val ordered = releases.filter { UpdateChecker.parseVersion(it.versionName) != null }
            .sortedWith { a, b -> UpdateChecker.compareSemver(
                UpdateChecker.parseVersion(a.versionName)!!, UpdateChecker.parseVersion(b.versionName)!!) }
            .map { it.copy(notes = it.notes.replace(Regex("[\\u2010-\\u2015\\u2212\\u00AD]"), "-")) }
        return entries.map { entry ->
            val pattern = Regex("(?<![A-Z0-9-])${Regex.escape(entry.details.id)}(?![A-Z0-9-])", RegexOption.IGNORE_CASE)
            val match = ordered.firstOrNull { release ->
                pattern.containsMatchIn(release.notes)
            }
            entry.copy(fixedIn = match?.versionName)
        }
    }

    /** Rebuilds recipient tracking from authenticated support conversations without replying or
     * replaying "check now" commands. The existing registry only supplies notification state. */
    @JvmStatic
    fun restoreRecipient(context: Context) {
        val registry = BugReportRegistry(context)
        val channel = UpdatePreferences(context).selectedChannel.id
        DatabaseBackend.getInstance(context).readableDatabase.rawQuery(
            """SELECT m.body, m.timeSent FROM messages m JOIN conversations c ON m.conversationUuid=c.uuid
               WHERE c.mode=0 AND lower(c.contactJid)=? AND m.status=0 AND m.type=0
               AND m.body LIKE '%Tracking ID: %' ORDER BY m.timeSent""",
            arrayOf(Config.BUG_REPORTS.asBareJid().toString().lowercase()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val details = parse(cursor.getString(0) ?: continue, false) ?: continue
                registry.adopt(details.id, channel, details.kind, details.titleEn, details.titleRu, cursor.getLong(1))
            }
        }
    }

    @JvmStatic
    fun receiveAnnouncement(context: Context, message: Message, notify: Boolean) {
        val conversation = message.getConversation()
        if (message.getStatus() != Message.STATUS_RECEIVED || conversation.getMode() != 0 ||
            !SupportAccounts.isSupportAddress(conversation.getAddress())) return
        val details = parse(message.getBody() ?: return, false) ?: return
        if (notify) {
            SupportTracking.adopt(context, im.conversations.android.xmpp.model.support.Track.create(
                details.id, details.kind, details.titleEn, details.titleRu, false))
        } else {
            BugReportRegistry(context).adopt(details.id, UpdatePreferences(context).selectedChannel.id,
                details.kind, details.titleEn, details.titleRu, message.getTimeSent())
        }
    }
}
