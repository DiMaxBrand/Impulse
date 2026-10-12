package eu.siacs.conversations.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.preference.PreferenceManager

/** How much the app is allowed to move. Stored as the pref string [key]. */
enum class AnimationLevel(val key: String) {
    FULL("full"),
    AUTOMATIC("auto"),
    REDUCED("reduced"),
    OFF("off");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: FULL
    }
}

enum class ChatBackground(val key: String) {
    PLAIN("plain"),
    TONAL("tonal"),
    IMAGE("image");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: PLAIN
    }
}

/**
 * Compose-observable view of the "Appearance" preferences that the chat and call screens read.
 * Backed by the default SharedPreferences (same store as every other setting); a listener keeps the
 * snapshot state in sync, so a change made in the Appearance screen shows up in an already-open
 * chat without restarting anything.
 */
object AppearanceState {
    const val TEXT_SCALE = "message_text_scale"
    const val DATE_PILLS = "show_date_pills"
    const val HAPTICS = "haptic_feedback"
    const val CHAT_BACKGROUND = "chat_background"
    const val CHAT_BACKGROUND_DIM = "chat_background_dim"
    const val CHAT_BACKGROUND_IMAGE = "chat_background_image"
    const val ANIMATION_LEVEL = "animation_level"

    var textScale by mutableFloatStateOf(1f)
        private set
    var showDatePills by mutableStateOf(true)
        private set
    var haptics by mutableStateOf(true)
        private set
    var chatBackground by mutableStateOf(ChatBackground.PLAIN)
        private set
    /** 0..100 percent of black laid over a background image. */
    var backgroundDim by mutableIntStateOf(30)
        private set
    var backgroundImagePath by mutableStateOf<String?>(null)
        private set
    var animationLevel by mutableStateOf(AnimationLevel.FULL)
        private set

    /** True when the rubber band / bubble size animations should run (level Full, or Automatic
     * on a device that is neither in battery saver nor has system animations switched off). */
    var fullEffects by mutableStateOf(true)
        private set
    /** Level Off (or Automatic with system animations disabled): also drop decorative springs. */
    var noMotion by mutableStateOf(false)
        private set

    private var appContext: Context? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, _ -> load(p) }

    @JvmStatic
    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        // The old "Larger font" switch becomes the first notch of the new text size slider.
        if (!prefs.getBoolean("text_scale_migrated_v1", false)) {
            val edit = prefs.edit().putBoolean("text_scale_migrated_v1", true)
            if (prefs.getBoolean("large_font", false) && !prefs.contains(TEXT_SCALE)) {
                edit.putFloat(TEXT_SCALE, 1.15f)
            }
            edit.apply()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        load(prefs)
    }

    /** Re-evaluates the "Automatic" level (call when the app comes to the foreground). */
    fun refresh() {
        appContext?.let { load(PreferenceManager.getDefaultSharedPreferences(it)) }
    }

    private fun load(p: SharedPreferences) {
        textScale = p.getFloat(TEXT_SCALE, 1f).coerceIn(0.85f, 1.6f)
        showDatePills = p.getBoolean(DATE_PILLS, true)
        haptics = p.getBoolean(HAPTICS, true)
        chatBackground = ChatBackground.fromKey(p.getString(CHAT_BACKGROUND, null))
        backgroundDim = p.getInt(CHAT_BACKGROUND_DIM, 30).coerceIn(0, 80)
        backgroundImagePath = p.getString(CHAT_BACKGROUND_IMAGE, null)
        animationLevel = AnimationLevel.fromKey(p.getString(ANIMATION_LEVEL, null))
        val ctx = appContext
        val systemOff = ctx != null && Settings.Global.getFloat(
            ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        val saver = ctx != null &&
            (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
        fullEffects = when (animationLevel) {
            AnimationLevel.FULL -> true
            AnimationLevel.AUTOMATIC -> !systemOff && !saver
            else -> false
        }
        noMotion = animationLevel == AnimationLevel.OFF ||
            (animationLevel == AnimationLevel.AUTOMATIC && systemOff)
    }
}

/** The Compose haptic feedback, silenced when the user turned haptics off in Appearance. */
@Composable
fun rememberAppHaptics(): HapticFeedback {
    val real = LocalHapticFeedback.current
    return object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            if (AppearanceState.haptics) real.performHapticFeedback(hapticFeedbackType)
        }
    }
}
