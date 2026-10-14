package eu.siacs.conversations.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntSize
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/** Shapes between the hearts in one turn. With the heart at both ends, it comes up every fifth shape. */
private const val SHAPES_PER_TURN = 4
private const val MS_PER_MORPH = 1300

/**
 * The avatar on someone's birthday: its outline keeps morphing from shape to shape out of the
 * whole Material shape set while it turns once round. A turn always starts and ends upright on
 * the heart, and the heart is the only moment the avatar is upright, so it "comes round" to a
 * heart again and again. The picture itself stays upright, only the outline turns.
 */
@Composable
fun BirthdayAvatar(avatar: ImageBitmap?, fallback: Color, modifier: Modifier = Modifier) {
    val heart = remember { MATERIAL_SHAPE_CATALOG.first { it.first == "Heart" }.second }
    val others = remember { MATERIAL_SHAPE_CATALOG.filter { it.first != "Heart" }.map { it.second } }
    var turn by remember { mutableStateOf<List<RoundedPolygon>>(listOf(heart, heart)) }
    val position = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            turn = listOf(heart) + others.shuffled().take(SHAPES_PER_TURN) + listOf(heart)
            position.snapTo(0f)
            val morphs = turn.size - 1
            position.animateTo(
                morphs.toFloat(),
                tween(durationMillis = morphs * MS_PER_MORPH, easing = LinearEasing),
            )
        }
    }

    val morphs = remember(turn) { turn.zipWithNext { a, b -> Morph(a, b) } }
    val path = remember { Path() }
    val matrix = remember { android.graphics.Matrix() }

    Canvas(modifier) {
        val count = morphs.size
        val t = position.value.coerceIn(0f, count.toFloat())
        val index = t.toInt().coerceAtMost(count - 1)
        // Each morph eases in and out, so the avatar rests briefly on every shape.
        val progress = FastOutSlowInEasing.transform((t - index).coerceIn(0f, 1f))
        val angle = 360f * t / count
        val side = size.width
        matrix.reset()
        matrix.postScale(side * 0.82f, side * 0.82f)
        matrix.postTranslate(side * 0.09f, side * 0.09f)
        matrix.postRotate(angle, side / 2f, side / 2f)
        morphs[index].toPath(progress, path)
        path.asAndroidPath().transform(matrix)
        clipPath(path) {
            if (avatar == null) {
                drawRect(fallback)
            } else {
                drawImage(avatar, dstSize = IntSize(side.toInt(), side.toInt()))
            }
        }
    }
}
