package eu.siacs.conversations.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.util.LruCache
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions

object AvatarSegmenter {

    private val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
    )

    private val cache = LruCache<String, Bitmap>(32)
    private val inProgress = mutableSetOf<String>()

    fun segment(source: Bitmap, key: String, onResult: (Bitmap?) -> Unit) {
        val cached = cache[key]
        if (cached != null) {
            onResult(cached)
            return
        }
        if (key in inProgress) return
        inProgress += key
        segmenter.process(InputImage.fromBitmap(source, 0))
            .addOnSuccessListener { mask: SegmentationMask ->
                inProgress -= key
                val w = mask.width
                val h = mask.height
                val scaled = Bitmap.createScaledBitmap(source, w, h, true)
                val pixels = IntArray(w * h)
                scaled.getPixels(pixels, 0, w, 0, 0, w, h)
                val buffer = mask.buffer.also { it.rewind() }
                for (i in pixels.indices) {
                    if (buffer.float < 0.5f) pixels[i] = Color.TRANSPARENT
                }
                if (scaled !== source) scaled.recycle()
                val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                result.setPixels(pixels, 0, w, 0, 0, w, h)
                cache.put(key, result)
                onResult(result)
            }
            .addOnFailureListener {
                inProgress -= key
                onResult(null)
            }
    }

    fun invalidate(key: String) {
        cache.remove(key)
        inProgress -= key
    }
}
