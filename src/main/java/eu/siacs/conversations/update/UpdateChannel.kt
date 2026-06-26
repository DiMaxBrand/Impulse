package eu.siacs.conversations.update

enum class UpdateChannel(val id: String) {
    STABLE("stable"),
    RC("rc"),
    BETA("beta"),
    ALPHA("alpha");

    companion object {
        fun fromId(id: String): UpdateChannel =
            entries.firstOrNull { it.id == id } ?: STABLE
    }
}
