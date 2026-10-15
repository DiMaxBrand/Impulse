package eu.siacs.conversations.ui.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.ActionBarActivity
import eu.siacs.conversations.ui.ImpulseExpressiveTheme
import eu.siacs.conversations.ui.LiquidGlass
import eu.siacs.conversations.ui.LiquidGlassSurface
import eu.siacs.conversations.ui.glassTint
import eu.siacs.conversations.ui.liquidFeel
import eu.siacs.conversations.ui.liquidTouch
import eu.siacs.conversations.ui.rememberLiquidState
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * Developer-options screen for the liquid glass experiment: a colourful, slowly moving page and,
 * over it, the pieces the experiment uses -- glass buttons, a lens you can drag anywhere, and a
 * bottom tab bar whose indicator springs between the tabs (or can be dragged). The tab bar is only
 * here for now; it is not part of any real screen.
 */
class LiquidGlassPlaygroundActivity : ActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ImpulseExpressiveTheme {
                if (LiquidGlass.supported) {
                    Playground(onBack = { finish() })
                } else {
                    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.liquid_playground_unsupported),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Playground(onBack: () -> Unit) {
    // Everything behind the glass is recorded here; the glass is drawn on top and shows it.
    val backdrop = rememberLayerBackdrop()
    var taps by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { PlaygroundPage() }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Top: a back bubble and a title bubble.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val backState = rememberLiquidState()
                LiquidGlassSurface(
                    backdrop = backdrop,
                    shape = CircleShape,
                    state = backState,
                    modifier = Modifier.size(52.dp).liquidTouch(backState, onBack),
                ) {
                    Icon(painterResource(R.drawable.ic_arrow_back_24dp), contentDescription = null)
                }
                val titleState = rememberLiquidState()
                LiquidGlassSurface(
                    backdrop = backdrop,
                    shape = CircleShape,
                    state = titleState,
                    modifier = Modifier.weight(1f).height(52.dp).liquidTouch(titleState) { taps++ },
                ) {
                    Text(
                        stringResource(R.string.liquid_playground_title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                }
            }

            Text(
                stringResource(R.string.liquid_playground_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )

            // Middle: four round buttons that stretch when pressed or dragged.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                for (icon in listOf(
                    R.drawable.ic_chat_24dp,
                    R.drawable.ic_call_24dp,
                    R.drawable.ic_contacts_24dp,
                    R.drawable.ic_settings_24dp,
                )) {
                    val state = rememberLiquidState()
                    LiquidGlassSurface(
                        backdrop = backdrop,
                        shape = CircleShape,
                        state = state,
                        modifier = Modifier.size(60.dp).liquidTouch(state) { taps++ },
                    ) {
                        Icon(painterResource(icon), contentDescription = null)
                    }
                }
            }
            Text(
                stringResource(R.string.liquid_playground_taps, taps),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                DraggableLens(backdrop)
            }

            GlassTabBar(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
    }
}

/** The page the glass sits on: a gradient, three drifting blobs and big text, so the bending at the
 * glass's edge is easy to see. */
@Composable
private fun PlaygroundPage() {
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

/** A glass circle that follows the finger around the page, stretching as it goes. */
@Composable
private fun DraggableLens(backdrop: Backdrop) {
    val density = LocalDensity.current
    var offset by remember { mutableStateOf(with(density) { Offset(110.dp.toPx(), 40.dp.toPx()) }) }
    val state = rememberLiquidState()
    LiquidGlassSurface(
        backdrop = backdrop,
        shape = CircleShape,
        state = state,
        tint = glassTint(),
        modifier = Modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .size(150.dp)
            .liquidFeel(state)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offset += drag
                }
            },
    ) {
        Text(
            stringResource(R.string.liquid_playground_drag_me),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Four tabs on a glass bar. The selected one is marked by a second, smaller piece of glass that
 * springs to the tapped tab and jiggles when it arrives; it can also be grabbed and dragged along
 * the bar, and snaps to the nearest tab when let go.
 */
@Composable
private fun GlassTabBar(backdrop: Backdrop, modifier: Modifier = Modifier) {
    val tabs = listOf(
        R.drawable.ic_chat_24dp to R.string.liquid_tab_chats,
        R.drawable.ic_call_24dp to R.string.liquid_tab_calls,
        R.drawable.ic_contacts_24dp to R.string.liquid_tab_contacts,
        R.drawable.ic_settings_24dp to R.string.liquid_tab_settings,
    )
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val barState = rememberLiquidState()
    val indicatorState = rememberLiquidState()
    val position = remember { Animatable(0f) } // left edge of the indicator inside the bar, px
    var selected by remember { mutableIntStateOf(0) }
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    val inset = with(density) { 6.dp.toPx() }
    val barHeight = 72.dp

    fun select(index: Int) {
        val target = index.coerceIn(0, tabs.lastIndex)
        if (target != selected) indicatorState.pulse(x = 0.12f, y = -0.04f)
        selected = target
        scope.launch { position.animateTo(target * tabWidthPx, spring(dampingRatio = 0.55f, stiffness = 320f)) }
    }

    Box(
        modifier = modifier.fillMaxWidth().height(barHeight)
            .onSizeChanged { tabWidthPx = (it.width - 2 * inset) / tabs.size },
    ) {
        LiquidGlassSurface(
            backdrop = backdrop,
            shape = CircleShape,
            state = barState,
            modifier = Modifier.fillMaxSize(),
        ) {}

        val tabWidth = with(density) { tabWidthPx.toDp() }
        LiquidGlassSurface(
            backdrop = backdrop,
            shape = CircleShape,
            state = indicatorState,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
            modifier = Modifier
                .offset { IntOffset((inset + position.value).roundToInt(), inset.roundToInt()) }
                .width(tabWidth)
                .height(barHeight - 12.dp),
        ) {}

        Row(
            Modifier.fillMaxSize()
                .padding(horizontal = with(density) { inset.toDp() })
                .pointerInput(tabWidthPx) {
                    detectTapGestures { tap -> select((tap.x / tabWidthPx).toInt()) }
                }
                .pointerInput(tabWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { indicatorState.setPressed(true) },
                        onDragEnd = {
                            indicatorState.setPressed(false)
                            select((position.value / tabWidthPx).roundToInt())
                        },
                        onDragCancel = {
                            indicatorState.setPressed(false)
                            select(selected)
                        },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            scope.launch {
                                position.snapTo((position.value + dx).coerceIn(0f, tabWidthPx * tabs.lastIndex))
                            }
                        },
                    )
                },
        ) {
            tabs.forEachIndexed { index, (icon, label) ->
                Column(
                    Modifier.weight(1f).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val color =
                        if (index == selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = color)
                }
            }
        }
    }
}
