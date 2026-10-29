package com.cluehaven.puzzlegames.game.tilemerge

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cluehaven.puzzlegames.ui.theme.Coral
import com.cluehaven.puzzlegames.ui.theme.Cream
import com.cluehaven.puzzlegames.ui.theme.DarkTeal
import com.cluehaven.puzzlegames.ui.theme.Gold
import com.cluehaven.puzzlegames.ui.theme.Mint
import com.cluehaven.puzzlegames.ui.theme.Teal
import kotlin.math.abs

@Composable
fun TileMergeScreen(padding: PaddingValues, goBack: () -> Unit) {
    val context = LocalContext.current
    val storage = remember(context) { TileMergeStorage(context) }
    var game by remember { mutableStateOf(storage.load()) }
    var showRestartDialog by remember { mutableStateOf(false) }

    fun update(next: TileMergeState) {
        game = next
        storage.save(next)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = goBack, contentPadding = PaddingValues(0.dp)) {
                Text("‹  Back", fontSize = 18.sp)
            }
            Text(
                "Tile Merge",
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScoreCard("SCORE", game.score, Modifier.weight(1f))
            ScoreCard("BEST", game.bestScore, Modifier.weight(1f))
        }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val boardSize = minOf(maxWidth, maxHeight)
            Box(
                modifier = Modifier
                    .size(boardSize)
                    .align(Alignment.Center)
                    .background(Color(0xFFD8DED8), RoundedCornerShape(18.dp))
                    .padding(7.dp)
                    .pointerInput(game) {
                    var dragX = 0f
                    var dragY = 0f
                    val threshold = 24.dp.toPx()
                    detectDragGestures(
                        onDragStart = { dragX = 0f; dragY = 0f },
                        onDragEnd = {
                            if (maxOf(abs(dragX), abs(dragY)) > threshold) {
                                val direction = if (abs(dragX) > abs(dragY)) {
                                    if (dragX > 0) MoveDirection.RIGHT else MoveDirection.LEFT
                                } else {
                                    if (dragY > 0) MoveDirection.DOWN else MoveDirection.UP
                                }
                                val next = TileMergeEngine.move(game, direction)
                                if (next != game) update(next)
                            }
                        },
                        onDrag = { change, amount ->
                            dragX += amount.x
                            dragY += amount.y
                            change.consume()
                        },
                    )
                    },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in 0..3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (column in 0..3) {
                                val value = game.board[row * 4 + column]
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .background(tileColor(value), RoundedCornerShape(12.dp))
                                        .semantics {
                                            contentDescription = if (value == 0) "Empty tile" else "Tile $value"
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (value != 0) {
                                        Text(
                                            value.toString(),
                                            color = DarkTeal,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = when {
                                                value >= 1024 -> 20.sp
                                                value >= 128 -> 23.sp
                                                else -> 27.sp
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            when {
                game.isGameOver -> "No moves left · Start a new run"
                game.reached2048 -> "You reached 2048! Keep merging"
                else -> "Swipe tiles to merge · Goal: 2048"
            },
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { update(TileMergeEngine.undo(game)) },
                enabled = game.undosUsed == 0 && game.history.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text(if (game.undosUsed == 0) "Undo · 1 free" else "Undo used") }
            OutlinedButton(
                onClick = { showRestartDialog = true },
                modifier = Modifier.weight(1f),
            ) { Text("New run") }
        }
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("Start a new run?") },
            text = { Text("Your current board will be replaced. Your best score stays.") },
            confirmButton = {
                TextButton(onClick = {
                    update(TileMergeEngine.newGame(bestScore = game.bestScore))
                    showRestartDialog = false
                }) { Text("New run") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) { Text("Keep playing") }
            },
        )
    }
}

@Composable
private fun ScoreCard(label: String, score: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(13.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(score.toString(), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
        }
    }
}

private fun tileColor(value: Int): Color = when (value) {
    0 -> Color(0xFFE8EDE8)
    2 -> Cream
    4 -> Color(0xFFEAD6AA)
    8 -> Gold
    16 -> Coral
    32 -> Mint
    64 -> Color(0xFF68B6A7)
    128 -> Color(0xFF7EC5CC)
    256 -> Color(0xFF9DB7E6)
    512 -> Color(0xFFBBA7D9)
    1024 -> Color(0xFFE3ABBE)
    else -> Color(0xFFF2D27A)
}
