package eu.siacs.conversations.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tanh
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/*
 * The glass pieces of the liquid glass experiment: a button, a toggle, a slider and a bottom tab bar.
 * Ported from the demo (catalog) app of the Backdrop library by Kyant0,
 * https://github.com/Kyant0/AndroidLiquidGlass (Apache License 2.0), numbers and all. Shapes are the
 * Material rounded-corner kinds (the library's own capsule is not on our compile classpath), and
 * colours come from the app theme instead of the demo's fixed iOS ones.
 */

/** Colours shared by the pieces below, taken from the app's theme. */
object LiquidGlassDefaults {
    /** The faint body of a glass piece, so it stays readable over very plain backgrounds. */
    @Composable
    fun surfaceColor(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)

    /** The groove behind a toggle or slider. */
    @Composable
    fun trackColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
}

/**
 * A glass button, the demo's `LiquidButton`: it brightens under the finger with a light that
 * follows it, swells a few dp, and leans toward a dragging finger, but only a little (the move is
 * squeezed through a `tanh` curve, so a long drag barely shifts it) while stretching along the
 * direction of the drag. Pressed lenses show colour fringes (chromatic aberration).
 *
 * [shape] must be a rounded-corner shape (a circle or a pill is the usual choice).
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    glass: GlassStack,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = CircleShape,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    // Unspecified: the style's own surface colour (the theme's surface at the style's opacity).
    surfaceColor: Color = Color.Unspecified,
    style: LiquidGlassStyle = LiquidGlassStyle.Button,
    interactiveHighlight: InteractiveHighlight = rememberInteractiveHighlight(),
    // Applied after the button's own press/drag transform: lets whatever holds the button push or
    // squeeze it (the message field pressing on the send button).
    extraLayerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    // This button's place in the stack: it records itself for the glass above it, and looks
    // through the screen and the glass below it.
    val slot = glass.rememberSlot()
    val body = if (surfaceColor.isSpecified) surfaceColor else style.surfaceColor()
    Box(
        modifier = modifier
            .layerBackdrop(slot.record)
            .drawBackdrop(
                backdrop = slot.backdrop,
                shape = { shape },
                effects = {
                    // Too fast to see the glass anyway while the list flies past: skip the effects.
                    if (!GlassPerformance.lite) {
                        if (style.vibrancy) vibrancy()
                        blur(style.blur.toPx())
                        glassLens(style.lensHeight.toPx(), style.lensAmount.toPx(), style.chromaticAberration)
                    }
                },
                layerBlock = if (isInteractive || extraLayerBlock != null) {
                    {
                        if (isInteractive) buttonLayer(interactiveHighlight)
                        extraLayerBlock?.invoke(this)
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (body.isSpecified) {
                        drawRect(body)
                    }
                },
            )
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .then(
                if (isInteractive) {
                    Modifier.then(interactiveHighlight.modifier).then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * The press and drag transform of a glass button, as the demo does it: a few dp of swell, a move
 * of only ~5% of the finger's travel squeezed through `tanh`, and a stretch along the drag.
 */
private fun GraphicsLayerScope.buttonLayer(interactiveHighlight: InteractiveHighlight) {
    val width = size.width
    val height = size.height

    val progress = interactiveHighlight.pressProgress
    val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

    val maxOffset = size.minDimension
    val initialDerivative = 0.05f
    val offset = interactiveHighlight.offset
    translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
    translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

    val maxDragScale = 4f.dp.toPx() / size.height
    val offsetAngle = atan2(offset.y, offset.x)
    scaleX =
        scale +
            maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
            (width / height).fastCoerceAtMost(1f)
    scaleY =
        scale +
            maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
            (height / width).fastCoerceAtMost(1f)
}

/**
 * A glass container that is not a button: the body of the message field, the panels over the
 * message bar. Same glass as [LiquidGlassButton] (without the press behaviour); [layerBlock] lets the
 * caller stretch it, [contentAlignment] defaults to the top start because panels whose height
 * animates reveal their content from the top.
 */
@Composable
fun LiquidGlassPanel(
    glass: GlassStack,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = RoundedCornerShape(24.dp),
    // Unspecified: the style's own surface colour.
    surfaceColor: Color = Color.Unspecified,
    style: LiquidGlassStyle = LiquidGlassStyle.Panel,
    contentAlignment: Alignment = Alignment.TopStart,
    // The panel has a size of its own (a fixed or animated one) and [content] may be bigger than it
    // for a moment, e.g. text that is taller than the field is yet: it is then clipped to the panel
    // instead of defining the panel's size.
    clipContent: Boolean = false,
    // Stretches the GLASS only: what is inside keeps its shape, so a bulging field does not squash
    // its own letters.
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val slot = glass.rememberSlot()
    val body = if (surfaceColor.isSpecified) surfaceColor else style.surfaceColor()
    // Two layers, not one: the glass (which [layerBlock] deforms) and the content on top of it
    // (which nothing deforms). The recording for the glass above covers both.
    Box(
        modifier = modifier.layerBackdrop(slot.record),
        contentAlignment = contentAlignment,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .drawBackdrop(
                    backdrop = slot.backdrop,
                    shape = { shape },
                    effects = {
                        if (!GlassPerformance.lite) {
                            if (style.vibrancy) vibrancy()
                            blur(style.blur.toPx())
                            glassLens(style.lensHeight.toPx(), style.lensAmount.toPx(), style.chromaticAberration)
                        }
                    },
                    layerBlock = layerBlock,
                    onDrawSurface = { if (body.isSpecified) drawRect(body) },
                ),
        )
        if (clipContent) {
            Box(Modifier.matchParentSize().clip(shape), contentAlignment = contentAlignment) { content() }
        } else {
            Box(Modifier.clip(shape), contentAlignment = contentAlignment, content = content)
        }
    }
}

/** The demo's `LiquidToggle`: a glass thumb on a coloured track that swells into a lens when held. */
@Composable
fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = LiquidGlassDefaults.trackColor(),
) {
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20f.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (selected()) 1f else 0f) }
    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    onSelect(fraction == 1f)
                    didDrag = false
                } else {
                    fraction = if (selected()) 0f else 1f
                    onSelect(fraction == 1f)
                }
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) {
                    didDrag = dragAmount.x != 0f
                }
                val delta = dragAmount.x / dragWidth
                fraction =
                    if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                    else (fraction - delta).fastCoerceIn(0f, 1f)
            },
        )
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }.collectLatest { dampedDragAnimation.updateValue(it) }
    }
    LaunchedEffect(selected) {
        snapshotFlow { selected() }.collectLatest { isSelected ->
            val target = if (isSelected) 1f else 0f
            if (target != fraction) {
                fraction = target
                dampedDragAnimation.animateToValue(target)
            }
        }
    }

    val trackBackdrop = rememberLayerBackdrop()

    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(CircleShape)
                .drawBehind { drawRect(lerp(trackColor, accentColor, dampedDragAnimation.value)) }
                .size(64f.dp, 28f.dp)
        )

        Box(
            Modifier
                .graphicsLayer {
                    val padding = 2f.dp.toPx()
                    translationX =
                        if (isLtr) lerp(padding, padding + dragWidth, dampedDragAnimation.value)
                        else lerp(-padding, -(padding + dragWidth), dampedDragAnimation.value)
                }
                .semantics { role = Role.Switch }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 0.75f, progress)
                            val scaleY = lerp(0f, 0.75f, progress)
                            scale(scaleX, scaleY) { drawBackdrop() }
                        },
                    ),
                    shape = { CircleShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        glassLens(5f.dp.toPx() * progress, 10f.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress,
                        )
                    },
                    shadow = { Shadow(radius = 4f.dp, color = Color.Black.copy(alpha = 0.05f)) },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 4f.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    },
                )
                .size(40f.dp, 24f.dp)
        )
    }
}

/** The demo's `LiquidSlider`: a thumb that turns into a see-through lens over the track while held. */
@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = LiquidGlassDefaults.trackColor(),
) {
    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        val trackWidth = constraints.maxWidth

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = {
                    if (didDrag) {
                        onValueChange(targetValue)
                    }
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) {
                        didDrag = dragAmount.x != 0f
                    }
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / trackWidth)
                    onValueChange(
                        if (isLtr) (targetValue + delta).coerceIn(valueRange)
                        else (targetValue - delta).coerceIn(valueRange)
                    )
                },
            )
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { value() }.collectLatest { value ->
                if (dampedDragAnimation.targetValue != value) {
                    dampedDragAnimation.updateValue(value)
                }
            }
        }

        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(trackColor)
                    .pointerInput(animationScope) {
                        detectTapGestures { position ->
                            val delta = (valueRange.endInclusive - valueRange.start) * (position.x / trackWidth)
                            val targetValue =
                                (if (isLtr) valueRange.start + delta else valueRange.endInclusive - delta)
                                    .coerceIn(valueRange)
                            dampedDragAnimation.animateToValue(targetValue)
                            onValueChange(targetValue)
                        }
                    }
                    .height(6f.dp)
                    .fillMaxWidth()
            )

            Box(
                Modifier
                    .clip(CircleShape)
                    .background(accentColor)
                    .height(6f.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * dampedDragAnimation.progress).fastRoundToInt()
                        layout(width, placeable.height) { placeable.place(0, 0) }
                    }
            )
        }

        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                            .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) *
                            if (isLtr) 1f else -1f
                }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 1f, progress)
                            val scaleY = lerp(0f, 1f, progress)
                            scale(scaleX, scaleY) { drawBackdrop() }
                        },
                    ),
                    shape = { CircleShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        glassLens(10f.dp.toPx() * progress, 14f.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress,
                        )
                    },
                    shadow = { Shadow(radius = 4f.dp, color = Color.Black.copy(alpha = 0.05f)) },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 4f.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    },
                )
                .size(40f.dp, 24f.dp)
        )
    }
}

internal val LocalLiquidBottomTabScale = staticCompositionLocalOf { { 1f } }

/** One tab of [LiquidBottomTabs]; it grows a little while the bubble is held. */
@Composable
fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalLiquidBottomTabScale.current
    Column(
        modifier
            .clip(CircleShape)
            .clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick)
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/**
 * The demo's bottom tab bar. The bubble that marks the selected tab can be dragged along the bar;
 * it swells while held into a lens that shows the tabs magnified and tinted with the accent colour,
 * squashes with speed, and settles on the nearest tab when let go. The whole bar leans a few dp
 * toward the drag. [content] is drawn twice (once plain, once tinted, for the lens to look at), so
 * it should hold no state of its own.
 */
@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
    content: @Composable RowScope.() -> Unit,
) {
    val tabsBackdrop = rememberLayerBackdrop()
    val isDark = MaterialTheme.colorScheme.surface.run { red + green + blue } < 1.5f

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) { (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabIndex) { mutableIntStateOf(selectedTabIndex()) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                },
            )
        }
        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }.collectLatest { index -> currentIndex = index }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelected(index)
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f,
                    )
                },
            )
        }

        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        glassLens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(interactiveHighlight.modifier)
                .height(64f.dp)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        // The same tabs again, tinted, invisible: only there for the bubble's lens to look at.
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(8f.dp.toPx())
                            glassLens(24f.dp.toPx() * progress, 24f.dp.toPx() * progress)
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56f.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { CircleShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        glassLens(10f.dp.toPx() * progress, 14f.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 8f.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.1f),
                            alpha = 1f - progress,
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                )
                .height(56f.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}
