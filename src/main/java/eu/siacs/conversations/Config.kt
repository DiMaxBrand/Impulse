package eu.siacs.conversations

import android.net.Uri
import eu.siacs.conversations.xmpp.Jid
import im.conversations.android.xmpp.model.state.Active
import im.conversations.android.xmpp.model.state.ChatStateNotification
import java.util.Locale

object Config {
    @JvmField val LOGTAG: String = BuildConfig.APP_NAME.lowercase(Locale.US)

    const val QUICK_LOG = false

    @JvmField val BUG_REPORTS: Jid = Jid.of("support@on-chat.ru")
    // Official Impulse announcements channel — auto-joined once per account, new or existing,
    // the first time it connects after this feature shipped (see BindProcessor's
    // OPTION_NEWS_CHANNEL_JOINED handling). Does not re-join if the user later leaves.
    @JvmField val NEWS_CHANNEL: Jid = Jid.of("news@conference.on-chat.ru")
    @JvmField val HELP: Uri = Uri.parse("https://help.conversations.im")
    const val MAGIC_CREATE_DOMAIN = "conversations.im"
    @JvmField val QUICKSY_DOMAIN: Jid = Jid.of("quicksy.im")

    const val CHANNEL_DISCOVERY = "https://search.jabber.network"

    const val DISALLOW_REGISTRATION_IN_UI = false
    const val USE_RANDOM_RESOURCE_ON_EVERY_BIND = false
    const val MESSAGE_DISPLAYED_SYNCHRONIZATION = true
    const val ALLOW_NON_TLS_CONNECTIONS = false

    const val CONTACT_SYNC_RETRY_INTERVAL = 1000L * 60 * 5
    const val QUICKSTART_ENABLED = true

    // Grace period before "away when app is exited" actually flips presence to AWAY — avoids
    // flickering to Away and back for a quick app switch (e.g. checking a notification).
    const val AWAY_ON_APP_EXIT_GRACE_PERIOD_MILLIS = 1000L * 30

    // How long continuously AWAY (from either trigger — screen lock or app exit) before presence
    // escalates from AWAY to XA (Extended Away). Deliberately much longer than the AWAY
    // thresholds themselves — this is meant to signal genuine long-term absence, not just having
    // stepped out for a bit.
    const val EXTENDED_AWAY_THRESHOLD_MILLIS = 1000L * 60 * 60 * 5

    const val HIDE_MESSAGE_TEXT_IN_NOTIFICATION = false
    const val ALWAYS_NOTIFY_BY_DEFAULT = false
    const val SUPPRESS_ERROR_NOTIFICATION = false

    const val PING_MAX_INTERVAL = 300
    const val IDLE_PING_INTERVAL = 600
    const val PING_MIN_INTERVAL = 30
    const val LOW_PING_TIMEOUT = 1
    const val PING_TIMEOUT = 15
    const val SOCKET_TIMEOUT = 12_000
    const val SOCKET_TIMEOUT_LOW = 8_000
    const val CONNECT_TIMEOUT = 90
    const val POST_CONNECTIVITY_CHANGE_PING_INTERVAL = 30
    const val CONNECT_DISCO_TIMEOUT = 20

    const val AVATAR_THUMBNAIL_SIZE = 256
    const val AVATAR_THUMBNAIL_CHAR_LIMIT = 16000
    const val AVATAR_FULL_SIZE = 1920

    const val USE_OPUS_VOICE_MESSAGES = false
    const val MESSAGE_MERGE_WINDOW = 90_000

    const val PAGE_SIZE = 50
    const val MAX_NUM_PAGES = 3
    const val MAX_SEARCH_RESULTS = 300
    const val REFRESH_UI_INTERVAL = 500

    const val MAX_DISPLAY_MESSAGE_CHARS = 4096
    const val MAX_STORAGE_MESSAGE_CHARS = 2 * 1024 * 1024

    const val MILLISECONDS_IN_DAY = 24 * 60 * 60 * 1000L
    const val OMEMO_AUTO_EXPIRY = 42 * MILLISECONDS_IN_DAY

    const val REMOVE_BROKEN_DEVICES = false
    const val OMEMO_PADDING = false
    const val PUT_AUTH_TAG_INTO_KEY = true
    const val AUTOMATICALLY_COMPLETE_SESSIONS = true
    const val DISABLE_PROXY_LOOKUP = false
    const val USE_DIRECT_JINGLE_CANDIDATES = true
    const val USE_JINGLE_MESSAGE_INIT = true

    const val ENABLE_CAPS_CACHE = true
    const val ENABLE_HTTP_UPLOAD = true
    const val EXTENDED_SM_LOGGING = false
    const val BACKGROUND_STANZA_LOGGING = false
    const val RESET_ATTEMPT_COUNT_ON_NETWORK_CHANGE = true
    const val ENCRYPT_ON_HTTP_UPLOADED = false
    const val X509_VERIFICATION = false
    const val REQUIRE_RTP_VERIFICATION = false
    const val JINGLE_MESSAGE_INIT_STRICT_OFFLINE_CHECK = false
    const val JINGLE_MESSAGE_INIT_STRICT_DEVICE_TIMEOUT = false
    const val DEVICE_DISCOVERY_TIMEOUT = 12_000L

    const val IGNORE_ID_REWRITE_IN_MUC = true
    const val MUC_LEAVE_BEFORE_JOIN = false
    const val TREAT_MULTI_CONTENT_AS_INVALID = false

    const val MAM_MAX_CATCHUP = MILLISECONDS_IN_DAY * 5
    const val MAM_MAX_MESSAGES = 750

    @JvmField val DEFAULT_CHAT_STATE: Class<out ChatStateNotification> = Active::class.java
    const val TYPING_TIMEOUT = 8
    const val EXPIRY_INTERVAL = 30 * 60 * 1000

    object Map {
        const val INITIAL_ZOOM_LEVEL = 4.0
        const val FINAL_ZOOM_LEVEL = 15.0
        const val MY_LOCATION_INDICATOR_SIZE = 10
        const val MY_LOCATION_INDICATOR_OUTLINE_SIZE = 3
        const val LOCATION_FIX_TIME_DELTA = 1000L * 10
        const val LOCATION_FIX_SPACE_DELTA = 10f
        const val LOCATION_FIX_SIGNIFICANT_TIME_DELTA = 1000L * 60 * 2
    }

    const val QUOTE_MAX_DEPTH = 7
    const val QUOTING_MAX_DEPTH = 2
}
