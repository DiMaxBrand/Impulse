package eu.siacs.conversations.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.glass.FluidGlassHost
import eu.siacs.conversations.ui.glass.LiquidBottomTab
import eu.siacs.conversations.ui.glass.LiquidBottomTabs
import eu.siacs.conversations.ui.glass.LiquidGlassButton
import eu.siacs.conversations.ui.glass.LiquidGlassStyle
import eu.siacs.conversations.ui.glass.LiquidSlider
import eu.siacs.conversations.ui.glass.LiquidToggle
import eu.siacs.conversations.ui.glass.rememberGlassStack
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The liquid glass playground: a page to play with the glass on. The page (colour, drifting blobs and
 * big text) is at the TOP, with a glass lens to drag around on it; the controls (round buttons, a
 * toggle, a slider and a bottom tab bar) are BELOW it.
 *
 * Opened from the Glass lab it arrives slowly (about five and a half seconds, it is a toy): the
 * lab's button melts into three glass elements that spread out and flow into each other (they are
 * elements of one fluid host, so they merge as they come close), take the shapes of the playground's
 * own buttons, travel to their places while the background changes over to the playground's, and
 * only then their icons and text fade in and the controls come in one after the other.
 * Without [origin] (the developer-options entry) there is no arrival: everything is in place at once.
 */

private fun win(p: Float, a: Float, b: Float): Float = ((p - a) / (b - a)).coerceIn(0f, 1f)
private fun ease(t: Float): Float = t * t * (3f - 2f * t)
/** Goes past 1 and settles: the shapes change like a jelly. */
private fun back(t: Float): Float {
    val c1 = 1.5f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}
private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Places the node at [rect] (window pixels, relative to the root) with exactly its size, read while laying out. */
private fun Modifier.placedAt(rect: () -> Rect): Modifier = this
    .offset { val r = rect(); IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
    .layout { measurable, _ ->
        val r = rect()
        val w = r.width.roundToInt().coerceAtLeast(1)
        val h = r.height.roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(w, h) { placeable.place(0, 0) }
    }

/** Fades and rises into place once [shown] has passed [index]: the controls come in one after the other. */
@Composable
private fun Modifier.cascade(index: Int, shown: Int): Modifier {
    val a by animateFloatAsState(if (shown > index) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = 110f), label = "cascade")
    return this.graphicsLayer {
        alpha = a
        translationY = (1f - a) * 44.dp.toPx()
    }
}

@Composable
internal fun GlassPlayground(origin: Rect?, onBack: () -> Unit, labBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null) {
    val density = LocalDensity.current
    // The playground's own page, and (when it arrives from the lab) the lab page too: the glass looks through
    // both, so on the way it bends the lab's rows as they fade out.
    val pageBackdrop = rememberLayerBackdrop()
    val backdrop: com.kyant.backdrop.Backdrop =
        if (labBackdrop != null) com.kyant.backdrop.backdrops.rememberCombinedBackdrop(labBackdrop, pageBackdrop) else pageBackdrop
    val base = rememberGlassStack(backdrop)
    // The three elements melt into each other while they are close: a wide reach, a slow, soft join.
    val host = remember { FluidGlassHost(density.density, LiquidGlassStyle.Bar, reachDp = 22f, softnessDp = 14f, responseSeconds = 0.5f) }
    val fluid = remember(base, host) { base.withFluid(host) }

    val arriving = origin != null
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    var backTarget by remember { mutableStateOf(Rect.Zero) }
    var titleTarget by remember { mutableStateOf(Rect.Zero) }
    var lensTarget by remember { mutableStateOf(Rect.Zero) }
    var lensDrag by remember { mutableStateOf(Offset.Zero) }
    var taps by remember { mutableIntStateOf(0) }
    var toggled by remember { mutableStateOf(true) }
    var slider by remember { mutableFloatStateOf(0.4f) }
    var tab by remember { mutableIntStateOf(0) }

    val p = remember { Animatable(if (arriving) 0f else 1f) }
    val known = backTarget.width > 0f && titleTarget.width > 0f && lensTarget.width > 0f
    LaunchedEffect(known) {
        if (known && arriving && p.value == 0f) p.animateTo(1f, tween(5600, easing = LinearEasing))
    }
    var shown by remember { mutableIntStateOf(if (arriving) 0 else 99) }
    LaunchedEffect(arriving) {
        if (arriving) {
            snapshotFlow { p.value }.first { it >= 0.95f }
            for (i in 1..6) {
                shown = i
                delay(280)
            }
        }
    }

    // Where each element is at progress [pv]: from the lab's button, out to its own spread-out spot as a round blob,
    // into the shape of its button, then over to the button's place.
    fun rectAt(i: Int): Rect {
        val target = when (i) { 0 -> backTarget; 1 -> titleTarget; else -> lensTarget.translate(if (p.value >= 1f) lensDrag else Offset.Zero) }
        if (!arriving || !known) return if (origin != null && !known) origin.translate(-rootPos) else target
        val o = origin!!.translate(-rootPos)
        val d = density.density
        val (bx, by, bs) = when (i) {
            0 -> Triple(-120f, -90f, 64f)
            1 -> Triple(0f, 120f, 84f)
            else -> Triple(120f, -50f, 96f)
        }
        val burstCx = o.center.x + bx * d
        val burstCy = o.center.y + by * d
        val burstSize = bs * d
        val pv = p.value
        val s = ease(win(pv, 0f, 0.35f))
        val w = back(win(pv, 0.35f, 0.62f))
        val m = ease(win(pv, 0.52f, 0.88f))
        val width = lerp(lerp(o.width, burstSize, s), target.width, w)
        val height = lerp(lerp(o.height, burstSize, s), target.height, w)
        val cx = lerp(lerp(o.center.x, burstCx, s), target.center.x, m)
        val cy = lerp(lerp(o.center.y, burstCy, s), target.center.y, m)
        return Rect(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f)
    }
    val contentAlpha = { if (arriving) ease(win(p.value, 0.78f, 0.93f)) else 1f }

    Box(Modifier.fillMaxSize().onGloballyPositioned { rootPos = it.positionInWindow() }) {
        // What the glass looks through: the lab's own colour first, then the playground's page.
        Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = ease(win(p.value, 0f, 0.25f)) }
                    .background(MaterialTheme.colorScheme.surface),
            )
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = ease(win(p.value, 0.2f, 0.55f)) }) { PlaygroundPage() }
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Where the back bubble and the title pill end up (the elements themselves are drawn by the host below).
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).onGloballyPositioned { backTarget = it.boundsInWindow().translate(-rootPos) })
                Box(Modifier.weight(1f).height(52.dp).onGloballyPositioned { titleTarget = it.boundsInWindow().translate(-rootPos) })
            }
            Text(
                stringResource(R.string.liquid_playground_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp).cascade(0, shown),
            )

            // The playground: the page itself (behind) with the lens on it.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    Modifier.offset(110.dp, 24.dp).size(150.dp)
                        .onGloballyPositioned { lensTarget = it.boundsInWindow().translate(-rootPos) },
                )
            }

            // The controls, below the playground.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).cascade(1, shown),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                for (icon in listOf(R.drawable.ic_chat_24dp, R.drawable.ic_call_24dp, R.drawable.ic_contacts_24dp, R.drawable.ic_settings_24dp)) {
                    LiquidGlassButton(onClick = { taps++ }, glass = base, modifier = Modifier.size(60.dp)) {
                        Icon(painterResource(icon), contentDescription = null)
                    }
                }
            }
            Text(
                stringResource(R.string.liquid_playground_taps, taps),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 24.dp).cascade(2, shown),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).cascade(3, shown),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.liquid_playground_toggle), style = MaterialTheme.typography.titleMedium)
                LiquidToggle(selected = { toggled }, onSelect = { toggled = it }, backdrop = backdrop)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp).cascade(4, shown)) {
                Text(stringResource(R.string.liquid_playground_slider, (slider * 100).roundToInt()), style = MaterialTheme.typography.titleMedium)
                LiquidSlider(
                    value = { slider },
                    onValueChange = { slider = it },
                    valueRange = 0f..1f,
                    visibilityThreshold = 0.001f,
                    backdrop = backdrop,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            val tabs = listOf(
                R.drawable.ic_chat_24dp to R.string.liquid_tab_chats,
                R.drawable.ic_call_24dp to R.string.liquid_tab_calls,
                R.drawable.ic_contacts_24dp to R.string.liquid_tab_contacts,
                R.drawable.ic_settings_24dp to R.string.liquid_tab_settings,
            )
            LiquidBottomTabs(
                selectedTabIndex = { tab },
                onTabSelected = { tab = it },
                backdrop = backdrop,
                tabsCount = tabs.size,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).cascade(5, shown),
            ) {
                tabs.forEachIndexed { index, (icon, label) ->
                    LiquidBottomTab({ tab = index }) {
                        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
                        Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // The three elements: one body of glass that pours apart and together, above everything else.
        FluidGlassHost(backdrop, host, Modifier.fillMaxSize()) {
            val round = RoundedCornerShape(50)
            LiquidGlassButton(
                onClick = onBack,
                glass = fluid,
                shape = round,
                modifier = Modifier.placedAt { rectAt(0) },
            ) {
                Icon(painterResource(R.drawable.ic_arrow_back_24dp), contentDescription = null, modifier = Modifier.graphicsLayer { alpha = contentAlpha() })
            }
            LiquidGlassButton(
                onClick = { taps++ },
                glass = fluid,
                shape = round,
                modifier = Modifier.placedAt { rectAt(1) },
            ) {
                Text(
                    stringResource(R.string.liquid_playground_title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer { alpha = contentAlpha() },
                )
            }
            LiquidGlassButton(
                onClick = {},
                glass = fluid,
                shape = round,
                modifier = Modifier
                    .placedAt { rectAt(2) }
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            if (p.value >= 1f) lensDrag += drag
                        }
                    },
            ) {
                Text(
                    stringResource(R.string.liquid_playground_drag_me),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = contentAlpha() },
                )
            }
        }
    }
}

/** The page the glass sits on: a gradient, three drifting blobs and big text, so the bending at the glass's edge is easy to see. */
@Composable
internal fun PlaygroundPage() {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "playgroundPage")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer)),
        ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val angle = t * 2f * Math.PI.toFloat()
            fun blob(color: Color, phase: Float, radius: Float, cx: Float, cy: Float) {
                val a = angle + phase
                drawCircle(
                    color = color.copy(alpha = 0.55f),
                    radius = radius,
                    center = Offset(size.width * (cx + 0.18f * cos(a)), size.height * (cy + 0.12f * sin(a))),
                )
            }
            blob(scheme.primary, 0f, size.minDimension * 0.28f, 0.3f, 0.3f)
            blob(scheme.tertiary, 2.1f, size.minDimension * 0.22f, 0.7f, 0.55f)
            blob(scheme.secondary, 4.2f, size.minDimension * 0.26f, 0.4f, 0.8f)
        }
        Column(Modifier.fillMaxSize().padding(top = 120.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            repeat(7) { row ->
                Text(
                    text = "LIQUID GLASS ${row + 1}",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = scheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(start = (row % 3 * 40).dp),
                )
            }
        }
    }
}
