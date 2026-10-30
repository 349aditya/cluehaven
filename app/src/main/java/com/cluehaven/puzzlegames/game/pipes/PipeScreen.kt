package com.cluehaven.puzzlegames.game.pipes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun PipeScreen(padding: PaddingValues, goBack: () -> Unit) {
    val context = LocalContext.current
    val storage = remember { PipeStorage.get(context) }
    var size by remember { mutableStateOf(0) }
    var game by remember { mutableStateOf<PipeState?>(null) }
    var restart by remember { mutableStateOf(false) }
    LaunchedEffect(size) { game = if (size == 0) null else storage.load(size) }
    BackHandler { if (size != 0) size = 0 else goBack() }
    fun update(next: PipeState) { game = next; storage.save(next) }
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = { if (size != 0) size = 0 else goBack() }) { Text("‹ Back") }
        Text("Pipe Connect", style = MaterialTheme.typography.headlineSmall)
        val current = game
        if (size == 0) {
            Text("Rotate each tile clockwise to connect every pipe to the top-left source. Leave no open ends.")
            listOf(4, 6).forEach { boardSize ->
                OutlinedButton(onClick = { size = boardSize }, modifier = Modifier.fillMaxWidth()) {
                    Text("Play / resume $boardSize×$boardSize")
                }
            }
        } else if (current == null) CircularProgressIndicator()
        else {
            val connected = PipeEngine.connected(current.size, current.tiles)
            Text("${current.moves} rotations · ${connected.size}/${current.tiles.size} connected")
            val active = MaterialTheme.colorScheme.primary
            val inactive = MaterialTheme.colorScheme.onSurfaceVariant
            repeat(current.size) { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(current.size) { column ->
                        val index = row * current.size + column
                        val mask = current.tiles[index]
                        val color = if (index in connected) active else inactive
                        Box(Modifier.weight(1f).aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(.5.dp, MaterialTheme.colorScheme.outline)
                            .clickable(enabled = !current.isComplete) { update(PipeEngine.turn(current, index)) }) {
                            Canvas(Modifier.fillMaxSize()) {
                                val middle = center
                                val ends = listOf(Offset(middle.x, 0f), Offset(this.size.width, middle.y),
                                    Offset(middle.x, this.size.height), Offset(0f, middle.y))
                                repeat(4) { d -> if (mask and (1 shl d) != 0)
                                    drawLine(color, middle, ends[d], 7.dp.toPx(), StrokeCap.Round) }
                                drawCircle(color, if (index == 0) 8.dp.toPx() else 4.dp.toPx(), middle)
                            }
                        }
                    }
                }
            }
            if (current.isComplete) Text("Network complete!")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { update(PipeEngine.undo(current)) },
                    enabled = current.history.isNotEmpty() && !current.isComplete) { Text("Undo") }
                Button(onClick = { update(PipeEngine.hint(current)) },
                    enabled = current.hintsUsed == 0 && !current.isComplete) { Text("Hint") }
                OutlinedButton(onClick = { restart = true }) { Text("New") }
            }
        }
    }
    if (restart) AlertDialog(onDismissRequest = { restart = false }, title = { Text("New network?") },
        text = { Text("Your current board for this size will be replaced.") },
        confirmButton = { TextButton(onClick = { game?.let { update(PipeEngine.newGame(it.size)) }; restart = false }) { Text("New puzzle") } },
        dismissButton = { TextButton(onClick = { restart = false }) { Text("Keep board") } })
}
