package eu.siacs.conversations.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The last result a [SilentInstaller] session reported, for screens that want to show it (the
 * Developer Options test). The nightly update itself never reads this.
 */
object InstallStatusBus {
    data class Result(val status: Int, val message: String?, val at: Long = System.nanoTime())

    private val state = MutableStateFlow<Result?>(null)
    val last: StateFlow<Result?> = state

    fun publish(status: Int, message: String?) {
        state.value = Result(status, message)
    }

    fun consume() {
        state.value = null
    }
}
