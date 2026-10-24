package eu.siacs.conversations.ui.glass

import android.app.Activity
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
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
    if (inset <= 0.dp) return
    Box(modifier.fillMaxWidth().height(inset)) {
        BlurBand(backdrop, radius = 22.dp, washAlpha = 0.55f, fadeStart = 0.35f)
        BlurBand(backdrop, radius = 7.dp, washAlpha = 0.25f, fadeStart = 0.0f)
    }
}

@Composable
private fun BlurBand(backdrop: Backdrop, radius: androidx.compose.ui.unit.Dp, washAlpha: Float, fadeStart: Float) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // White, not a brighter picture: the wash is what makes it whiter. On dark themes it is the
    // theme's own surface, since white icons on white would vanish.
    val wash = if (dark) MaterialTheme.colorScheme.surface else Color.White
    Box(
        Modifier
            .fillMaxWidth()
            .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Opaque down to [fadeStart] of the bar's height, then fading out to nothing.
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Black,
                        fadeStart.coerceIn(0f, 0.95f) to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = { blur(radius.toPx()) },
                onDrawSurface = { drawRect(wash.copy(alpha = washAlpha)) },
            ),
    )
}

/**
 * Lets the chat list and the chat run up under the status bar while liquid glass is on: the host
 * frame keeps clear of the gesture bar and the keyboard as before, but no longer of the status bar.
 * (The screens then place their top bars below the bar themselves, from the same inset.) Leaves the
 * frame untouched when glass is off, and on the tablet layout.
 */
object GlassEdgeToEdge {
    private val APPLIED = R.id.main_fragment

    fun apply(activity: Activity, on: Boolean) {
        val frame = activity.findViewById<View>(R.id.main_fragment) ?: return
        val tablet = activity.findViewById<View>(R.id.secondary_fragment) != null
        val wasOn = frame.getTag(R.id.main_fragment) == true
        if (on && !tablet) {
            if (wasOn) return
            frame.fitsSystemWindows = false
            ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime(),
                )
                view.setPadding(bars.left, 0, bars.right, bars.bottom)
                // Not consumed: the screens read the status bar inset themselves.
                insets
            }
            frame.setTag(R.id.main_fragment, true)
            ViewCompat.requestApplyInsets(frame)
        } else if (wasOn) {
            ViewCompat.setOnApplyWindowInsetsListener(frame, null)
            frame.setPadding(0, 0, 0, 0)
            frame.fitsSystemWindows = true
            frame.setTag(R.id.main_fragment, null)
            ViewCompat.requestApplyInsets(frame)
        }
    }
}
