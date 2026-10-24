package eu.siacs.conversations.ui.glass

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The knobs of the Glass lab (Appearance, shown while the glass style is on). Every one of them is
 * read where the glass is drawn, so moving a slider changes the screen at once. They are plain
 * multipliers on the built-in looks, with 1 = as designed; they live in their own preferences file.
 */
object GlassTuning {
    private const val PREFS = "glass_tuning"
    private var prefs: SharedPreferences? = null

    /** How blurred the glass is (every piece's own blur is multiplied by this). */
    var blur by mutableFloatStateOf(1f)
        private set

    /** How strongly the rim bends what is behind it. */
    var refraction by mutableFloatStateOf(1f)
        private set

    /** How frosted the menus and panels are: the strength of their blur and veil. */
    var frost by mutableFloatStateOf(1f)
        private set

    /** How much the glass stretches and wobbles. */
    var jelly by mutableFloatStateOf(1f)
        private set

    /** The colour fringes on the rim of the pieces that have them. */
    var fringes by mutableStateOf(true)
        private set

    /** While a list flies past or the keyboard moves, glass is a picture taken a few times a second. */
    var lessOften by mutableStateOf(true)
        private set

    /** The blurred bands under the status bar and the gesture bar. */
    var barBlur by mutableStateOf(true)
        private set

    fun ensureLoaded(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        blur = p.getFloat("blur", 1f)
        refraction = p.getFloat("refraction", 1f)
        frost = p.getFloat("frost", 1f)
        jelly = p.getFloat("jelly", 1f)
        fringes = p.getBoolean("fringes", true)
        lessOften = p.getBoolean("less_often", true)
        barBlur = p.getBoolean("bar_blur", true)
    }

    fun putBlur(v: Float) { blur = v; prefs?.edit()?.putFloat("blur", v)?.apply() }
    fun putRefraction(v: Float) { refraction = v; prefs?.edit()?.putFloat("refraction", v)?.apply() }
    fun putFrost(v: Float) { frost = v; prefs?.edit()?.putFloat("frost", v)?.apply() }
    fun putJelly(v: Float) { jelly = v; prefs?.edit()?.putFloat("jelly", v)?.apply() }
    fun putFringes(v: Boolean) { fringes = v; prefs?.edit()?.putBoolean("fringes", v)?.apply() }
    fun putLessOften(v: Boolean) { lessOften = v; prefs?.edit()?.putBoolean("less_often", v)?.apply() }
    fun putBarBlur(v: Boolean) { barBlur = v; prefs?.edit()?.putBoolean("bar_blur", v)?.apply() }

    fun reset() {
        putBlur(1f); putRefraction(1f); putFrost(1f); putJelly(1f)
        putFringes(true); putLessOften(true); putBarBlur(true)
    }
}
