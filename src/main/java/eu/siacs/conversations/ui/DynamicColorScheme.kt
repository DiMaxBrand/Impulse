package eu.siacs.conversations.ui

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeContent
import eu.siacs.conversations.AppSettings

/**
 * Colour scheme for every Compose screen.
 *
 * - "Dynamic colors" on and API 31+: the wallpaper-derived Material You scheme.
 * - Otherwise: a scheme generated from the accent seed ([AppSettings.getAccentSeed], default the
 *   app's green). It uses the same algorithm ([SchemeContent]) that Material's own
 *   `DynamicColors` applies to the View/XML screens for a content-based seed, so both stacks agree.
 */
fun dynamicOrStaticColorScheme(context: Context, isDark: Boolean): ColorScheme {
    val settings = AppSettings(context)
    return if (settings.isDynamicColorsDesired && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        seedColorScheme(settings.accentSeed, isDark)
    }
}

/** Builds a full Compose [ColorScheme] from a single seed colour. */
fun seedColorScheme(seed: Int, isDark: Boolean): ColorScheme {
    val scheme: DynamicScheme = SchemeContent(Hct.fromInt(seed), isDark, 0.0)
    val m = MaterialDynamicColors()
    fun DynamicColor.c() = Color(getArgb(scheme))
    return ColorScheme(
        primary = m.primary().c(),
        onPrimary = m.onPrimary().c(),
        primaryContainer = m.primaryContainer().c(),
        onPrimaryContainer = m.onPrimaryContainer().c(),
        inversePrimary = m.inversePrimary().c(),
        secondary = m.secondary().c(),
        onSecondary = m.onSecondary().c(),
        secondaryContainer = m.secondaryContainer().c(),
        onSecondaryContainer = m.onSecondaryContainer().c(),
        tertiary = m.tertiary().c(),
        onTertiary = m.onTertiary().c(),
        tertiaryContainer = m.tertiaryContainer().c(),
        onTertiaryContainer = m.onTertiaryContainer().c(),
        background = m.background().c(),
        onBackground = m.onBackground().c(),
        surface = m.surface().c(),
        onSurface = m.onSurface().c(),
        surfaceVariant = m.surfaceVariant().c(),
        onSurfaceVariant = m.onSurfaceVariant().c(),
        surfaceTint = m.primary().c(),
        inverseSurface = m.inverseSurface().c(),
        inverseOnSurface = m.inverseOnSurface().c(),
        error = m.error().c(),
        onError = m.onError().c(),
        errorContainer = m.errorContainer().c(),
        onErrorContainer = m.onErrorContainer().c(),
        outline = m.outline().c(),
        outlineVariant = m.outlineVariant().c(),
        scrim = Color.Black,
        surfaceBright = m.surfaceBright().c(),
        surfaceDim = m.surfaceDim().c(),
        surfaceContainer = m.surfaceContainer().c(),
        surfaceContainerHigh = m.surfaceContainerHigh().c(),
        surfaceContainerHighest = m.surfaceContainerHighest().c(),
        surfaceContainerLow = m.surfaceContainerLow().c(),
        surfaceContainerLowest = m.surfaceContainerLowest().c(),
        primaryFixed = m.primaryFixed().c(),
        primaryFixedDim = m.primaryFixedDim().c(),
        onPrimaryFixed = m.onPrimaryFixed().c(),
        onPrimaryFixedVariant = m.onPrimaryFixedVariant().c(),
        secondaryFixed = m.secondaryFixed().c(),
        secondaryFixedDim = m.secondaryFixedDim().c(),
        onSecondaryFixed = m.onSecondaryFixed().c(),
        onSecondaryFixedVariant = m.onSecondaryFixedVariant().c(),
        tertiaryFixed = m.tertiaryFixed().c(),
        tertiaryFixedDim = m.tertiaryFixedDim().c(),
        onTertiaryFixed = m.onTertiaryFixed().c(),
        onTertiaryFixedVariant = m.onTertiaryFixedVariant().c(),
    )
}
