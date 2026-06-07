package eu.siacs.conversations.services

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

class DebouncedInterfaceUpdater(private val service: XmppConnectionService) : Runnable {
    companion object {
        private const val UI_REFRESH_THRESHOLD = 250
        private val LAST_UI_UPDATE_CALL = AtomicLong(0)
    }

    override fun run() {
        synchronized(LAST_UI_UPDATE_CALL) {
            if (SystemClock.elapsedRealtime() - LAST_UI_UPDATE_CALL.get() >= UI_REFRESH_THRESHOLD) {
                LAST_UI_UPDATE_CALL.set(SystemClock.elapsedRealtime())
                service.updateConversationUi()
            }
        }
    }
}
