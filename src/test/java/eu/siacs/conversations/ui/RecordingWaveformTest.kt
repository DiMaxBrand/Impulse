package eu.siacs.conversations.ui

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class RecordingWaveformTest {
    @Test fun medianWindowsSuppressIsolatedPeaksWithoutChangingInputAudio() {
        val waveform = RecordingWaveform()
        repeat(5) { waveform.add(if (it == 2) 32767 else 0) }
        assertEquals(0f, waveform.bars(1).single(), 0f)
    }

    @Test fun newSamplesArriveAtRightAndLongHistoryFitsSameBarCount() {
        val waveform = RecordingWaveform()
        waveform.add(32767)
        assertEquals(listOf(0f, 0f, 0f, 1f), waveform.bars(4))
        repeat(20000) { waveform.add(12000) }
        val bars = waveform.bars(48)
        assertEquals(48, bars.size)
        assertTrue(bars.all { it in 0f..1f })
        assertTrue(bars.first() > 0f)
    }

    @Test fun oneSegmentExportPreservesEncodedBytesAndInputUntilCallerCleansUp() {
        val dir = Files.createTempDirectory("voice-draft-test").toFile()
        try {
            val source = dir.resolve("segment.oga").apply { writeBytes(byteArrayOf(0, 1, 42, -1)) }
            val output = dir.resolve("RECORDING_export.oga")
            assertEquals(output, VoiceDraftAudio.join(listOf(source), output, true))
            assertArrayEquals(source.readBytes(), output.readBytes())
            assertTrue(source.exists())
        } finally { dir.deleteRecursively() }
    }
}
