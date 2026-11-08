package eu.siacs.conversations.ui

import kotlin.math.ln

/** Display-only envelope. Compaction increases each sample's time span, never edits the audio. */
class RecordingWaveform {
    private val samples = ArrayList<Float>()
    private var bucketSize = 1
    private val incoming = ArrayList<Float>()

    fun clear() { samples.clear(); incoming.clear(); bucketSize = 1 }

    fun add(amplitude: Int) {
        incoming += (ln(1f + amplitude.coerceIn(0, 32767)) / ln(32768f)).coerceIn(0f, 1f)
        if (incoming.size >= bucketSize) {
            samples += median(incoming)
            incoming.clear()
        }
        if (samples.size >= 2048) {
            val compacted = samples.chunked(2).map(::median)
            samples.clear(); samples.addAll(compacted)
            bucketSize *= 2
        }
    }

    fun bars(count: Int = 48): List<Float> {
        if (count <= 0) return emptyList()
        val all = if (incoming.isEmpty()) samples.toList() else samples + median(incoming)
        if (all.isEmpty()) return List(count) { 0f }
        // At first bars arrive from the right. Once full, the complete history is rebinned.
        if (all.size < count) return List(count - all.size) { 0f } + all
        return List(count) { i ->
            val start = i * all.size / count
            val end = ((i + 1) * all.size / count).coerceAtLeast(start + 1)
            median(all.subList(start, end))
        }
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        return if (sorted.size % 2 == 0) (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
            else sorted[sorted.size / 2]
    }
}
