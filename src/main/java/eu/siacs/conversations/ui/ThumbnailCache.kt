package eu.siacs.conversations.ui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Shared thumbnail store, keyed by message uuid. Without this, the "uploading" and "sent"
 * bubbles each decoded their own copy independently — the instant an upload finished, a fresh
 * composable mounted starting from a blank state and had to redecode the same bitmap it had a
 * frame earlier, causing a visible flash back to a placeholder before the image reappeared.
 */
object ThumbnailCache {
    val bitmaps = mutableStateMapOf<String, ImageBitmap>()

    fun get(uuid: String?): ImageBitmap? = uuid?.let { bitmaps[it] }

    fun put(uuid: String?, bitmap: ImageBitmap) {
        if (uuid != null) bitmaps[uuid] = bitmap
    }
}
