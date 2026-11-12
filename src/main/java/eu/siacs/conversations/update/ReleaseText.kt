package eu.siacs.conversations.update

/** Selects display text while leaving the original GitHub body intact for report matching. */
object ReleaseText {
    private val titleMarker = Regex("(?<![A-Za-z0-9])(?:RU|EN):\\s*")

    fun title(raw: String?, language: String): String? {
        if (raw.isNullOrBlank()) return raw
        val markers = titleMarker.findAll(raw).toList()
        if (markers.none { it.value.startsWith("RU:") } || markers.none { it.value.startsWith("EN:") }) return raw
        val sections = markers.mapIndexed { index, marker ->
            marker.value.take(2) to raw.substring(marker.range.last + 1, markers.getOrNull(index + 1)?.range?.first ?: raw.length).trim()
        }.toMap()
        return sections[if (language.equals("ru", ignoreCase = true)) "RU" else "EN"]?.takeIf { it.isNotBlank() }
            ?: raw
    }

    fun notes(raw: String?, language: String, support: Boolean): String? {
        if (raw.isNullOrBlank()) return raw
        val sections = linkedMapOf<String, StringBuilder>()
        var current = "preamble"
        sections[current] = StringBuilder()
        for (line in raw.lines()) {
            val heading = line.trim().removePrefix("### ").removePrefix("## ").removePrefix("# ")
                .trim().trim('*').trim().removeSuffix(":").lowercase()
            val next = when (heading) {
                "ru", "что нового" -> "ru"
                "en", "what's new", "what’s new" -> "en"
                "developer notes" -> "dev"
                "what's changed", "what’s changed", "new contributors", "full changelog" -> "generated"
                else -> null
            }
            if (next != null) {
                current = next
                sections.getOrPut(current) { StringBuilder() }
                if (current == "dev" || current == "generated") sections.getValue(current).appendLine(line)
            } else {
                sections.getValue(current).appendLine(line)
            }
        }
        val structured = sections.containsKey("ru") || sections.containsKey("en")
        val preferred = if (language.equals("ru", ignoreCase = true)) "ru" else "en"
        val main = if (structured) sections[preferred]?.toString().orEmpty() else sections["preamble"]?.toString().orEmpty()
        val mainWithHeading = main.trim().takeIf { it.isNotEmpty() }?.let {
            val heading = if (preferred == "ru") "Что нового" else "What's new"
            "## $heading\n\n$it"
        }.orEmpty()
        val developer = if (support) listOfNotNull(sections["dev"], sections["generated"]).joinToString("\n") else ""
        return listOf(mainWithHeading, developer.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")
    }
}
