package eu.siacs.conversations.utils

import com.google.common.collect.ImmutableMap
import java.util.Locale

object LanguageUtils {
    private val LANGUAGE_MAP: Map<String, String> = ImmutableMap.of(
        "german", "de",
        "deutsch", "de",
        "english", "en",
        "russian", "ru",
    )

    @JvmStatic
    fun convert(input: String?): String? {
        if (input == null) return null
        return LANGUAGE_MAP[input.lowercase(Locale.US)] ?: input
    }
}
