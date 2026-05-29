package eu.siacs.conversations.xmpp.manager

import android.content.Context
import android.util.Log
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.Config
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import java.time.Duration
import java.time.Instant

class ActivityManager(context: Context, connection: XmppConnection) :
    AbstractManager(context, connection) {

    private val appSettings = AppSettings(context)

    private var activity: Activity? = null

    init {
        reset()
    }

    fun record(address: Jid, activityType: ActivityType) {
        val activity = Activity(address, Instant.now(), activityType)
        Log.d(Config.LOGTAG, "recording $activity")
        this.activity = activity
    }

    fun reset() {
        this.activity =
            Activity(getAccount().getJid().asBareJid(), Instant.MIN, ActivityType.NONE)
    }

    fun isInGracePeriod(): Boolean {
        val activity = this.activity ?: return false
        val gracePeriod = appSettings.getGracePeriodLength()
        if (gracePeriod.isZero) return false
        val until = activity.instant.plus(gracePeriod)
        val now = Instant.now()
        if (until.isBefore(now)) return false
        val account = getAccount()
        Log.d(
            Config.LOGTAG,
            "${account.getJid().asBareJid()}: in grace period for ${Duration.between(now, until)} due to $activity"
        )
        return true
    }

    data class Activity(val address: Jid, val instant: Instant, val activityType: ActivityType)

    enum class ActivityType {
        DISPLAYED,
        CHAT_STATE,
        MESSAGE,
        NONE
    }
}
