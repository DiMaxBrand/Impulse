package eu.siacs.conversations.ui.util

import com.google.common.collect.ImmutableMap

object UriHelper {
    @JvmStatic
    fun parseQueryString(q: String?): Map<String, String?> {
        if (q.isNullOrEmpty()) return ImmutableMap.of()
        val result = LinkedHashMap<String, String?>()
        for (param in q.split("&")) {
            val pair = param.split("=")
            result[pair[0]] = if (pair.size == 2 && pair[1].isNotEmpty()) pair[1] else null
        }
        return result
    }
}
