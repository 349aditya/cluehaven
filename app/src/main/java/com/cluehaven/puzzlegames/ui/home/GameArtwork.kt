package com.cluehaven.puzzlegames.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cluehaven.puzzlegames.ui.theme.Coral
import com.cluehaven.puzzlegames.ui.theme.Cream
import com.cluehaven.puzzlegames.ui.theme.DarkTeal
import com.cluehaven.puzzlegames.ui.theme.Gold
import com.cluehaven.puzzlegames.ui.theme.Teal

private val sudokuPreview = listOf(
    "...5..",
    "..6123",
    "23..61",
    "56.234",
    "...6.2",
    "61....",
)

@Composable
fun PipeArtwork(dimension: Dp) {
    Canvas(Modifier.size(dimension).clearAndSetSemantics { }) {
        val unit = size.minDimension / 100f
        val board = Size(96 * unit, 94 * unit)
        drawRoundRect(DarkTeal.copy(alpha = 0.14f), Offset(2 * unit, 6 * unit),
            board, CornerRadius(13 * unit))
        drawRoundRect(DarkTeal, Offset(2 * unit, 2 * unit), board, CornerRadius(13 * unit))
        repeat(3) { row ->
            repeat(3) { column ->
                drawRoundRect(Color(0xFFEAF4EF),
                    Offset((8 + column * 29) * unit, (8 + row * 29) * unit),
                    Size(26 * unit, 26 * unit), CornerRadius(5 * unit))
            }
        }
        fun center(index: Int) = Offset((21 + index % 3 * 29) * unit,
            (21 + index / 3 * 29) * unit)
        val route = listOf(0, 1, 4, 3, 6, 7, 8, 5, 2)
        val pipe = Path().apply {
            moveTo(center(0).x, center(0).y)
            route.drop(1).forEach { lineTo(center(it).x, center(it).y) }
        }
        drawPath(pipe, DarkTeal, style = Stroke(11 * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(pipe, Teal, style = Stroke(7 * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(pipe, Color.White.copy(alpha = 0.35f),
            style = Stroke(2 * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        listOf(0 to Gold, 2 to Coral).forEach { (index, color) ->
            drawCircle(DarkTeal, 9 * unit, center(index))
            drawCircle(color, 6 * unit, center(index))
            drawCircle(Color.White.copy(alpha = 0.8f), 2 * unit,
                center(index) - Offset(1.5f * unit, 1.5f * unit))
        }
    }
}

@Composable
fun NonogramArtwork(size: Dp) {
    val picture = listOf("00100", "01110", "11111", "01110", "00100")
    Canvas(Modifier.size(size).clearAndSetSemantics { }) {
        val cell = this.size.minDimension / 5
        picture.forEachIndexed { row, line ->
            line.forEachIndexed { column, value ->
                drawRect(if (value == '1') Teal else Cream,
                    Offset(column * cell + 1.dp.toPx(), row * cell + 1.dp.toPx()),
                    Size(cell - 2.dp.toPx(), cell - 2.dp.toPx()))
            }
        }
    }
}

@Composable
fun SudokuArtwork(dimension: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(dimension)
            .background(Color.White, RoundedCornerShape(9.dp))
            .padding(4.dp)
            .clearAndSetSemantics { },
    ) {
        Column(Modifier.fillMaxSize()) {
            for (row in 0 until 6) {
                Row(Modifier.weight(1f)) {
                    for (column in 0 until 6) {
                        val digit = sudokuPreview[row][column]
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            if (digit != '.') {
                                Text(
                                    "$digit",
                                    color = DarkTeal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (dimension < 70.dp) 7.sp else 11.sp,
                                    lineHeight = if (dimension < 70.dp) 7.sp else 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
        Canvas(Modifier.matchParentSize()) {
            for (line in 1 until 6) {
                val x = this.size.width * line / 6f
                val y = this.size.height * line / 6f
                val verticalStrong = line % 3 == 0
                val horizontalStrong = line % 2 == 0
                drawLine(
                    if (verticalStrong) Teal else Color(0xFFB6C7C4),
                    androidx.compose.ui.geometry.Offset(x, 0f),
                    androidx.compose.ui.geometry.Offset(x, this.size.height),
                    if (verticalStrong) 1.8.dp.toPx() else 0.6.dp.toPx(),
                )
                drawLine(
                    if (horizontalStrong) Teal else Color(0xFFB6C7C4),
                    androidx.compose.ui.geometry.Offset(0f, y),
                    androidx.compose.ui.geometry.Offset(this.size.width, y),
                    if (horizontalStrong) 1.8.dp.toPx() else 0.6.dp.toPx(),
                )
            }
        }
    }
}

@Composable
fun TileMergeArtwork(dimension: Dp, modifier: Modifier = Modifier) {
    val tiles = listOf(2, 2, 4, 8)
    Box(
        modifier.size(dimension)
            .background(Teal, RoundedCornerShape(10.dp))
            .padding(5.dp)
            .clearAndSetSemantics { },
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in 0..1) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (column in 0..1) {
                        val value = tiles[row * 2 + column]
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .background(
                                    when (value) {
                                        2 -> Cream
                                        4 -> Gold
                                        else -> Coral
                                    },
                                    RoundedCornerShape(6.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$value",
                                color = DarkTeal,
                                fontWeight = FontWeight.Bold,
                                fontSize = (dimension.value * .25f).sp,
                                lineHeight = (dimension.value * .25f).sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
