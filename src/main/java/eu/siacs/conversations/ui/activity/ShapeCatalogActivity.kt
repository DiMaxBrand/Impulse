package eu.siacs.conversations.ui.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.ActionBarActivity
import eu.siacs.conversations.ui.ImpulseExpressiveTheme
import eu.siacs.conversations.ui.MaterialShapeHelpers

/** Name + shape pairs, in the same order MaterialShapeHelpers exposes them. */
private val CATALOG_SHAPES: List<Pair<String, RoundedPolygon>> by lazy {
    listOf(
        "Circle" to MaterialShapeHelpers.circle(),
        "Pill" to MaterialShapeHelpers.pill(),
        "Semi-circle" to MaterialShapeHelpers.semiCircle(),
        "Diamond" to MaterialShapeHelpers.diamond(),
        "Gem" to MaterialShapeHelpers.gem(),
        "Ghostish" to MaterialShapeHelpers.ghostish(),
        "Soft burst" to MaterialShapeHelpers.softBurst(),
        "Slanted" to MaterialShapeHelpers.slanted(),
        "Arrow" to MaterialShapeHelpers.arrow(),
    )
}

/**
 * Developer-options screen for browsing the MaterialShapes set: a hero shape pinned to the top
 * third of the screen morphs (via [Morph], the same technique the chat list's presence-shaped
 * avatar frame already uses) into whatever shape is tapped in the scrollable catalog below.
 * The catalog wraps with FlowRow rather than scrolling sideways — only the catalog itself
 * scrolls vertically; the hero never moves.
 */
class ShapeCatalogActivity : ActionBarActivity() {

    @OptIn(
        ExperimentalMaterial3ExpressiveApi::class,
        ExperimentalMaterial3Api::class,
        ExperimentalFoundationApi::class,
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ImpulseExpressiveTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.shape_catalog_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_arrow_back_24dp),
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                    },
                ) { padding ->
                    var selectedIndex by remember { mutableIntStateOf(0) }
                    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                        ShapeHero(
                            shape = CATALOG_SHAPES[selectedIndex].second,
                            name = CATALOG_SHAPES[selectedIndex].first,
                            // weight(1f) against the catalog's weight(2f) below splits the
                            // screen exactly into thirds — the hero never scrolls with it.
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        HorizontalDivider()
                        FlowRow(
                            modifier = Modifier
                                .weight(2f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CATALOG_SHAPES.forEachIndexed { index, (name, shape) ->
                                ShapeCatalogItem(
                                    name = name,
                                    shape = shape,
                                    selected = index == selectedIndex,
                                    onClick = { selectedIndex = index },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShapeHero(shape: RoundedPolygon, name: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MorphingShape(
                targetShape = shape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(140.dp),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/** Same Morph-driven redraw as the chat list's presence-shaped avatar frame — animates a spring
 * from whatever shape was showing to [targetShape] every time it changes. */
@Composable
private fun MorphingShape(targetShape: RoundedPolygon, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val morphProgress = remember { Animatable(1f) }
    val fromShape = remember { mutableStateOf(targetShape) }
    val toShape = remember { mutableStateOf(targetShape) }

    LaunchedEffect(targetShape) {
        if (toShape.value === targetShape) return@LaunchedEffect
        fromShape.value = toShape.value
        toShape.value = targetShape
        morphProgress.snapTo(0f)
        morphProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    val morph = remember(fromShape.value, toShape.value) { Morph(fromShape.value, toShape.value) }
    val progress = morphProgress.value
    val reusedPath = remember { Path() }
    val reusedMatrix = remember { android.graphics.Matrix() }

    Canvas(modifier = modifier) {
        reusedMatrix.reset()
        reusedMatrix.postScale(size.width, size.height)
        morph.toPath(progress, reusedPath)
        reusedPath.asAndroidPath().transform(reusedMatrix)
        clipPath(reusedPath) { drawRect(color) }
    }
}

@Composable
private fun ShapeCatalogItem(
    name: String,
    shape: RoundedPolygon,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Selection is expressed through shape change as well as color — the swatch itself morphs
    // toward a rounder silhouette when picked, not just a tint swap, echoing the same
    // "shape as state" language the filmstrip selection indicator uses elsewhere in the app.
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "shapeCatalogItemScale",
    )
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(background, RoundedCornerShape(16.dp))
                .padding(12.dp)
                .scale(scale),
            contentAlignment = Alignment.Center,
        ) {
            MorphingShape(targetShape = shape, color = tint, modifier = Modifier.size(36.dp))
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
