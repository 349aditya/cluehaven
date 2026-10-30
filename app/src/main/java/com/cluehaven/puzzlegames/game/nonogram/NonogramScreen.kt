package com.cluehaven.puzzlegames.game.nonogram

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable
fun NonogramScreen(padding: PaddingValues, goBack: () -> Unit) {
    val context = LocalContext.current
    val storage = remember { NonogramStorage.get(context) }
    var game by remember { mutableStateOf<NonogramState?>(null) }
    var requested by remember { mutableStateOf<NonogramPuzzle?>(null) }
    var cross by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Map<String, NonogramState>>(emptyMap()) }
    val puzzles = remember { listOf(NonogramEngine.daily(LocalDate.now())) + NonogramEngine.puzzles }
    LaunchedEffect(Unit) { saved = puzzles.associate { it.id to storage.load(it) } }
    LaunchedEffect(requested) { game = requested?.let { storage.load(it) } }
    BackHandler { if (requested != null) requested = null else goBack() }
    fun update(next: NonogramState) {
        game = next
        saved = saved + (next.puzzle.id to next)
        storage.save(next)
    }
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = { if (requested != null) requested = null else goBack() }) { Text("‹ Back") }
        Text("Nonogram", style = MaterialTheme.typography.headlineSmall)
        val current = game
        if (requested == null) {
            Text("Reveal a picture using number clues. Each number is a run of filled squares; leave a gap between runs.")
            puzzles.forEachIndexed { index, puzzle ->
                OutlinedButton(onClick = { requested = puzzle; cross = false }, modifier = Modifier.fillMaxWidth()) {
                    Text((if (index == 0) "Daily puzzle" else "${puzzle.size}×${puzzle.size} · Picture $index") +
                        when { saved[puzzle.id]?.isComplete == true -> " · Solved"
                            saved[puzzle.id]?.cells?.any { it != 0 } == true -> " · Continue"
                            else -> "" })
                }
            }
        } else if (current == null) CircularProgressIndicator()
        else {
            val puzzle = current.puzzle
            Text("${puzzle.size}×${puzzle.size}")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Spacer(Modifier.weight(2f))
                puzzle.columns.forEach { clue ->
                    Text(clue.joinToString("\n"), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp)
                }
            }
            repeat(puzzle.size) { row ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(puzzle.rows[row].joinToString(" "), Modifier.weight(2f), textAlign = TextAlign.End, fontSize = 12.sp)
                    repeat(puzzle.size) { column ->
                        val cell = row * puzzle.size + column
                        Box(Modifier.weight(1f).aspectRatio(1f)
                            .background(if (current.cells[cell] == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .border(.5.dp, MaterialTheme.colorScheme.outline)
                            .clickable(enabled = !current.isComplete) { update(NonogramEngine.mark(current, cell, cross)) },
                            contentAlignment = Alignment.Center) {
                            if (current.cells[cell] == 2) Text("×")
                        }
                    }
                }
            }
            if (current.isComplete) {
                Text("Picture complete!")
                Button(onClick = { requested = null }) { Text("Choose another") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !cross, onClick = { cross = false }, label = { Text("Fill") })
                    FilterChip(selected = cross, onClick = { cross = true }, label = { Text("Cross") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { update(NonogramEngine.undo(current)) }, enabled = current.history.isNotEmpty()) { Text("Undo") }
                    Button(onClick = { update(NonogramEngine.hint(current)) }, enabled = current.hintsUsed == 0) { Text("Hint · 1 free") }
                }
            }
        }
    }
}
