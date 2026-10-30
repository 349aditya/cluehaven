package com.cluehaven.puzzlegames.game.pipes

import androidx.activity.compose.BackHandler
import com.cluehaven.puzzlegames.ui.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import com.cluehaven.puzzlegames.ui.theme.Gold
import com.cluehaven.puzzlegames.ui.theme.Coral
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cluehaven.puzzlegames.data.playRecord

@Composable
fun PipeScreen(padding: PaddingValues, initialSize: Int = 0, goBack: () -> Unit) {
    val feedback = com.cluehaven.puzzlegames.ui.LocalGameFeedback.current
    val context = LocalContext.current
    val storage = remember(context) { PipeStorage.get(context) }
    var boardSize by rememberSaveable { mutableStateOf(initialSize.takeIf { it in setOf(4, 6) } ?: 0) }
    var state by remember { mutableStateOf<PipeState?>(null) }
    var restart by remember { mutableStateOf(false) }
    LaunchedEffect(boardSize) {
        state = null
        if (boardSize != 0) state = storage.load(boardSize)
    }
    BackHandler { if (boardSize != 0) boardSize = 0 else goBack() }
    fun update(next: PipeState) {
        feedback()
        state = next
        storage.save(next)
    }
    val current = state
    if (boardSize == 0) {
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GameHeader("Pipe Connect", goBack)
            Text("Bring the whole network to life.", style = MaterialTheme.typography.titleLarge)
            Text("Rotate each tile to connect the network. No open ends, no wrapping edges.")
            listOf(4, 6).forEach { size ->
                Card(onClick = { boardSize = size }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.cluehaven.puzzlegames.ui.home.PipeArtwork(56.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("$size×$size network", fontWeight = FontWeight.Bold)
                            Text(if (size == 4) "A little moment of focus" else "A bigger connection", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    } else if (current == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else {
        val connected = remember(current.tiles) { PipeEngine.connected(current.size, current.tiles) }
        PuzzlePage(padding, "Pipe Connect", { boardSize = 0 }, footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PuzzleAction("Undo", "undo", Modifier.weight(1f), enabled = current.history.isNotEmpty() && !current.isComplete) {
                    update(PipeEngine.undo(current))
                }
                PuzzleAction("Hint", "hint", Modifier.weight(1f), enabled = current.hintsUsed == 0 && !current.isComplete) {
                    update(PipeEngine.hint(current))
                }
                PuzzleAction("New", "new", Modifier.weight(1f), primary = true) { restart = true }
            }
        }) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                StatusPill("${current.size}×${current.size} · ${current.moves} rotations")
            }
            PlayTimerLine(current.playRecord(), current.moves > 0 || current.hintsUsed > 0 || current.history.isNotEmpty(),
                current.isComplete, paused = restart)
            PipeBoard(current, ::update)
            Text("${connected.size} / ${current.tiles.size} connected", style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(progress = { connected.size.toFloat() / current.tiles.size },
                modifier = Modifier.fillMaxWidth().height(8.dp), strokeCap = StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.outlineVariant)
            if (current.isComplete) CompletionCard("Network complete!", "Every pipe is connected.", "New network") { restart = true }
        }
    }
    if (restart) AlertDialog(onDismissRequest = { restart = false }, title = { Text("Generate a new network?") },
        text = { Text("This replaces your current board for this size.") },
        confirmButton = { TextButton(onClick = {
            state?.let { update(PipeEngine.newGame(it.size)) }; restart = false
        }) { Text("New puzzle") } },
        dismissButton = { TextButton(onClick = { restart = false }) { Text("Keep board") } })
}

@Composable
private fun PipeBoard(current: PipeState, update: (PipeState) -> Unit) {
    val connected = remember(current.tiles) { PipeEngine.connected(current.size, current.tiles) }
            val activeColor = MaterialTheme.colorScheme.primary
            val inactiveColor = MaterialTheme.colorScheme.outline
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                repeat(current.size) { row ->
                    Row {
                        repeat(current.size) { column ->
                            val index = row * current.size + column
                            val mask = current.tiles[index]
                            val color by animateColorAsState(if (index in connected) activeColor else inactiveColor,
                                tween(if (LocalReducedMotion.current) 0 else 200), label = "Pipe connection")
                            val ports = listOf("north", "east", "south", "west").filterIndexed { d, _ -> mask and (1 shl d) != 0 }
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(1.dp)
                                .background(if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                .border(0.5.dp, MaterialTheme.colorScheme.outline)
                                .clickable(enabled = !current.isComplete) { update(PipeEngine.turn(current, index)) }
                                .semantics { contentDescription = "Row ${row + 1}, column ${column + 1}, " +
                                    "ports ${ports.joinToString()}, " + (if (index in connected) "connected" else "disconnected") +
                                    if (index == 0) ", source" else "" }) {
                                Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                                    val center = Offset(size.width / 2, size.height / 2)
                                    val ends = listOf(Offset(center.x, -4.dp.toPx()), Offset(size.width + 4.dp.toPx(), center.y),
                                        Offset(center.x, size.height + 4.dp.toPx()), Offset(-4.dp.toPx(), center.y))
                                    repeat(4) { d -> if (mask and (1 shl d) != 0)
                                        drawLine(color, center, ends[d], 10.dp.toPx(), StrokeCap.Butt) }
                                    drawCircle(if (index == 0) Gold else color, if (index == 0) 8.dp.toPx() else 5.dp.toPx(), center)
                                    repeat(4) { d ->
                                        val next = PipeEngine.neighbor(current.size, index, d)
                                        if (mask and (1 shl d) != 0 &&
                                            (next == null || current.tiles[next] and (1 shl ((d + 2) % 4)) == 0)) {
                                            drawCircle(Coral, 3.dp.toPx(), center + (ends[d] - center) * .73f)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

}
