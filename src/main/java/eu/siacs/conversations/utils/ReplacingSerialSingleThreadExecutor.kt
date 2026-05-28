package eu.siacs.conversations.utils

class ReplacingSerialSingleThreadExecutor(name: String) : SerialSingleThreadExecutor(name) {
    @Synchronized
    override fun execute(r: Runnable) {
        tasks.clear()
        (active as? Cancellable)?.cancel()
        super.execute(r)
    }

    @Synchronized
    fun cancelRunningTasks() {
        tasks.clear()
        (active as? Cancellable)?.cancel()
    }
}
