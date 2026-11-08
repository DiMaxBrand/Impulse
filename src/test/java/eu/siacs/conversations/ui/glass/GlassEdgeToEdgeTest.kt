package eu.siacs.conversations.ui.glass

import android.app.Activity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import eu.siacs.conversations.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = android.app.Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class GlassEdgeToEdgeTest {
    private fun insets(height: Int, visible: Boolean) = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 48))
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, height))
        .setVisible(WindowInsetsCompat.Type.ime(), visible).build()

    private fun animation() = WindowInsetsAnimationCompat(
        WindowInsetsCompat.Type.ime(), LinearInterpolator(), 250L)

    @Test fun resumeClearsInterruptedAnimationAndStaleHiddenKeyboardSpace() {
        val frame = View(RuntimeEnvironment.getApplication())
        val glide = GlassEdgeToEdge.Glide(frame)
        glide.onInsets(insets(700, true))
        assertEquals(700, frame.paddingBottom)
        glide.onPrepare(animation()) // backgrounding interrupts this animation; no onEnd
        glide.onInsets(insets(0, false))
        assertEquals(700, frame.paddingBottom)
        glide.refresh(insets(700, false)) // even a stale nonzero height must not reserve hidden IME
        assertFalse(glide.animating)
        assertEquals(0, frame.paddingBottom)
        glide.onInsets(insets(500, true))
        assertEquals(500, frame.paddingBottom) // future ordinary updates are no longer suppressed
    }

    @Test fun keyboardHideStillInterpolatesAndEndsAtZero() {
        val frame = View(RuntimeEnvironment.getApplication())
        val glide = GlassEdgeToEdge.Glide(frame)
        glide.onInsets(insets(700, true))
        val animation = animation()
        glide.onPrepare(animation)
        glide.onInsets(insets(0, false))
        glide.onProgress(insets(350, false), mutableListOf(animation))
        assertEquals(350, frame.paddingBottom)
        glide.onEnd(animation)
        assertEquals(0, frame.paddingBottom)
        assertFalse(glide.animating)
    }

    @Test fun repeatedApplyRefreshesExistingHandlerInsteadOfReturningEarly() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val frame = FrameLayout(activity).apply { id = R.id.main_fragment }
        activity.setContentView(frame)
        GlassEdgeToEdge.apply(activity, true)
        val glide = frame.getTag(R.id.main_fragment) as GlassEdgeToEdge.Glide
        glide.onInsets(insets(700, true))
        glide.onPrepare(animation())
        GlassEdgeToEdge.apply(activity, true)
        assertSame(glide, frame.getTag(R.id.main_fragment))
        assertFalse(glide.animating)
        assertEquals(0, frame.paddingBottom)
        GlassEdgeToEdge.apply(activity, false)
        assertNull(frame.getTag(R.id.main_fragment))
        assertTrue(frame.fitsSystemWindows)
        controller.pause().stop().destroy()
    }

    @Test fun materialAndTabletFramesAreLeftAlone() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val frame = FrameLayout(activity).apply {
            id = R.id.main_fragment
            setPadding(1, 2, 3, 4)
        }
        root.addView(frame)
        activity.setContentView(root)
        GlassEdgeToEdge.apply(activity, false)
        assertEquals(4, frame.paddingBottom)
        root.addView(View(activity).apply { id = R.id.secondary_fragment })
        GlassEdgeToEdge.apply(activity, true)
        assertNull(frame.getTag(R.id.main_fragment))
        assertEquals(4, frame.paddingBottom)
        controller.pause().stop().destroy()
    }
}
