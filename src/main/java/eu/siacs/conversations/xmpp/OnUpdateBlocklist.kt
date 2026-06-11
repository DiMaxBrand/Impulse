package eu.siacs.conversations.xmpp

interface OnUpdateBlocklist {

    // Use an enum instead of a boolean to avoid the boolean trap
    // (callers read `OnUpdateBlocklist(Status.BLOCKED)` instead of an ambiguous true/false).
    enum class Status {
        BLOCKED,
        UNBLOCKED
    }

    @Suppress("MethodNameSameAsClassName")
    fun OnUpdateBlocklist(status: Status)
}
