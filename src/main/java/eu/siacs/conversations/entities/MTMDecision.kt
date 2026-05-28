package eu.siacs.conversations.entities

class MTMDecision {
    companion object {
        const val DECISION_INVALID = 0
        const val DECISION_ABORT = 1
        const val DECISION_ONCE = 2
        const val DECISION_ALWAYS = 3
    }

    var state = DECISION_INVALID
}
