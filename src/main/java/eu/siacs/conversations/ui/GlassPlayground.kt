package eu.siacs.conversations.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VectorConverter
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.layout.LayoutCoordinates
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.flow.first
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.abs

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

/** Both endpoints are drawn by the moving actor, so neither handoff can pop its contents in. */
@Composable
private fun PlaygroundActorContent(index: Int, merging: Boolean, count: Int) {
    if (index == 6) return // The navigation indicator is a separate, clear lens.
    if (merging) {
        when (index) {
            0 -> Icon(painterResource(R.drawable.ic_palette_24dp), null, tint = MaterialTheme.colorScheme.onSurface)
            1 -> Text("Merge", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            2 -> Icon(painterResource(R.drawable.ic_touch_app_24dp), null, tint = MaterialTheme.colorScheme.onSurface)
            else -> Text(stringResource(when (index) {
                3 -> R.string.glass_lab_merge_add
                4 -> R.string.glass_lab_merge_remove
                else -> R.string.glass_lab_reset
            }, count), maxLines = 1, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.graphicsLayer { alpha = if ((index == 3 && count >= 16) || (index == 4 && count <= 1)) 0.38f else 1f })
        }
    } else when (index) {
        1 -> Text(stringResource(R.string.liquid_playground_drag_me), style = MaterialTheme.typography.labelLarge)
        3 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            listOf(R.drawable.ic_chat_24dp to R.string.liquid_tab_chats,
                R.drawable.ic_call_24dp to R.string.liquid_tab_calls,
                R.drawable.ic_contacts_24dp to R.string.liquid_tab_contacts,
                R.drawable.ic_settings_24dp to R.string.liquid_tab_settings).forEach { (icon, label) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(icon), null, modifier = Modifier.size(24.dp))
                    Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
        else -> Icon(painterResource(when (index) {
            0 -> R.drawable.ic_chat_24dp
            2 -> R.drawable.ic_call_24dp
            4 -> R.drawable.ic_contacts_24dp
            else -> R.drawable.ic_settings_24dp
        }), null)
    }
}

/** The two experiments as glass pills: the open one is filled with the theme's colour and fully round, the other is a softer rounded shape. */
@Composable
private fun PlaygroundTabs(tab: Int, onTab: (Int) -> Unit, glass: eu.siacs.conversations.ui.glass.GlassStack, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(R.string.liquid_playground_tab_simple, R.string.liquid_playground_tab_merging).forEachIndexed { i, label ->
            val selected = tab == i
            val corner by animateDpAsState(if (selected) 26.dp else 14.dp, spring(dampingRatio = 0.6f, stiffness = 380f), label = "tabCorner")
            val fill by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f) else Color.Transparent,
                label = "tabFill",
            )
            LiquidGlassButton(
                onClick = { onTab(i) },
                glass = glass,
                shape = RoundedCornerShape(corner),
                tint = fill,
                style = LiquidGlassStyle.Surface,
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
            }
        }
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
    val base = rememberGlassStack(backdrop, adaptive = true)
    // The three elements melt into each other while they are close: a wide reach, a slow, soft join.
    val host = remember { FluidGlassHost(density.density, LiquidGlassStyle.Bar, reachDp = 22f, softnessDp = 14f, responseSeconds = 0.5f) }
    val fluid = remember(base, host) { base.withFluid(host) }

    val arriving = origin != null
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    var backTarget by remember { mutableStateOf(Rect.Zero) }
    var titleTarget by remember { mutableStateOf(Rect.Zero) }
    var lensTarget by remember { mutableStateOf(Rect.Zero) }
    var lensDrag by remember { mutableStateOf(Offset.Zero) }
    var lensDragging by remember { mutableStateOf(false) }
    var lensCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val lensPressure = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var lensArea by remember { mutableStateOf(Rect.Zero) }
    var page by remember { mutableIntStateOf(0) }
    val fluidState = eu.siacs.conversations.ui.glass.rememberGlassFluidState()
    var fieldBounds by remember { mutableStateOf(Rect.Zero) }
    val protoButtons = remember { mutableStateListOf(Rect.Zero, Rect.Zero, Rect.Zero) }
    val roundButtons = remember { mutableStateListOf(Rect.Zero, Rect.Zero, Rect.Zero, Rect.Zero) }
    var barBounds by remember { mutableStateOf(Rect.Zero) }
    var indicatorBounds by remember { mutableStateOf(Rect.Zero) }
    val tabHold = remember { Animatable(0f) }
    var preparingMorph by remember { mutableStateOf(false) }
    var fieldBackdrop by remember { mutableStateOf<com.kyant.backdrop.backdrops.LayerBackdrop?>(null) }
    // Changing tab: the glass of one tab flows over to the other's (1 = at rest).
    val morph = remember { Animatable(1f) }
    var morphFrom by remember { mutableIntStateOf(0) }
    var morphActive by remember { mutableStateOf(false) }
    val morphing = morphActive
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun switchTo(target: Int) {
        if (target == page || morphActive || preparingMorph) return
        preparingMorph = true
        scope.launch {
            if (page == 0) tabHold.animateTo(1f, tween(180)) else tabHold.snapTo(1f)
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            morphFrom = page
            morph.snapTo(0f)
            morphActive = true
            page = target
            snapshotFlow {
                fieldBounds.width > 0f && protoButtons.all { it.width > 0f } &&
                    roundButtons.all { it.width > 0f } && barBounds.width > 0f && indicatorBounds.width > 0f
            }.first { it }
            androidx.compose.runtime.withFrameNanos { }
            morph.animateTo(1f, tween(1900, easing = LinearEasing))
            androidx.compose.runtime.withFrameNanos { }
            morphActive = false
            if (target == 0) tabHold.animateTo(0f, tween(220)) else tabHold.snapTo(0f)
            preparingMorph = false
        }
    }
    androidx.compose.runtime.SideEffect { fluidState.hideForMorph(morphActive) }
    var taps by remember { mutableIntStateOf(0) }
    var toggled by remember { mutableStateOf(true) }
    var slider by remember { mutableFloatStateOf(0.4f) }
    var tab by remember { mutableIntStateOf(0) }

    val p = remember { Animatable(if (arriving) 0f else 1f) }
    eu.siacs.conversations.ui.glass.pauseGlassInkWhile {
        morphActive || preparingMorph || lensDragging || p.value < 1f
    }
    LaunchedEffect(page, morphActive) {
        if (page != 0 || morphActive) lensDragging = false
    }
    val known = backTarget.width > 0f && titleTarget.width > 0f && lensTarget.width > 0f
    LaunchedEffect(known) {
        if (known && arriving && p.value == 0f) p.animateTo(1f, tween(5600, easing = LinearEasing))
    }
    var shown by remember { mutableIntStateOf(if (arriving) 0 else 99) }
    LaunchedEffect(arriving) {
        if (arriving) {
            snapshotFlow { p.value }.first { it >= 0.95f }
            for (i in 1..7) {
                shown = i
                delay(280)
            }
        }
    }

    // Where each element is at progress [pv]: from the lab's button, out to its own spread-out spot as a round blob,
    // into the shape of its button, then over to the button's place.
    fun clampTo(r: Rect, area: Rect): Rect {
        if (area.width <= 0f) return r
        val dx = if (r.left < area.left) area.left - r.left else if (r.right > area.right) area.right - r.right else 0f
        val dy = if (r.top < area.top) area.top - r.top else if (r.bottom > area.bottom) area.bottom - r.bottom else 0f
        return r.translate(dx, dy)
    }

    fun lensRect(): Rect {
        val placed = clampTo(lensTarget.translate(lensDrag), lensArea)
        val pressure = lensPressure.value
        val sx = (1f / (1f + abs(pressure.x) / placed.width.coerceAtLeast(1f))).coerceAtLeast(0.82f)
        val sy = (1f / (1f + abs(pressure.y) / placed.height.coerceAtLeast(1f))).coerceAtLeast(0.82f)
        val hw = placed.width * sx / 2f
        val hh = placed.height * sy / 2f
        val cx = when { pressure.x > 0f -> placed.right - hw; pressure.x < 0f -> placed.left + hw; else -> placed.center.x }
        val cy = when { pressure.y > 0f -> placed.bottom - hh; pressure.y < 0f -> placed.top + hh; else -> placed.center.y }
        return Rect(cx - hw, cy - hh, cx + hw, cy + hh)
    }

    fun rectAt(i: Int): Rect {
        val target = when (i) { 0 -> backTarget; 1 -> titleTarget; else -> if (p.value >= 1f) lensRect() else clampTo(lensTarget, lensArea) }
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
    // The glass elements that take part in the tab morph, in pairs: the simple tab's [button, lens, button, bar, button,
    // button] to the merging tab's [piece, piece, piece, add, remove, reset].
    fun simpleRect(i: Int): Rect = when (i) {
        0 -> roundButtons[0]
        1 -> lensRect()
        2 -> roundButtons[1]
        3 -> {
            val dx = with(density) { 8.dp.toPx() } * tabHold.value
            val dy = if (barBounds.width > 0f) barBounds.height * dx / barBounds.width else 0f
            Rect(barBounds.left - dx, barBounds.top - dy, barBounds.right + dx, barBounds.bottom + dy)
        }
        4 -> roundButtons[2]
        5 -> roundButtons[3]
        else -> indicatorBounds
    }
    fun mergingRect(i: Int): Rect {
        if (i == 6) return mergingRect(0)
        if (i >= 3) return protoButtons[i - 3].translate(-rootPos)
        val piece = fluidState.pieces.getOrNull(i) ?: fluidState.pieces.firstOrNull()
        return if (piece == null) {
            Rect(fieldBounds.center.x - 30f, fieldBounds.center.y - 30f, fieldBounds.center.x + 30f, fieldBounds.center.y + 30f).translate(-rootPos)
        } else {
            Rect(
                fieldBounds.left + piece.left, fieldBounds.top + piece.top,
                fieldBounds.left + piece.left + piece.width, fieldBounds.top + piece.top + piece.height,
            ).translate(-rootPos)
        }
    }
    fun actorRect(i: Int): Rect {
        val from = if (morphFrom == 0) simpleRect(i) else mergingRect(i)
        val to = if (morphFrom == 0) mergingRect(i) else simpleRect(i)
        if (from.width <= 0f || to.width <= 0f) return if (to.width > 0f) to else from
        val m = morph.value
        val move = ease(win(m, 0.05f, 0.85f))
        val grow = back(win(m, 0.15f, 0.8f))
        val w = lerp(from.width, to.width, grow)
        val h = lerp(from.height, to.height, grow)
        val cx = lerp(from.center.x, to.center.x, move)
        val cy = lerp(from.center.y, to.center.y, move)
        return Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
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
            // The two experiments are tabs: only the open one exists, so the other costs nothing.
            PlaygroundTabs(tab = page, onTab = { switchTo(it) }, glass = base, modifier = Modifier.cascade(0, shown))

            // Both tabs are laid out while the glass moves over (so each knows where its places are); the one that
            // is leaving fades out first and the one that is arriving fades in last.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                fun pageAlpha(which: Int): Float = when {
                    !morphing -> 1f
                    morphFrom == which -> 1f - ease(win(morph.value, 0f, 0.3f))
                    else -> ease(win(morph.value, 0.7f, 1f))
                }
                if (page == 0 || (morphing && morphFrom == 0)) {
                    // The simple glass demo: one screen that does not scroll.
                    Column(Modifier.fillMaxSize().graphicsLayer { alpha = pageAlpha(0) }) {
                        Text(
                            stringResource(R.string.liquid_playground_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp).cascade(0, shown),
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).cascade(1, shown)
                                .graphicsLayer { alpha = if (morphing) 0f else 1f },
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            listOf(R.drawable.ic_chat_24dp, R.drawable.ic_call_24dp, R.drawable.ic_contacts_24dp, R.drawable.ic_settings_24dp)
                                .forEachIndexed { index, icon ->
                                    LiquidGlassButton(
                                        onClick = { taps++ },
                                        glass = base,
                                        style = LiquidGlassStyle.Surface,
                                        modifier = Modifier.size(60.dp)
                                            .onGloballyPositioned { roundButtons[index] = it.boundsInWindow().translate(-rootPos) },
                                    ) {
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
                        // The lens has the room under the slider; it cannot leave it.
                        Box(
                            Modifier.weight(1f).fillMaxWidth()
                                .onGloballyPositioned { lensArea = it.boundsInWindow().translate(-rootPos) },
                        ) {
                            Box(
                                Modifier.offset(110.dp, 24.dp).size(150.dp)
                                    .onGloballyPositioned { lensTarget = it.boundsInWindow().translate(-rootPos) },
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
                            heldProgress = { tabHold.value },
                            onIndicatorBounds = { indicatorBounds = it.translate(-rootPos) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).cascade(5, shown)
                                .graphicsLayer { alpha = if (morphing) 0f else 1f }
                                .onGloballyPositioned { barBounds = it.boundsInWindow().translate(-rootPos) },
                        ) {
                            tabs.forEachIndexed { index, (icon, label) ->
                                LiquidBottomTab({ tab = index }) {
                                    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
                                    Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                if (page == 1 || (morphing && morphFrom == 1)) {
                    // The merging glass experiment: its field first, its controls under it. This one scrolls.
                    Column(Modifier.fillMaxSize().graphicsLayer { alpha = pageAlpha(1) }.verticalScroll(rememberScrollState())) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            FluidPrototype(
                                fluid = fluidState,
                                controlsAlpha = if (morphing) 0f else 1f,
                                onFieldBounds = { fieldBounds = it },
                                onButtonBounds = { i, r -> protoButtons[i] = r },
                                controlsGlass = base,
                                onFieldBackdrop = { fieldBackdrop = it },
                            )
                        }
                    }
                }
            }
        }

        PlaygroundAnimatedOverlay {
            val patternAlpha by rememberUpdatedState(when {
                !morphing -> if (page == 1) 1f else 0f
                morphFrom == 1 -> 1f - ease(win(morph.value, 0f, 0.3f))
                else -> ease(win(morph.value, 0.7f, 1f))
            })
            val pattern = fieldBackdrop?.let { field ->
                com.kyant.backdrop.backdrops.rememberBackdrop(field) { drawBackdrop ->
                    if (patternAlpha > 0f) {
                        drawContext.canvas.saveLayer(Rect(Offset.Zero, size), androidx.compose.ui.graphics.Paint().apply { alpha = patternAlpha })
                        drawBackdrop()
                        drawContext.canvas.restore()
                    }
                }
            }
            val actorBackdrop = if (pattern != null) com.kyant.backdrop.backdrops.rememberCombinedBackdrop(backdrop, pattern) else backdrop
            val clear = if (!morphing) page.toFloat() else if (morphFrom == 0) ease(morph.value) else 1f - ease(morph.value)
            val actorStyle = LiquidGlassStyle(blur = (2f * (1f - clear)).dp,
                lensHeight = (12f + 2f * clear).dp, lensAmount = (24f - 4f * clear).dp,
                chromaticAberration = false, vibrancy = false, saturation = 1.25f - 0.25f * clear)
            // The moving controls and both separate parts of the navigation bar share one fluid body.
            FluidGlassHost(actorBackdrop, host, Modifier.fillMaxSize(), styleOverride = actorStyle) {
                val round = RoundedCornerShape(50)
                // The glass of the tab being left, flowing to where the other tab has its own.
                if (morphing) {
                    for (i in 0 until 7) {
                        androidx.compose.runtime.key(i) {
                            LiquidGlassButton(
                                onClick = {},
                                glass = fluid,
                                shape = RoundedCornerShape(with(density) {
                                    val piece = if (i < 3) fluidState.pieces.getOrNull(i) else null
                                    val target = mergingRect(i)
                                    val pieceRadius = piece?.radius ?: if (i in 3..5) with(density) { 20.dp.toPx() } else minOf(target.width, target.height) / 2f
                                    val simple = simpleRect(i)
                                    val simpleRadius = minOf(simple.width, simple.height) / 2f
                                    lerp(
                                        if (morphFrom == 0) simpleRadius else pieceRadius,
                                        if (morphFrom == 0) pieceRadius else simpleRadius,
                                        ease(win(morph.value, 0.15f, 0.8f)),
                                    ).coerceAtLeast(0f).toDp()
                                }),
                                isInteractive = false,
                                style = LiquidGlassStyle.Surface,
                                surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = run {
                                    val simple = if (i == 1 || i == 6) 0f else LiquidGlassStyle.Surface.surfaceAlpha
                                    val merging = if (i in 3..5) LiquidGlassStyle.Surface.surfaceAlpha else 0f
                                    lerp(if (morphFrom == 0) simple else merging,
                                        if (morphFrom == 0) merging else simple, ease(win(morph.value, 0.05f, 0.95f)))
                                }),
                                fluidPresence = if (i == 6 || (i < 3 && i >= fluidState.pieces.size)) ({
                                    if (morphFrom == 0) 1f - ease(win(morph.value, 0.65f, 0.95f))
                                    else ease(win(morph.value, 0.05f, 0.35f))
                                }) else null,
                                modifier = Modifier.placedAt { actorRect(i) },
                            ) {
                                Box(Modifier.graphicsLayer { alpha = 1f - ease(win(morph.value, 0.12f, 0.58f)) }) {
                                    PlaygroundActorContent(i, morphFrom == 1, fluidState.pieces.size)
                                }
                                Box(Modifier.graphicsLayer { alpha = ease(win(morph.value, 0.42f, 0.88f)) }) {
                                    PlaygroundActorContent(i, morphFrom == 0, fluidState.pieces.size)
                                }
                            }
                        }
                    }
                }
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
                if (page == 0 && !morphing) LiquidGlassButton(
                    onClick = {},
                    glass = fluid,
                    shape = round,
                    isInteractive = false,
                    modifier = Modifier
                        .placedAt { rectAt(2) }
                        .onGloballyPositioned { lensCoordinates = it }
                        .pointerInput(Unit) {
                            var grabbed = Offset.Zero
                            var initial = Offset.Zero
                            detectDragGestures(
                                onDragStart = { local ->
                                    lensDragging = true
                                    grabbed = lensCoordinates?.localToWindow(local) ?: Offset.Zero
                                    initial = lensDrag
                                },
                                onDragEnd = { lensDragging = false; scope.launch { lensPressure.animateTo(Offset.Zero, spring(0.8f, 380f)) } },
                                onDragCancel = { lensDragging = false; scope.launch { lensPressure.animateTo(Offset.Zero, spring(0.8f, 380f)) } },
                            ) { change, _ ->
                                change.consume()
                                if (p.value >= 1f) {
                                    val at = lensCoordinates?.localToWindow(change.position) ?: grabbed
                                    val wanted = lensTarget.translate(initial + at - grabbed)
                                    val clamped = clampTo(wanted, lensArea)
                                    lensDrag = clamped.topLeft - lensTarget.topLeft
                                    scope.launch { lensPressure.snapTo(wanted.topLeft - clamped.topLeft) }
                                }
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
}

@Composable
private fun PlaygroundAnimatedOverlay(content: @Composable () -> Unit) { content() }

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
