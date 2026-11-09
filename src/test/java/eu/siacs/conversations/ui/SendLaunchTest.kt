package eu.siacs.conversations.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class SendLaunchTest {
    private val composer = Rect(30f, 700f, 330f, 760f)
    private val bubble = Rect(100f, 530f, 350f, 650f)

    @Test fun textAndSmallVoiceOriginsArriveAtExactDestination() {
        for (source in listOf(composer, Rect(40f, 710f, 83f, 729f))) {
            assertRect(source, SendLaunchMotion.rect(source, bubble, 0f, 3f))
            assertRect(bubble, SendLaunchMotion.rect(source, bubble, 1f, 3f))
            for (frame in 0..120) {
                val rect = SendLaunchMotion.rect(source, bubble, frame / 120f, 3f)
                assertTrue(rect.width.isFinite() && rect.width > 0f)
                assertTrue(rect.height.isFinite() && rect.height > 0f)
            }
        }
        assertTrue(SendLaunchMotion.DURATION_MS in 800..999)
    }

    @Test fun wobbleChangesDirectionButSettlesWithoutResidualOffset() {
        assertEquals(0f, SendLaunchMotion.wobble(0f, 60f), 0.001f)
        assertEquals(0f, SendLaunchMotion.wobble(1f, 60f), 0.001f)
        assertTrue(SendLaunchMotion.wobble(0.125f, 60f) > 0f)
        assertTrue(SendLaunchMotion.wobble(0.375f, 60f) < 0f)
        assertEquals(0f, SendLaunchMotion.material(0f), 0f)
        assertEquals(1f, SendLaunchMotion.material(1f), 0f)
        assertTrue(SendLaunchMotion.material(0.7f) in 0.1f..0.9f)
    }

    @Test fun spaceIsSharedOnlyWhenThereAreMessagesOnBothSides() {
        assertEquals(-60f to 60f, SendLaunchMotion.displacement(120f, true, true))
        assertEquals(-120f to 0f, SendLaunchMotion.displacement(120f, true, false))
        assertEquals(0f to 120f, SendLaunchMotion.displacement(120f, false, true))
        assertEquals(0f to 0f, SendLaunchMotion.displacement(120f, false, false))
    }

    @Test fun rapidSendPreservesCurrentVisualPositionsAndOldFlightCannotCancelNewOne() {
        val state = SendLaunchState()
        state.rows["old"] = Rect(0f, 500f, 200f, 560f)
        state.enqueue("first", "chat", composer, "one", false)
        val first = state.flights.single()
        state.rows["old"] = Rect(0f, 440f, 200f, 500f)
        assertEquals(60f, state.offset("old"), 0f)
        state.enqueue("second", "chat", composer, "two", false)
        state.rows["old"] = Rect(0f, 380f, 200f, 440f)
        assertEquals(120f, state.offset("old"), 0f)
        state.finish(first)
        assertTrue(state.contains("second"))
        assertEquals(120f, state.offset("old"), 0f)
        state.clear()
        assertTrue(state.flights.isEmpty())
        assertTrue(state.rows.isEmpty())
        assertEquals(0f, state.offset("old"), 0f)
    }

    @Test fun missingIdentityOrGeometryAndDuplicateSendsDoNotLaunch() {
        val state = SendLaunchState()
        state.enqueue(null, "chat", composer, "one", false)
        state.enqueue("one", null, composer, "one", false)
        state.enqueue("one", "chat", Rect.Zero, "one", false)
        assertTrue(state.flights.isEmpty())
        state.enqueue("one", "chat", composer, "one", false)
        state.enqueue("one", "chat", composer, "one", false)
        assertEquals(1, state.flights.size)
    }

    @Test fun composerClearsOnceWhenTheFlightStartsAndEachQueuedSendGetsANewCaptureEpoch() {
        val state = SendLaunchState()
        var clears = 0
        assertTrue(state.enqueue("one", "chat", composer, "one", false) { clears++ })
        val first = state.flights.single()
        assertEquals(1, state.composerCaptureEpoch)
        state.start(first)
        state.start(first)
        assertEquals(1, clears)
        assertTrue(state.enqueue("two", "chat", composer, "two", false))
        assertEquals(2, state.composerCaptureEpoch)
    }

    private fun assertRect(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 0.001f)
        assertEquals(expected.top, actual.top, 0.001f)
        assertEquals(expected.right, actual.right, 0.001f)
        assertEquals(expected.bottom, actual.bottom, 0.001f)
    }
}
