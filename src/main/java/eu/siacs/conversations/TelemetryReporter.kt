package eu.siacs.conversations

import android.os.Build
import androidx.preference.PreferenceManager
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xmpp.Jid
import im.conversations.android.xmpp.model.stanza.Message

object TelemetryReporter {

    private const val PREF_SENT_VERSION = "telemetry_sent_version"

    @JvmStatic
    fun maybeSend(account: Account, service: XmppConnectionService) {
        val rawJid = BuildConfig.TELEMETRY_JID
        if (rawJid.isNullOrEmpty()) return
        val appSettings = AppSettings(service)
        if (!appSettings.isShareDiagnostics()) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(service)
        if (prefs.getString(PREF_SENT_VERSION, null) == BuildConfig.VERSION_NAME) return
        val to = try { Jid.of(rawJid) } catch (_: Exception) { return }
        val stanza = Message(Message.Type.CHAT)
        stanza.setTo(to)
        stanza.setBody(buildPayload(appSettings))
        service.sendMessagePacket(account, stanza)
        prefs.edit().putString(PREF_SENT_VERSION, BuildConfig.VERSION_NAME).apply()
    }

    private fun buildPayload(s: AppSettings): String = buildString {
        appendLine("Это системное сообщение автоматически проверяет, корректно ли перенесены текущие настройки приложения Impulse.")
        appendLine("[Impulse Diagnostics]")
        append("v=${BuildConfig.VERSION_NAME} build=${BuildConfig.VERSION_CODE} api=${Build.VERSION.SDK_INT}")
        appendLine()
        append("away_screen=${s.isAwayWhenScreenLocked().b} dnd_sync=${s.isDndSyncSystem().b} ")
        append("dnd_silent=${s.isDndIncludeSilentMode().b} manually_presence=${s.isUserManagedAvailability().b}")
        appendLine()
        append("btbv=${s.isBTBVEnabled().b} omemo=${s.getOmemo()} ")
        append("trust_ca=${s.isTrustSystemCAStore().b} channel_binding=${s.isRequireChannelBinding().b} tls13=${s.isRequireTlsV13().b}")
        appendLine()
        append("auto_accept=${s.autoAcceptFileSize.orElse(0L)} video=${s.videoCompression} picture=${s.getPictureCompression()}")
        appendLine()
        append("dynamic_colors=${s.isDynamicColorsDesired().b} theme=${s.themeSetting} large_font=${s.isLargeFont().b} ")
        append("colorful=${s.isColorfulChatBubbles().b} align_start=${s.isAlignStart().b}")
        appendLine()
        append("read_receipts=${s.isReadReceipts().b} chat_states=${s.isSendChatStates().b} ")
        append("last_activity=${s.isBroadcastLastActivity().b} entity_time=${s.isEntityTime().b} ")
        append("allow_correction=${s.isAllowMessageCorrection().b}")
        appendLine()
        append("crash_reports=${s.isSendCrashReports().b} screenshots=${s.isAllowScreenshots().b} ")
        append("shared_storage=${s.isUseSharedStorage().b} enter_send=${s.isEnterSend().b} ")
        append("auto_send_rec=${s.isAutoSendRecording().b} scroll_bottom=${s.isScrollToBottom().b} ")
        append("call_integration=${s.isCallIntegration().b}")
        appendLine()
        appendLine()
        append("Кстати, это чат с поддержкой Impulse. Вы можете написать сюда в любое время, если вам нужна помощь — скорее всего, мы ответим в тот же день.")
    }

    private val Boolean.b get() = if (this) 1 else 0
}
