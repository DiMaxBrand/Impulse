package eu.siacs.conversations.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun tonalBackdropColor(): Color {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val base = if (dark) scheme.surfaceContainerLowest else scheme.surfaceDim
    return lerp(base, scheme.primary, if (dark) 0.07f else 0.09f)
}

/** Incoming bubble colour. On a tinted or photo background the usual container tone is too close
 * to the backdrop, so it steps one tone away from it (white in light, lifted grey in dark). */
@Composable
internal fun incomingBubbleColor(): Color {
    val scheme = MaterialTheme.colorScheme
    if (AppearanceState.chatBackground == ChatBackground.PLAIN) return scheme.surfaceContainerHigh
    return if (scheme.surface.luminance() < 0.5f) scheme.surfaceContainerHighest
    else scheme.surfaceContainerLowest
}

/** Paints the chat background chosen in Appearance behind the message list. */
@Composable
internal fun ChatBackgroundLayer(modifier: Modifier = Modifier) {
    when (AppearanceState.chatBackground) {
        ChatBackground.PLAIN -> Unit
        ChatBackground.TONAL -> {
            // Bubbles are light-on-dark-ish tones, so the backdrop has to sit clearly beside
            // them: darker than the bubbles in a light theme, darker still in a dark theme, with
            // only a breath of the accent so it stays a tint.
            Box(modifier.background(tonalBackdropColor()))
        }
        ChatBackground.IMAGE -> {
            val path = AppearanceState.backgroundImagePath
            val bitmap by produceState<ImageBitmap?>(null, path) {
                value = if (path == null) null else withContext(Dispatchers.IO) {
                    runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
                }
            }
            val image = bitmap
            if (image != null) {
                Box(modifier) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                    Box(
                        Modifier.matchParentSize()
                            .background(Color.Black.copy(alpha = AppearanceState.backgroundDim / 100f))
                    )
                }
            }
        }
    }
}
