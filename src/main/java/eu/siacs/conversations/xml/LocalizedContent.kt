package eu.siacs.conversations.xml

import com.google.common.collect.Iterables
import java.util.Locale

class LocalizedContent private constructor(
    val content: String,
    val language: String,
    val count: Int,
) {
    companion object {
        const val STREAM_LANGUAGE = "en"

        @JvmStatic
        fun get(contents: Map<String, String>): LocalizedContent? {
            if (contents.isEmpty()) return null
            val userLanguage = Locale.getDefault().language
            contents[userLanguage]?.let { return LocalizedContent(it, userLanguage, contents.size) }
            contents[STREAM_LANGUAGE]?.let { return LocalizedContent(it, STREAM_LANGUAGE, contents.size) }
            val first = Iterables.get(contents.entries, 0)
            return LocalizedContent(first.value, first.key, contents.size)
        }
    }
}
