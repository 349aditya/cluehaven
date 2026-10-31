package com.cluehaven.puzzlegames.game.blocks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cluehaven.puzzlegames.data.playRecord
import com.cluehaven.puzzlegames.game.daily.DailyGameSession
import com.cluehaven.puzzlegames.ui.*

@Composable
fun BlockScreen(padding: PaddingValues, daily: DailyGameSession<BlockState>? = null, goBack: () -> Unit) {
    val context = LocalContext.current
    val storage = remember(context) { BlockStorage.get(context) }
    val feedback = LocalGameFeedback.current
    var loaded by remember { mutableStateOf<BlockState?>(null) }
    var restart by remember { mutableStateOf(false) }
    var showOver by remember { mutableStateOf(false) }
    LaunchedEffect(storage, daily != null) {
        if (daily == null) loaded = storage.load()
        showOver = (daily?.state ?: loaded)?.isGameOver == true
    }
    val game = daily?.state ?: loaded ?: run {
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val gameOver = remember(game.board, game.tray) { game.isGameOver }
    var selected by remember(game.runId) { mutableIntStateOf(-1) }
    val interaction = remember(game.runId) { BlockInteraction() }
    var clear by remember(game.runId) { mutableStateOf<BlockClearEffect?>(null) }
    var rejection by remember(game.runId) { mutableStateOf<BlockRejection?>(null) }
    var message by remember(game.runId) { mutableStateOf("Drag a block, or select one and tap the board.") }
    BackHandler {
        when {
            interaction.drag != null -> interaction.drag = null
            restart -> restart = false
            showOver -> showOver = false
            else -> goBack()
        }
    }
    fun update(next: BlockState) {
        if (next == game) return
        feedback()
        loaded = next
        if (daily != null) daily.update(next) else storage.save(next)
        selected = -1
        interaction.drag = null
        clear = null
        rejection = null
        showOver = next.isGameOver
        message = when {
            next.lastCleared.isNotEmpty() -> "${next.lastCleared.size} squares cleared" + if (next.combo > 1) " · Combo ×${next.combo}!" else "!"
            next.undoUsed && !game.undoUsed -> "Last placement restored · Undo used for this run"
            else -> "Drag a block, or select one and tap the board."
        }
    }
    fun restartRun() {
        showOver = false
        if (daily != null) daily.retry() else update(BlockEngine.newGame(bestScore = game.bestScore))
    }
    fun place(slot: Int, row: Int, column: Int) {
        val shape = game.tray.getOrNull(slot)?.let(BlockEngine::shape) ?: return
        val next = BlockEngine.place(game, slot, row, column)
        if (next == game) {
            rejection = BlockRejection(System.nanoTime(), shape, BlockCell(row, column))
            message = "That block doesn't fit there. Try another space."
        } else {
            // Keep cleared colours even when undo has been used and no snapshot remains.
            val placedIndices = shape.cells.map { (row + it.row) * 8 + column + it.column }.toSet()
            val tiles = next.lastCleared.associateWith { if (it in placedIndices) shape.color else game.board[it] }
            update(next)
            if (tiles.isNotEmpty()) clear = BlockClearEffect(next.moves, tiles)
        }
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { interaction.rootOrigin = it.boundsInRoot().topLeft }) {
        PuzzlePage(padding, "Block Garden", goBack, footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PuzzleAction("Undo", "undo", Modifier.weight(1f), enabled = !game.undoUsed && game.previous != null) {
                    update(BlockEngine.undo(game))
                }
                PuzzleAction(if (daily != null) "Retry" else "New run", "new", Modifier.weight(1f), primary = true) { restart = true }
            }
        }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BlockScore("Score", game.score, Modifier.weight(1f))
                BlockScore("Best", game.bestScore, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                StatusPill("8×8 · ${game.lines} lines cleared")
                if (game.combo > 1) Text("Combo ×${game.combo}", color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
            BlockBoard(game, interaction, clear, rejection,
                Modifier.widthIn(max = 440.dp).fillMaxWidth().align(Alignment.CenterHorizontally)) { row, column ->
                if (selected < 0) message = "Select a block below, then tap its top-left position."
                else place(selected, row, column)
            }
            BlockTray(game, selected, interaction, select = { slot ->
                selected = slot
                rejection = null
                message = "Tap the board at the block's top-left position, or drag it into place."
            }, drop = { slot, cell -> place(slot, cell.row, cell.column) })
            Text(if (gameOver) "No remaining blocks fit · Run complete" else message,
                Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            PlayTimerLine(game.playRecord(), game.moves > 0 || game.undoUsed, gameOver, paused = restart)
        }
        BlockDragOverlay(game, interaction, Modifier.matchParentSize())
    }
    if (restart) AlertDialog(onDismissRequest = { restart = false }, title = { Text(if (daily != null) "Retry today’s challenge?" else "Start a new garden?") },
        text = { Text(if (daily != null) "The same starting board will be restored." else "Your current board will be replaced. Your best score stays.") },
        confirmButton = { TextButton(onClick = { restart = false; restartRun() }) { Text(if (daily != null) "Retry" else "New run") } },
        dismissButton = { TextButton(onClick = { restart = false }) { Text("Keep playing") } })
    if (showOver) AlertDialog(onDismissRequest = { showOver = false }, title = { Text(if (daily != null) "Run ended" else "Garden complete") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("No remaining blocks fit. Score ${game.score} · ${game.lines} lines cleared.")
                Text("Best score ${game.bestScore}", fontWeight = FontWeight.Bold)
                if (!game.undoUsed && game.previous != null) OutlinedButton(onClick = { update(BlockEngine.undo(game)) }) {
                    Text("Undo last placement")
                }
            }
        },
        confirmButton = { TextButton(onClick = { showOver = false; restartRun() }) { Text(if (daily != null) "Retry" else "New run") } },
        dismissButton = { TextButton(onClick = { showOver = false }) { Text("View board") } })
}

@Composable
private fun BlockScore(label: String, score: Int, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(score.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
