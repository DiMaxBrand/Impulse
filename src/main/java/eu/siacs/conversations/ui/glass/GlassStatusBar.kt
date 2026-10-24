package eu.siacs.conversations.ui.glass

import android.app.Activity
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import eu.siacs.conversations.R

/*
 * The status bar in the liquid glass experiment: the screen's content runs up under it (the host no
 * longer keeps it out, see [GlassEdgeToEdge]) and is blurred there, progressively -- strongest at
 * the very top, fading to nothing at the bottom edge of the bar -- with a whiter wash so the
 * clock and icons stay readable. Nothing is drawn or blurred outside the bar's own area.
 */

/** Two blurs of different strength, each fading out over the bar, so the blur grows towards the top. */
@Composable
fun GlassStatusBarBlur(backdrop: Backdrop, modifier: Modifier = Modifier) {
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    if (inset <= 0.dp || !GlassTuning.barBlur) return
    Box(modifier.fillMaxWidth().height(inset)) {
        BlurBand(backdrop, inset, radius = 22.dp, washAlpha = 0.55f, fadeStart = 0.35f, fromBottom = false)
        BlurBand(backdrop, inset, radius = 7.dp, washAlpha = 0.25f, fadeStart = 0.0f, fromBottom = false)
    }
}

/** The same at the bottom, over the gesture bar: strongest at the very bottom edge. */
@Composable
fun GlassNavigationBarBlur(backdrop: Backdrop, modifier: Modifier = Modifier) {
    val inset = rememberGlassNavInset()
    if (inset <= 0.dp || !GlassTuning.barBlur) return
    Box(modifier.fillMaxWidth().height(inset)) {
        BlurBand(backdrop, inset, radius = 22.dp, washAlpha = 0.55f, fadeStart = 0.35f, fromBottom = true)
        BlurBand(backdrop, inset, radius = 7.dp, washAlpha = 0.25f, fadeStart = 0.0f, fromBottom = true)
    }
}

/**
 * The space the screens leave for the gesture bar now that the host runs under it. None while the
 * keyboard is up: then the host frame itself keeps clear of the keyboard, gesture bar included.
 */
@Composable
fun rememberGlassNavInset(): androidx.compose.ui.unit.Dp {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val nav = WindowInsets.navigationBars.getBottom(density)
    val ime = WindowInsets.ime.getBottom(density)
    return if (ime > nav) 0.dp else with(density) { nav.toDp() }
}

@Composable
private fun BlurBand(
    backdrop: Backdrop,
    height: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp,
    washAlpha: Float,
    fadeStart: Float,
    fromBottom: Boolean,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // White, not a brighter picture: the wash is what makes it whiter. On dark themes it is the
    // theme's own surface, since white icons on white would vanish.
    val wash = if (dark) MaterialTheme.colorScheme.surface else Color.White
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Opaque down to [fadeStart] of the bar's height, then fading out to nothing.
                val stops = if (fromBottom) {
                    arrayOf(
                        0f to Color.Transparent,
                        (1f - fadeStart.coerceIn(0f, 0.95f)) to Color.Black,
                        1f to Color.Black,
                    )
                } else {
                    arrayOf(
                        0f to Color.Black,
                        fadeStart.coerceIn(0f, 0.95f) to Color.Black,
                        1f to Color.Transparent,
                    )
                }
                drawRect(
                    Brush.verticalGradient(colorStops = stops),
                    blendMode = BlendMode.DstIn,
                )
            }
            .glassSnapshot()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                highlight = null,
                shadow = null,
                innerShadow = null,
                effects = { blur(radius.toPx()) },
                onDrawSurface = { drawRect(wash.copy(alpha = washAlpha)) },
            ),
    )
}

/**
 * Lets the chat list and the chat run up under the status bar and the gesture bar while liquid glass
 * is on: the host frame keeps clear of the keyboard as before, but no longer of the system bars.
 * (The screens then place their bars clear of them themselves, from the same insets.) Leaves the
 * frame untouched when glass is off, and on the tablet layout.
 */
object GlassEdgeToEdge {

    private fun pad(view: View, insets: WindowInsetsCompat) {
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        // Under the gesture bar too, unless the keyboard is up: then clear of the keyboard, as
        // before. The screens leave room for the gesture bar themselves.
        view.setPadding(bars.left, 0, bars.right, if (ime > bars.bottom) ime else 0)
    }

    /**
     * The frame follows the keyboard frame by frame while it slides, so the screen glides up with
     * it. (Without this the system hands over the keyboard's final height at once and the screen
     * jumps there while the keyboard is still on its way.)
     */
    private class Glide(private val frame: View) :
        WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
        var animating = false

        override fun onPrepare(animation: WindowInsetsAnimationCompat) {
            if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) animating = true
        }

        override fun onProgress(
            insets: WindowInsetsCompat,
            runningAnimations: MutableList<WindowInsetsAnimationCompat>,
        ): WindowInsetsCompat {
            if (animating) pad(frame, insets)
            return insets
        }

        override fun onEnd(animation: WindowInsetsAnimationCompat) {
            if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                animating = false
                ViewCompat.getRootWindowInsets(frame)?.let { pad(frame, it) }
            }
        }
    }

    fun apply(activity: Activity, on: Boolean) {
        val frame = activity.findViewById<View>(R.id.main_fragment) ?: return
        val tablet = activity.findViewById<View>(R.id.secondary_fragment) != null
        val wasOn = frame.getTag(R.id.main_fragment) == true
        if (on && !tablet) {
            if (wasOn) return
            frame.fitsSystemWindows = false
            val glide = Glide(frame)
            ViewCompat.setWindowInsetsAnimationCallback(frame, glide)
            ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
                // While the keyboard slides, the animation callback sets the padding.
                if (!glide.animating) pad(view, insets)
                // Not consumed: the screens read the status bar inset themselves.
                insets
            }
            frame.setTag(R.id.main_fragment, true)
            ViewCompat.requestApplyInsets(frame)
        } else if (wasOn) {
            ViewCompat.setOnApplyWindowInsetsListener(frame, null)
            ViewCompat.setWindowInsetsAnimationCallback(frame, null)
            frame.setPadding(0, 0, 0, 0)
            frame.fitsSystemWindows = true
            frame.setTag(R.id.main_fragment, null)
            ViewCompat.requestApplyInsets(frame)
        }
    }
}
