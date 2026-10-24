package eu.siacs.conversations.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.LruCache
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import eu.siacs.conversations.entities.Contact
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Cuts the person out of an avatar photo (for the 3D pop-out over the shape outline).
 *
 * The cut-out is cached on disk under a key made from the avatar's own identity (its hash), so it
 * survives restarts and is made again exactly when the avatar changes: a new avatar is a new key.
 * Nothing needs to be invalidated, and nothing expires on a timer. A photo with no person in it is
 * remembered too, so it is not tried again every launch.
 */
object AvatarSegmenter {

    /** The person cut out of the photo, and where they are in it (all as fractions 0..1). */
    class Cutout(val bitmap: Bitmap, val top: Float, val bottom: Float, val centerX: Float)

    private const val DIR = "avatar_cutouts"
    private const val MAX_FILES = 80
    private const val NONE = "none"

    private val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
    )

    private val memory = LruCache<String, Cutout>(32)
    private val noPerson = HashSet<String>()

    /** One photo at a time goes through the model. */
    private val lock = Mutex()

    /** What identifies this contact's photo: changes when (and only when) the picture does. */
    fun key(contact: Contact?): String? {
        if (contact == null) return null
        contact.getAvatar()?.let { return "h-$it" }
        contact.getProfilePhoto()?.let { return "p-${it.hashCode()}" }
        return null
    }

    private fun dir(context: Context) = File(context.applicationContext.cacheDir, DIR).apply { mkdirs() }

    private fun pngFile(context: Context, key: String) = File(dir(context), "$key.png")
    private fun metaFile(context: Context, key: String) = File(dir(context), "$key.b")

    /** True when the answer for [key] is already known (a cut-out, or "no person"): no waiting. */
    fun isCached(context: Context, key: String): Boolean =
        memory[key] != null || key in noPerson || metaFile(context, key).exists()

    /**
     * The cut-out for the photo [source] identified by [key], from memory, then disk, and only
     * then by running the model. Null when there is no person in it (or the model failed).
     */
    suspend fun cutout(context: Context, key: String, source: Bitmap): Cutout? {
        memory[key]?.let { return it }
        if (key in noPerson) return null
        val stored = withContext(Dispatchers.IO) { readDisk(context, key) }
        if (stored != null) {
            if (stored === NO_CUTOUT) {
                noPerson += key
                return null
            }
            memory.put(key, stored)
            return stored
        }
        return lock.withLock {
            memory[key]?.let { return@withLock it }
            if (key in noPerson) return@withLock null
            val made = withContext(Dispatchers.Default) { runModel(source) }
            when {
                made.failed -> null
                made.cutout == null -> {
                    noPerson += key
                    withContext(Dispatchers.IO) { metaFile(context, key).writeText(NONE) }
                    null
                }
                else -> {
                    memory.put(key, made.cutout)
                    withContext(Dispatchers.IO) { writeDisk(context, key, made.cutout) }
                    made.cutout
                }
            }
        }
    }

    private val NO_CUTOUT = Cutout(Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8), -1f, -1f, -1f)

    private fun readDisk(context: Context, key: String): Cutout? {
        val meta = metaFile(context, key)
        if (!meta.exists()) return null
        val text = try { meta.readText() } catch (_: Exception) { return null }
        if (text == NONE) return NO_CUTOUT
        val png = pngFile(context, key)
        val parts = text.split(',')
        if (parts.size != 3 || !png.exists()) return null
        val bitmap = BitmapFactory.decodeFile(png.path) ?: return null
        png.setLastModified(System.currentTimeMillis())
        return Cutout(bitmap, parts[0].toFloat(), parts[1].toFloat(), parts[2].toFloat())
    }

    private fun writeDisk(context: Context, key: String, cutout: Cutout) {
        try {
            pngFile(context, key).outputStream().use { cutout.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            metaFile(context, key).writeText("${cutout.top},${cutout.bottom},${cutout.centerX}")
            prune(context)
        } catch (_: Exception) {
            // The cache is only a shortcut.
        }
    }

    /** Keeps the folder small: the cut-outs not used for longest go first. */
    private fun prune(context: Context) {
        val pngs = dir(context).listFiles { f -> f.extension == "png" } ?: return
        if (pngs.size <= MAX_FILES) return
        pngs.sortedBy { it.lastModified() }.take(pngs.size - MAX_FILES).forEach {
            it.delete()
            File(it.parentFile, it.nameWithoutExtension + ".b").delete()
        }
    }

    private class Made(val cutout: Cutout?, val failed: Boolean)

    private suspend fun runModel(source: Bitmap): Made {
        val mask = suspendCancellableCoroutine<SegmentationMask?> { cont ->
            segmenter.process(InputImage.fromBitmap(source, 0))
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        } ?: return Made(null, failed = true)
        val w = mask.width
        val h = mask.height
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== source) scaled.recycle()
        val buffer = mask.buffer.also { it.rewind() }
        var top = -1
        var bottom = -1
        var left = -1
        var right = -1
        for (i in pixels.indices) {
            if (buffer.float < 0.5f) {
                pixels[i] = Color.TRANSPARENT
            } else {
                val row = i / w
                val col = i % w
                if (top == -1) top = row
                bottom = row
                if (left == -1 || col < left) left = col
                if (right == -1 || col > right) right = col
            }
        }
        if (top == -1) return Made(null, failed = false)
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return Made(Cutout(result, top.toFloat() / h, bottom.toFloat() / h, (left + right) / 2f / w), failed = false)
    }

    fun clearAll() {
        memory.evictAll()
        noPerson.clear()
    }
}
