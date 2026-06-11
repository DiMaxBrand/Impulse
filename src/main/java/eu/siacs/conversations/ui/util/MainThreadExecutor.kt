package eu.siacs.conversations.ui.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

class MainThreadExecutor private constructor() : Executor {
    private val handler = Handler(Looper.myLooper()!!)

    override fun execute(command: Runnable) { handler.post(command) }

    companion object {
        private val INSTANCE = MainThreadExecutor()

        @JvmStatic
        fun getInstance(): MainThreadExecutor = INSTANCE
    }
}
