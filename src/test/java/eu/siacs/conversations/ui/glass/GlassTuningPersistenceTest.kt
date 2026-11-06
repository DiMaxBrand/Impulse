package eu.siacs.conversations.ui.glass

import android.os.Looper
import android.content.SharedPreferences
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = android.app.Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class GlassTuningPersistenceTest {
    @Test fun sliderUpdatesStayLiveButPersistOnlyTheLatestValuesAfterSettling() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("glass_tuning", 0)
        prefs.edit().putFloat("blur", 1f).apply()
        GlassTuning.ensureLoaded(context)
        var writes = 0
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "blur") writes++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        repeat(100) { GlassTuning.putBlur(it / 100f) }
        GlassTuning.putRefraction(1.3f)
        assertEquals(0.99f, GlassTuning.blur, 0f)
        assertEquals(1f, prefs.getFloat("blur", -1f), 0f)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(199))
        assertEquals(0, writes)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertEquals(1, writes)
        assertEquals(0.99f, prefs.getFloat("blur", -1f), 0f)
        assertEquals(1.3f, prefs.getFloat("refraction", -1f), 0f)
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
