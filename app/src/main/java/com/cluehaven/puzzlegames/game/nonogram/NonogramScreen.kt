package com.cluehaven.puzzlegames.game.nonogram

import androidx.activity.compose.BackHandler
import com.cluehaven.puzzlegames.ui.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import com.cluehaven.puzzlegames.data.playRecord

@Composable
fun NonogramScreen(padding: PaddingValues, initialId: String? = null, goBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(initialId?.takeUnless { it.startsWith("endless-") }) }
    var endlessResume by rememberSaveable { mutableStateOf(initialId?.takeIf { it.startsWith("endless-") }) }
    var showEndless by rememberSaveable { mutableStateOf(endlessResume != null) }
    if (showEndless) {
        EndlessNonogramScreen(padding, endlessResume) { showEndless = false; endlessResume = null }
        return
    }
    val feedback = com.cluehaven.puzzlegames.ui.LocalGameFeedback.current
    val context = LocalContext.current
    val storage = remember(context) { NonogramStorage.get(context) }
    var state by remember { mutableStateOf<NonogramState?>(null) }
    var saved by remember { mutableStateOf<Map<String, NonogramState>>(emptyMap()) }
    var filterSize by rememberSaveable { mutableStateOf(5) }
    var selectedCell by rememberSaveable { mutableStateOf(-1) }
    var cross by rememberSaveable { mutableStateOf(false) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    // Refresh the chooser when the calendar changes, keeping any open puzzle intact.
    LaunchedEffect(Unit) {
        while (true) { today = LocalDate.now(); delay(30_000) }
    }
    val daily = remember(today) { NonogramEngine.daily(today) }
    val puzzles = remember(daily) { listOf(daily) + NonogramEngine.puzzles }
    LaunchedEffect(puzzles) { saved = puzzles.associate { it.id to storage.load(it) } }
    LaunchedEffect(selectedId) {
        state = null
        val id = selectedId ?: return@LaunchedEffect
        val puzzle = when {
            id.startsWith("daily-") -> runCatching {
                NonogramEngine.daily(LocalDate.parse(id.removePrefix("daily-")))
            }.getOrNull()
            else -> NonogramEngine.puzzles.firstOrNull { it.id == id }
        }
        if (puzzle == null) selectedId = null else state = storage.load(puzzle)
    }
    BackHandler { if (selectedId != null) selectedId = null else goBack() }

    fun update(next: NonogramState) {
        feedback()
        state = next
        saved = saved + (next.puzzle.id to next)
        storage.save(next)
    }

    if (selectedId != null) {
        val current = state
        if (current == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        } else NonogramPlay(current, padding, cross, selectedCell,
            back = { selectedId = null }, selectMode = { cross = it },
            mark = { selectedCell = it; update(NonogramEngine.mark(current, it, cross)) },
            update = ::update)
        return
    }
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GameHeader("Nonogram") { if (selectedId != null) selectedId = null else goBack() }
        val current = state
        if (selectedId == null) {
            Text("Reveal a picture using the number clues.", Modifier.fillMaxWidth())
            Card(onClick = { selectedId = daily.id }, colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("Daily puzzle  →", fontWeight = FontWeight.Bold)
                    Text("$today · " + if (saved[daily.id]?.isComplete == true) "Solved ✓" else "Ready when you are")
                }
            }
            Card(onClick = { showEndless = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    com.cluehaven.puzzlegames.ui.home.NonogramArtwork(44.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Endless", fontWeight = FontWeight.Bold)
                        Text("Fresh pictures, solved with logic", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", color = MaterialTheme.colorScheme.primary)
                }
            }
            Text("Picture pack", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(5, 10).forEach { size ->
                    FilterChip(selected = filterSize == size, onClick = { filterSize = size },
                        label = { Text("$size×$size") })
                }
            }
            NonogramEngine.puzzles.filter { it.size == filterSize }.chunked(3).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { puzzle ->
                        val progress = saved[puzzle.id]
                        Card(onClick = { selectedId = puzzle.id; cross = false; selectedCell = -1 },
                            modifier = Modifier.weight(1f)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                val filledColor = MaterialTheme.colorScheme.primary
                                val emptyColor = MaterialTheme.colorScheme.outlineVariant
                                Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                    val unit = size.minDimension / puzzle.size
                                    val left = (size.width - unit * puzzle.size) / 2
                                    repeat(puzzle.size * puzzle.size) { index ->
                                        drawRect(if (progress?.cells?.get(index) == 1) filledColor else emptyColor,
                                            Offset(left + index % puzzle.size * unit, index / puzzle.size * unit),
                                            Size(unit * .85f, unit * .85f))
                                    }
                                }
                                Text(if (progress?.isComplete == true) puzzle.title else "Picture ${puzzle.number}",
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(puzzle.difficulty.label, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(when {
                                    progress?.isComplete == true -> "✓ Solved"
                                    progress?.cells?.any { it != 0 } == true -> "◷ In progress"
                                    else -> "○ New"
                                }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    repeat(3 - pair.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            val total = NonogramEngine.puzzles.count { it.size == filterSize }
            val solved = NonogramEngine.puzzles.count { it.size == filterSize && saved[it.id]?.isComplete == true }
            Text("$solved / $total solved in ${filterSize}×$filterSize · ${NonogramEngine.puzzles.size} puzzles total\nProgress saves automatically on this device.",
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun NonogramPlay(state: NonogramState, padding: PaddingValues, cross: Boolean, selected: Int,
    back: () -> Unit, selectMode: (Boolean) -> Unit, mark: (Int) -> Unit, update: (NonogramState) -> Unit,
    nextPuzzle: (() -> Unit)? = null, preparingNext: Boolean = false) {
    PuzzlePage(padding, "Nonogram", back, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PuzzleAction("Undo", "undo", Modifier.weight(1f),
                enabled = state.history.isNotEmpty() && !state.isComplete) { update(NonogramEngine.undo(state)) }
            PuzzleAction("Hint", "hint", Modifier.weight(1f),
                enabled = state.hintsUsed == 0 && !state.isComplete) { update(NonogramEngine.hint(state)) }
        }
    }) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StatusPill("${state.puzzle.size}×${state.puzzle.size} · " +
                when {
                    state.puzzle.endless != null -> "Endless · ${state.puzzle.difficulty.label}"
                    state.puzzle.id.startsWith("daily-") -> "Daily"
                    else -> "Picture ${state.puzzle.number} · ${state.puzzle.difficulty.label}"
                })
        }
        PlayTimerLine(state.playRecord(), state.cells.any { it != 0 } || state.hintsUsed > 0, state.isComplete)
        NonogramBoard(state, selected, mark)
        if (state.isComplete) CompletionCard("${state.puzzle.title} complete!",
            if (preparingNext) "Preparing your next picture…" else "Your progress is saved.",
            if (nextPuzzle != null) "Next puzzle" else "Choose another", nextPuzzle ?: back)
        else {
            Row(Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                .padding(horizontal = 36.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(onClick = { selectMode(false) }, modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = if (!cross) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (!cross) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    shadowElevation = 1.dp) {
                    Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) { Text("■  Fill") }
                }
                Surface(onClick = { selectMode(true) }, modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = if (cross) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (cross) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    shadowElevation = 1.dp) {
                    Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) { Text("×  Cross") }
                }
            }
        }
    }
}

@Composable
private fun NonogramBoard(state: NonogramState, selected: Int, mark: (Int) -> Unit) {
    val puzzle = state.puzzle
    val rows = remember(puzzle) { puzzle.rows }
    val columns = remember(puzzle) { puzzle.columns }
    val depth = columns.maxOf { it.size }
    val selectedRow = if (selected in state.cells.indices) selected / puzzle.size else -1
    val selectedColumn = if (selected in state.cells.indices) selected % puzzle.size else -1
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height((depth * 18 + 8).dp),
            verticalAlignment = Alignment.Bottom) {
            Spacer(Modifier.width(36.dp))
            columns.forEachIndexed { column, clue ->
                Text(clue.joinToString("\n"), Modifier.weight(1f).padding(bottom = 4.dp)
                    .semantics { contentDescription = "Column ${column + 1} clues ${clue.joinToString()}" },
                    color = if (NonogramEngine.clues((0 until puzzle.size).map { if (state.cells[it * puzzle.size + column] == 1) 1 else 0 }) == clue)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center, fontSize = 12.sp, lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(36.dp))
        }
        repeat(puzzle.size) { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(rows[row].joinToString(" "), Modifier.width(36.dp).padding(end = 5.dp)
                    .semantics { contentDescription = "Row ${row + 1} clues ${rows[row].joinToString()}" },
                    color = if (NonogramEngine.clues(state.cells.subList(row * puzzle.size, (row + 1) * puzzle.size).map { if (it == 1) 1 else 0 }) == rows[row])
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                repeat(puzzle.size) { column ->
                    val index = row * puzzle.size + column
                    val cell = state.cells[index]
                    val filled = cell == 1
                    Box(Modifier.weight(1f).aspectRatio(1f)
                        .background(if (filled) MaterialTheme.colorScheme.primary else if (row == selectedRow || column == selectedColumn)
                            MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                        .drawWithContent {
                            drawContent()
                            val thin = 0.35.dp.toPx()
                            val strong = 0.9.dp.toPx()
                            val lineColor = androidx.compose.ui.graphics.Color(0xFF9BB1AB)
                            drawLine(lineColor, Offset.Zero, Offset(0f, size.height),
                                if (column % 5 == 0) strong else thin)
                            drawLine(lineColor, Offset.Zero, Offset(size.width, 0f),
                                if (row % 5 == 0) strong else thin)
                            if (column == puzzle.size - 1) drawLine(lineColor,
                                Offset(size.width, 0f), Offset(size.width, size.height), strong)
                            if (row == puzzle.size - 1) drawLine(lineColor,
                                Offset(0f, size.height), Offset(size.width, size.height), strong)
                        }
                        .clickable(enabled = !state.isComplete) { mark(index) }
                        .semantics {
                            contentDescription = "Row ${row + 1}, column ${column + 1}, " +
                                when (cell) { 1 -> "filled"; 2 -> "crossed out"; else -> "unknown" }
                        }, contentAlignment = Alignment.Center) {
                        if (cell == 2) Text("×", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.width(36.dp))
            }
        }
    }
    }
}
