package eu.siacs.conversations.ui

import eu.siacs.conversations.ui.glass.FluidBridges
import eu.siacs.conversations.ui.glass.FluidShape
import org.junit.Assert.*
import org.junit.Test

class ProfileHeaderMorphTest {
    @Test fun blurStartsBeforeBridgeReachAndGrowsContinuouslyTowardCamera() {
        assertEquals(0f, cameraBlurAmount(0.5f, 64f, 64f), 0f)
        assertTrue(cameraBlurAmount(0.5f, 48f, 64f) > 0f)
        assertTrue(cameraBlurAmount(0.5f, 32f, 64f) > cameraBlurAmount(0.5f, 48f, 64f))
        assertEquals(1f, cameraBlurAmount(0.5f, 12f, 64f), 0f)
        assertEquals(1f, cameraBlurAmount(1f, -10f, 64f), 0f)
        assertEquals(0f, cameraBlurAmount(0f, 12f, 64f), 0f)
    }
    @Test fun titleCopiesNeverAppearTogetherDuringForwardOrReverseScroll() {
        for (step in 0..100) {
            val (body, toolbar) = profileTitleAlphas(step / 100f)
            assertTrue(body == 0f || toolbar == 0f)
        }
        assertEquals(1f to 0f, profileTitleAlphas(0f))
        assertEquals(0f to 1f, profileTitleAlphas(1f))
    }
    @Test fun dissolvedEdgeChangesTheSilhouetteButKeepsItsInteriorOpaque() {
        assertEquals(1f, cameraEdgeAlpha(-10f, 20f), 0f)
        assertEquals(0f, cameraEdgeAlpha(20f, 20f), 0f)
        assertTrue(cameraEdgeAlpha(-2f, 20f) < 1f)
        assertTrue(cameraEdgeAlpha(2f, 20f) > 0f)
        assertEquals(1f, cameraEdgeAlpha(-2f, 0f), 0f)
        assertEquals(0f, cameraEdgeAlpha(2f, 0f), 0f)
        // A broad soft edge must not make the narrow neck's interior translucent grey.
        assertEquals(1f, cameraEdgeAlpha(-3f, 20f, 8f), 0f)
    }
    @Test fun cameraHaloDisappearsAtBothRestingStatesAndReversesSymmetrically() {
        assertEquals(0f, cameraHaloProgress(0f), 0f)
        assertEquals(0f, cameraHaloProgress(1f), 0f)
        assertEquals(1f, cameraHaloProgress(0.5f), 0f)
        assertEquals(cameraHaloProgress(0.25f), cameraHaloProgress(0.75f), 0f)
        assertEquals(0f, cameraHaloProgress(1.1f), 0f)
    }
    @Test fun photoRemainsClearAwayFromCamera() {
        val look = cameraAbsorption(0f, 100f, 32f)
        assertEquals(0f, look.blur, 0f)
        assertEquals(0f, look.topBlack, 0f)
        assertEquals(0f, look.bottomBlack, 0f)
    }

    @Test fun approachingPhotoBlursAndDarkensFromCameraSide() {
        val look = cameraAbsorption(0.5f, 8f, 32f)
        assertTrue(look.blur > 0.5f)
        assertTrue(look.topBlack > look.bottomBlack)
    }

    @Test fun photoIsAlreadyCompletelyBlackBeforeEnteringCamera() {
        val look = cameraAbsorption(0.995f, -10f, 32f)
        assertEquals(1f, look.topBlack, 0f)
        assertEquals(1f, look.bottomBlack, 0f)
        assertEquals(1f, look.blur, 0f)
    }
    @Test fun photoCenterStaysUndarkenedUntilFinalAbsorption() {
        for (p in listOf(0.4f, 0.6f, 0.8f, 0.9f)) {
            assertEquals(0f, cameraAbsorption(p, 0f, 32f).bottomBlack, 0f)
        }
    }
    private val metrics = HeaderMetrics(1080f, 1080f, 100f, 400f, 360f, 180f,
        540f, 30f, 18f, true, 60f)

    @Test fun collapsedPictureEndsInsideCameraInsteadOfBelowIt() {
        val pose = cameraMergePose(metrics, 1f, 540f, 360f, 360f, 360f)
        assertEquals(metrics.camX, pose.x, 0.001f)
        assertEquals(metrics.camY, pose.y, 0.001f)
        assertEquals(metrics.camR * 2f, pose.width, 0.001f)
        assertEquals(metrics.camR * 2f, pose.height, 0.001f)
    }

    @Test fun reverseScrollAndOvershootCannotProduceNegativeOrExtraGeometry() {
        val start = cameraMergePose(metrics, -0.2f, 540f, 360f, 360f, 360f)
        assertEquals(360f, start.width, 0.001f)
        assertEquals(360f, start.y, 0.001f)
        val end = cameraMergePose(metrics, 1.2f, 540f, 360f, 360f, 360f)
        assertEquals(metrics.camY, end.y, 0.001f)
        assertEquals(36f, end.height, 0.001f)
    }

    @Test fun playgroundBridgeHasNoStrandAcrossDistantShapes() {
        val bridge = FluidBridges().apply { reach = 32f; softness = 12f; response = 0.4f }
        val camera = FluidShape(0).apply { hw = 18f; hh = 18f; radius = 18f }
        val avatar = FluidShape(1).apply { cy = 300f; hw = 60f; hh = 60f; radius = 60f }
        repeat(120) { bridge.step(1f / 60f, listOf(camera, avatar), false) }
        assertEquals(0f, bridge.blend(camera, avatar), 0f)
        avatar.cy = 90f // A twelve-pixel gap, inside the same reach as the playground.
        repeat(120) { bridge.step(1f / 60f, listOf(camera, avatar), false) }
        assertTrue(bridge.blend(camera, avatar) > 0f)
    }
}
