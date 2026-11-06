package eu.siacs.conversations.ui.glass

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext
import org.junit.Assert.*
import org.junit.Test

class GlassDragTest {
    @Test fun draggingElsewhereOnBarPicksUpLensAtThatPosition() {
        assertEquals(2f, tabDragOrigin(254f, 408f, 4f, 100f, 4, true, 0f), 0f)
        assertEquals(3f, tabDragOrigin(408f, 408f, 4f, 100f, 4, true, 0f), 0f)
        assertEquals(2f, tabDragOrigin(154f, 408f, 4f, 100f, 4, false, 0f), 0f)
    }

    @Test fun grabbingInsideLensPreservesOffsetWithoutJumping() {
        assertEquals(1f, tabDragOrigin(125f, 408f, 4f, 100f, 4, true, 1f), 0f)
        assertEquals(1f, tabDragOrigin(283f, 408f, 4f, 100f, 4, false, 1f), 0f)
    }

    // Leave render/animation jobs queued: finger events must never depend on their execution.
    private class QueuedDispatcher : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
    }

    private fun controller(scope: CoroutineScope, end: Float = 1f) = DampedDragAnimation(
        animationScope = scope, initialValue = 0f, valueRange = 0f..end,
        visibilityThreshold = 0.001f, initialScale = 1f, pressedScale = 1.5f,
        followFinger = true, onDragStarted = {}, onDragStopped = {}, onDrag = { _, _ -> },
    )

    @Test fun oneContinuousSliderDragReachesEndBeforeAnimationJobsRun() {
        val scope = CoroutineScope(SupervisorJob() + QueuedDispatcher())
        val drag = controller(scope)
        drag.beginDrag(Offset.Zero)
        repeat(100) { drag.updateValue(drag.targetValue + 0.01f) }
        assertEquals(1f, drag.targetValue, 0.00001f)
        assertEquals(1f, drag.value, 0.00001f)
        scope.cancel()
    }

    @Test fun oneContinuousTabDragTraversesAllTabsAndReversesWithoutLosingTravel() {
        val scope = CoroutineScope(SupervisorJob() + QueuedDispatcher())
        val drag = controller(scope, 3f)
        drag.beginDrag(Offset.Zero)
        repeat(30) { drag.updateValue(drag.targetValue + 0.1f) }
        assertEquals(3f, drag.value, 0.00001f)
        repeat(30) { drag.updateValue(drag.targetValue - 0.1f) }
        assertEquals(0f, drag.value, 0.00001f)
        scope.cancel()
    }

    @Test fun travelClampsAtEdgesAndSettlingPublishesItsTargetImmediately() {
        val scope = CoroutineScope(SupervisorJob() + QueuedDispatcher())
        val drag = controller(scope)
        drag.beginDrag(Offset.Zero)
        drag.updateValue(4f)
        assertEquals(1f, drag.value, 0f)
        drag.updateValue(-4f)
        assertEquals(0f, drag.value, 0f)
        drag.animateToValue(0.5f)
        assertEquals(0.5f, drag.targetValue, 0f)
        scope.cancel()
    }
}
