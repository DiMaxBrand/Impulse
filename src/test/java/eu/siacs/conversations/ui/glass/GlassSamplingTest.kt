package eu.siacs.conversations.ui.glass

import org.junit.Assert.*
import org.junit.Test

class GlassSamplingTest {
    @Test fun oneFpsRateIsARealMinimumIntervalBetweenCaptures() {
        val interval = sampledBackdropInterval(refreshRate = 120f, requestedRate = 1f)
        assertEquals(1000L, interval)

        val gate = SampledCaptureGate(interval)
        val captures = mutableListOf<Long>()
        var nextTimerTick = interval
        assertTrue(gate.consumeIfRequested(0L)) // Initial image.
        captures += 0L

        // Simulate 60 Hz Compose draws for five seconds and a 1 Hz cadence timer. Even when the
        // timer tick and a draw land on adjacent frames, actual captures stay at least 1000 ms apart.
        for (time in 16L..5000L step 16L) {
            if (time >= nextTimerTick) {
                gate.request()
                nextTimerTick += interval
            }
            if (gate.consumeIfRequested(time)) captures += time
        }
        assertTrue(captures.size > 1)
        assertTrue(captures.zipWithNext().all { (before, after) -> after - before >= interval })

        // If the first draw itself is late, the next timer tick must not cause a burst after it.
        val delayedGate = SampledCaptureGate(interval)
        assertTrue(delayedGate.consumeIfRequested(900L))
        delayedGate.request() // Timer tick at 1000 ms.
        assertFalse(delayedGate.consumeIfRequested(1000L))
        delayedGate.request() // Next timer tick at 2000 ms.
        assertTrue(delayedGate.consumeIfRequested(2000L))
    }

    @Test fun simultaneousInkRequestsAreSpacedInsteadOfBurstingOnOneFrame() {
        val budget = GlassReadbackBudget()
        var time = 0L
        repeat(9) { index ->
            time += budget.waitMillis(time)
            assertEquals(index * 32L, time)
            budget.started(time)
        }
        assertEquals(0L, budget.waitMillis(1000L))
    }

    @Test fun surfaceUsesClearOpticsWithATranslucentLayerAndNoFogPass() {
        val surface = LiquidGlassStyle.Surface
        assertNull(surface.frost)
        assertTrue(surface.surfaceAlpha > 0f && surface.surfaceAlpha < 1f)
        assertEquals(LiquidGlassStyle.Button.blur, surface.blur)
        assertNotNull(LiquidGlassStyle.Menu.frost)
    }
}
