package eu.siacs.conversations.ui.util

import com.google.common.collect.ImmutableMap

object UriHelper {
    @JvmStatic
    fun parseQueryString(q: String?): Map<String, String?> {
        if (q.isNullOrEmpty()) return ImmutableMap.of()
        val builder = ImmutableMap.builder<String, String?>()
        for (param in q.split("&")) {
            val pair = param.split("=")
            builder.put(pair[0], if (pair.size == 2 && pair[1].isNotEmpty()) pair[1] else null)
        }
        return builder.build()
    }
}
