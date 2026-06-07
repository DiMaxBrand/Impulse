package eu.siacs.conversations.utils

import android.util.Log
import eu.siacs.conversations.Config
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.Executors

open class SerialSingleThreadExecutor(private val name: String) : Executor {

    val tasks = ArrayDeque<Runnable>()
    private val executor: Executor = Executors.newSingleThreadExecutor()
    @Volatile var active: Runnable? = null

    @Synchronized
    override fun execute(r: Runnable) {
        tasks.offer(Runner(r))
        if (active == null) {
            scheduleNext()
        }
    }

    @Synchronized
    private fun scheduleNext() {
        active = tasks.poll()
        if (active != null) {
            executor.execute(active)
            val remaining = tasks.size
            if (remaining > 0) {
                Log.d(Config.LOGTAG, "$remaining remaining tasks on executor '$name'")
            }
        }
    }

    private inner class Runner(private val runnable: Runnable) : Runnable, Cancellable {

        override fun cancel() {
            if (runnable is Cancellable) {
                runnable.cancel()
            }
        }

        override fun run() {
            try {
                runnable.run()
            } finally {
                scheduleNext()
            }
        }
    }
}
