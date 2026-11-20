package eu.siacs.conversations.update

enum class UpdateChannel(val id: String) {
    STABLE("stable"),
    RC("rc"),
    BETA("beta"),
    ALPHA("alpha");

    companion object {
        fun fromVersion(version: String): UpdateChannel? {
            val parsed = UpdateChecker.parseVersion(version) ?: return null
            return when (parsed.preType) {
                null -> STABLE
                "rc" -> RC
                "beta" -> BETA
                "alpha" -> ALPHA
                else -> null
            }
        }

        fun fromId(id: String): UpdateChannel =
            entries.firstOrNull { it.id == id } ?: STABLE
    }
}

internal object ChannelTransition {
    fun choose(current: UpdateChannel, target: UpdateChannel, gradual: Boolean, installed: UpdateChannel?): Pair<UpdateChannel, UpdateChannel?> {
        if (!gradual || installed == null || installed.ordinal <= target.ordinal || target.ordinal >= current.ordinal) return target to null
        return advance(current, target, installed)
    }
    fun advance(current: UpdateChannel, target: UpdateChannel, installed: UpdateChannel): Pair<UpdateChannel, UpdateChannel?> {
        if (installed.ordinal >= current.ordinal) return current to target
        return if (installed.ordinal <= target.ordinal) target to null else installed to target
    }
}
