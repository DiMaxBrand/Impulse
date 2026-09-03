package eu.siacs.conversations.ui

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * Wallpaper-derived Material You colors ([dynamicDarkColorScheme]/[dynamicLightColorScheme])
 * require API 31 (Android 12) -- calling them unconditionally crashes on anything older.
 * Below API 31 this falls back to Material3's plain static [darkColorScheme]/[lightColorScheme]
 * defaults, which is the same fallback Compose itself recommends for pre-S devices.
 */
fun dynamicOrStaticColorScheme(context: Context, isDark: Boolean): ColorScheme {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (isDark) darkColorScheme() else lightColorScheme()
    }
}
