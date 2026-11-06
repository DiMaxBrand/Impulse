package eu.siacs.conversations.ui.glass

import org.junit.Assert.*
import org.junit.Test

class GlassSamplingTest {
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
