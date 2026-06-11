package eu.siacs.conversations.ui.util

class PendingActionHelper {
    private var pendingAction: PendingAction? = null

    fun push(pendingAction: PendingAction) {
        this.pendingAction = pendingAction
    }

    fun execute() {
        pendingAction?.execute()
        pendingAction = null
    }

    fun undo() {
        pendingAction = null
    }

    fun interface PendingAction {
        fun execute()
    }
}
