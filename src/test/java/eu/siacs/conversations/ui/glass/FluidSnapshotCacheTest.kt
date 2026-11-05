package eu.siacs.conversations.ui.glass

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class FluidSnapshotCacheTest {
    @Test fun cornerAndFrostChangesReachTheRendererWithoutMovingTheElement() {
        val host = FluidGlassHost(1f)
        val corner = mutableFloatStateOf(14f)
        val frost = mutableFloatStateOf(1f)
        host.newElement().apply {
            rect = Rect(10f, 10f, 130f, 62f)
            cornerProvider = { corner.floatValue }
            frostProvider = { frost.floatValue }
        }
        val cache = FluidSnapshotCache(host)
        assertEquals(14f, cache.get().infos[0], 0f)
        assertEquals(1f, cache.get().looks[0], 0f)
        corner.floatValue = 26f
        frost.floatValue = 0.4f
        assertEquals(26f, cache.get().infos[0], 0f)
        assertEquals(0.4f, cache.get().looks[0], 0f)
        frost.floatValue = 0f
        assertEquals(0f, cache.get().looks[0], 0f)
    }
    private class Scene : FluidScene {
        val visible = mutableStateOf(false)
        val x = mutableStateOf(10f)
        override val tick = 0 // Physics has settled: there are no more ticks.
        override val frostActive = false
        override fun shapes(): List<FluidShape> = if (!visible.value) emptyList() else listOf(
            FluidShape(0).also { it.cx = x.value; it.cy = 20f; it.hw = 30f; it.hh = 30f; it.radius = 15f },
        )
        override fun bridgeBlend(a: FluidShape, b: FluidShape) = 0f
    }

    @Test fun glassReappearsAfterMorphWithoutDragging() {
        val scene = Scene()
        val cache = FluidSnapshotCache(scene)
        assertEquals(0, cache.get().n)
        scene.visible.value = true
        assertEquals(1, cache.get().n)
        scene.visible.value = false
        assertEquals(0, cache.get().n)
        scene.visible.value = true
        assertEquals(1, cache.get().n)
    }

    @Test fun finalLayoutPositionInvalidatesSettledFrame() {
        val scene = Scene().also { it.visible.value = true }
        val cache = FluidSnapshotCache(scene)
        assertEquals(10f, cache.get().boxes[0], 0f)
        scene.x.value = 321f
        val finalFrame = cache.get()
        assertEquals(321f, finalFrame.boxes[0], 0f)
        assertSame(finalFrame, cache.get()) // Passes in the unchanged frame share their snapshot.
    }
}
