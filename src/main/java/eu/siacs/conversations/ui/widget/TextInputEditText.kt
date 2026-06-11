package eu.siacs.conversations.ui.widget

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.TextView
import java.lang.reflect.Field

/**
 * A wrapper class to fix some weird fuck ups on Meizu devices
 * credit goes to the people in this thread https://github.com/android-in-china/Compatibility/issues/11
 */
class TextInputEditText : com.google.android.material.textfield.TextInputEditText {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun getHint(): CharSequence? {
        val manufacturer = Build.MANUFACTURER.uppercase()
        if (!manufacturer.contains("MEIZU") || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return super.getHint()
        } else {
            return try {
                getSuperHintHack()
            } catch (e: Exception) {
                super.getHint()
            }
        }
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun getSuperHintHack(): CharSequence? {
        val hintField: Field = TextView::class.java.getDeclaredField("mHint")
        hintField.isAccessible = true
        return hintField.get(this) as? CharSequence
    }
}
