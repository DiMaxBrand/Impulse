package eu.siacs.conversations.ui

import org.junit.Assert.*
import org.junit.Test

class VoiceRecordingPolicyTest {
    @Test fun tapsUnderOneFullSecondAreDiscarded() {
        listOf(-1L, 0L, 80L, 500L, 999L).forEach {
            assertFalse("$it ms must not be sent", VoiceRecordingPolicy.canSend(it))
        }
        assertTrue(VoiceRecordingPolicy.canSend(1000L))
        assertTrue(VoiceRecordingPolicy.canSend(1001L))
    }

    @Test fun remainingPausedCannotTurnShortClipIntoSendableRecording() {
        val duration = VoiceRecordingPolicy.activeDuration(1000, 20000, 0, 1500)
        assertEquals(500L, duration)
        assertFalse(VoiceRecordingPolicy.canSend(duration))
    }

    @Test fun earlierPausesAreExcludedAfterRecordingResumes() {
        assertEquals(999L, VoiceRecordingPolicy.activeDuration(1000, 5999, 4000, 0))
        assertFalse(VoiceRecordingPolicy.canSend(VoiceRecordingPolicy.activeDuration(1000, 5999, 4000, 0)))
        assertTrue(VoiceRecordingPolicy.canSend(VoiceRecordingPolicy.activeDuration(1000, 6000, 4000, 0)))
    }
}
