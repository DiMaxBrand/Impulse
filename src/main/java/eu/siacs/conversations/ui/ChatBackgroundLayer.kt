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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Paints the chat background chosen in Appearance behind the message list. */
@Composable
internal fun ChatBackgroundLayer(modifier: Modifier = Modifier) {
    when (AppearanceState.chatBackground) {
        ChatBackground.PLAIN -> Unit
        ChatBackground.TONAL -> {
            val scheme = MaterialTheme.colorScheme
            Box(modifier.background(scheme.surfaceContainerLow).background(scheme.primary.copy(alpha = 0.05f)))
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
