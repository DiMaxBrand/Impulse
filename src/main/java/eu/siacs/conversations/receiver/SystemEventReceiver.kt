package eu.siacs.conversations.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.common.base.Strings
import eu.siacs.conversations.Config
import eu.siacs.conversations.Conversations
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.utils.Compatibility

class SystemEventReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_NEEDS_FOREGROUND_SERVICE = "needs_foreground_service"
    }

    override fun onReceive(context: Context, originalIntent: Intent) {
        val intentForService = Intent(context, XmppConnectionService::class.java)
        val action = originalIntent.action
        intentForService.action = if (Strings.isNullOrEmpty(action)) "other" else action
        originalIntent.extras?.let { intentForService.putExtras(it) }
        if ("ui" == action || Conversations.getInstance(context).hasEnabledAccount()) {
            Compatibility.startService(context, intentForService)
        } else {
            Log.d(Config.LOGTAG, "EventReceiver ignored action ${intentForService.action}")
        }
    }
}
