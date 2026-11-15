package eu.siacs.conversations.ui

import android.content.Context
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.utils.SupportAccounts

/** Select only explicitly bilingual news posts; ordinary or malformed posts stay unchanged. */
object NewsMessageText {
    private val marker = Regex("(?im)^[ \\t]*(EN|RU|РУС)[ \\t]*:[ \\t]*")

    fun select(raw: String, language: String): String {
        val matches = marker.findAll(raw).toList()
        if (matches.size != 2 || matches.first().range.first != 0) return raw
        val sections = matches.mapIndexed { index, match ->
            val code = match.groupValues[1].uppercase().let { if (it == "РУС") "RU" else it }
            code to raw.substring(match.range.last + 1, matches.getOrNull(index + 1)?.range?.first ?: raw.length).trim()
        }.toMap()
        if (sections.size != 2 || sections["RU"].isNullOrBlank() || sections["EN"].isNullOrBlank()) return raw
        return sections[if (language.equals("ru", ignoreCase = true)) "RU" else "EN"] ?: raw
    }

    @JvmStatic
    fun display(context: Context, message: Message): String {
        val raw = message.body ?: return ""
        val conversation = message.getConversation()
        if (conversation.getMode() != Conversational.MODE_MULTI ||
            conversation.getAddress().asBareJid() != Config.NEWS_CHANNEL.asBareJid() ||
            SupportAccounts.hasSupportAccount(context)) return raw
        return select(raw, androidx.core.os.ConfigurationCompat.getLocales(context.resources.configuration)[0]?.language.orEmpty())
    }
}
