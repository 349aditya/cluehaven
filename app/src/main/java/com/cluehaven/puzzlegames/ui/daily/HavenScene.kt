package com.cluehaven.puzzlegames.ui.daily

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.cluehaven.puzzlegames.data.HavenProgress
import com.cluehaven.puzzlegames.game.daily.DailyGame
import com.cluehaven.puzzlegames.ui.LocalReducedMotion
import com.cluehaven.puzzlegames.ui.theme.*

/** Saved growth is rendered directly. Animations never trigger persistence or rewards. */
@Composable
fun HavenScene(progress: HavenProgress, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    Box(modifier.fillMaxWidth().aspectRatio(1.32f).clip(RoundedCornerShape(18.dp))
        .background(if (dark) Color(0xFF17383E) else Color(0xFFE8F1E7))
        .clearAndSetSemantics { contentDescription = progress.sceneDescription }) {
        if (LocalReducedMotion.current) GardenCanvas(progress.completed, dark)
        else Crossfade(progress.completed, animationSpec = tween(350), label = "Saved garden growth") {
            GardenCanvas(it, dark)
        }
    }
}

@Composable
private fun GardenCanvas(grown: Set<DailyGame>, dark: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        withTransform({ scale(size.width / 330f, size.height / 250f, pivot = Offset.Zero) }) {
            val ground = if (dark) Color(0xFF214B4C) else Color(0xFFCEE3D4)
            val outline = if (dark) Mint.copy(alpha = .58f) else Teal.copy(alpha = .52f)
            val dashed = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
            val hill = Path().apply {
                moveTo(0f, 190f); cubicTo(65f, 170f, 125f, 176f, 182f, 190f)
                cubicTo(230f, 200f, 287f, 175f, 330f, 170f)
                lineTo(330f, 250f); lineTo(0f, 250f); close()
            }
            drawPath(hill, ground)
            drawCircle(Gold, 19f, Offset(282f, 45f))
            drawCircle(Gold.copy(alpha = .08f), 27f, Offset(282f, 45f))
            // The house and sun are the permanent setting; the five earned items are below.
            drawRoundRect(if (dark) Color(0xFF215259) else Teal, Offset(36f, 118f), Size(72f, 80f), CornerRadius(2f))
            val roof = Path().apply { moveTo(24f, 122f); lineTo(72f, 82f); lineTo(120f, 122f); close() }
            drawPath(roof, if (dark) Color(0xFF316C71) else Color(0xFF38868A))
            drawRoundRect(Cream, Offset(61f, 159f), Size(22f, 39f), CornerRadius(5f))
            drawRoundRect(Gold, Offset(87f, 138f), Size(13f, 13f), CornerRadius(3f))
            drawLine(if (dark) Color(0xFF15383E) else Color(0xFFA5C5B4), Offset(28f, 199f), Offset(115f, 199f), 3f, StrokeCap.Round)

            if (DailyGame.SUDOKU in grown) tree() else tree(outline, dashed)
            if (DailyGame.NONOGRAM in grown) flowers() else flowers(outline, dashed)
            if (DailyGame.PIPES in grown) water() else water(outline, dashed)
            if (DailyGame.TILE_MERGE in grown) lanterns() else lanterns(outline, dashed)
            if (DailyGame.BLOCKS in grown) gardenBed() else gardenBed(outline, dashed)
        }
    }
}

private fun DrawScope.tree(ghost: Color? = null, dashed: Stroke? = null) {
    val trunk = Offset(181f, 124f)
    if (ghost != null) {
        drawRoundRect(ghost, trunk, Size(9f, 75f), CornerRadius(3f), style = requireNotNull(dashed))
        listOf(Offset(185f, 106f) to 29f, Offset(163f, 125f) to 22f, Offset(207f, 125f) to 22f).forEach { (point, radius) ->
            drawCircle(ghost, radius, point, style = dashed)
        }
    } else {
        drawOval(Teal.copy(alpha = .14f), Offset(160f, 193f), Size(50f, 9f))
        drawRoundRect(Color(0xFFC69F63), trunk, Size(9f, 75f), CornerRadius(3f))
        drawCircle(Mint, 29f, Offset(185f, 106f))
        drawCircle(Color(0xFF7BBDAC), 22f, Offset(163f, 125f))
        drawCircle(Color(0xFF83C4B2), 22f, Offset(207f, 125f))
        drawOval(Color.White.copy(alpha = .14f), Offset(172f, 84f), Size(17f, 11f))
    }
}

private fun DrawScope.flowers(ghost: Color? = null, dashed: Stroke? = null) {
    listOf(128f to 179f, 143f to 170f, 157f to 182f).forEachIndexed { index, (x, y) ->
        if (ghost != null) {
            drawPath(Path().apply { moveTo(x, 198f); lineTo(x, y + 7f) }, ghost, style = requireNotNull(dashed))
            drawCircle(ghost, 7f, Offset(x, y), style = dashed)
        } else {
            drawLine(Mint, Offset(x, 198f), Offset(x, y), 2.5f, StrokeCap.Round)
            drawOval(Color(0xFF79BBA6), Offset(x - 7f, y + 9f), Size(7f, 4f))
            val petal = listOf(Coral, Cream, Gold)[index]
            listOf(Offset(-4f, 0f), Offset(4f, 0f), Offset(0f, -4f), Offset(0f, 4f)).forEach {
                drawCircle(petal, 4f, Offset(x, y) + it)
            }
            drawCircle(if (index == 2) Cream else Gold, 2.5f, Offset(x, y))
        }
    }
}

private fun DrawScope.water(ghost: Color? = null, dashed: Stroke? = null) {
    val course = Path().apply {
        moveTo(18f, 224f); lineTo(111f, 224f); lineTo(111f, 211f)
        lineTo(211f, 211f); lineTo(211f, 228f); lineTo(313f, 228f)
    }
    if (ghost != null) drawPath(course, ghost, style = requireNotNull(dashed))
    else {
        drawPath(course, Color(0xFF103B40), style = Stroke(14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(course, Mint, style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(course, Color(0xFFD9F5E9).copy(alpha = .45f), style = Stroke(1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.lanterns(ghost: Color? = null, dashed: Stroke? = null) {
    val line = Path().apply {
        moveTo(240f, 155f); lineTo(240f, 111f)
        cubicTo(256f, 120f, 274f, 122f, 298f, 112f); lineTo(298f, 154f)
    }
    if (ghost != null) {
        drawPath(line, ghost, style = requireNotNull(dashed))
        listOf(250f to 132f, 286f to 133f).forEach { (x, y) ->
            drawRoundRect(ghost, Offset(x - 6f, y - 8f), Size(12f, 17f), CornerRadius(4f), style = dashed)
        }
    } else {
        drawPath(line, Color(0xFFB89C66), style = Stroke(2.4f, cap = StrokeCap.Round))
        listOf(250f to 132f, 286f to 133f).forEach { (x, y) ->
            drawLine(Color(0xFFB89C66), Offset(x, y - 14f), Offset(x, y - 7f), 1.5f)
            drawCircle(Gold.copy(alpha = .12f), 14f, Offset(x, y))
            drawRoundRect(Gold, Offset(x - 6f, y - 8f), Size(12f, 17f), CornerRadius(4f))
            drawLine(Cream, Offset(x, y - 4f), Offset(x, y + 4f), 2f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.gardenBed(ghost: Color? = null, dashed: Stroke? = null) {
    val origin = Offset(232f, 177f)
    val box = Size(70f, 24f)
    if (ghost != null) drawRoundRect(ghost, origin, box, CornerRadius(6f), style = requireNotNull(dashed))
    else {
        drawRoundRect(Color(0xFFB98E5A), origin + Offset(0f, 3f), box, CornerRadius(6f))
        drawRoundRect(Color(0xFF745944), origin, box, CornerRadius(6f))
        drawRoundRect(Color(0xFF493F34), origin + Offset(4f, 4f), Size(62f, 15f), CornerRadius(4f))
        repeat(4) { seedling ->
            val x = 244f + seedling * 15f
            drawLine(Mint, Offset(x, 193f), Offset(x, 181f), 1.8f, StrokeCap.Round)
            drawOval(Mint, Offset(x - 7f, 180f), Size(7f, 5f))
            drawOval(Color(0xFF76BBA4), Offset(x, 176f), Size(7f, 5f))
        }
    }
}
