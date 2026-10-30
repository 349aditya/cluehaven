package com.cluehaven.puzzlegames.ui.home

import androidx.compose.foundation.Canvas
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
                                fontSize = if (dimension < 70.dp) 15.sp else 21.sp,
                                lineHeight = if (dimension < 70.dp) 15.sp else 21.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
