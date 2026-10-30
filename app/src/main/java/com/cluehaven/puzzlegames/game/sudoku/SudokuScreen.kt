package com.cluehaven.puzzlegames.game.sudoku

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cluehaven.puzzlegames.ui.theme.Coral
import com.cluehaven.puzzlegames.ui.theme.DarkTeal
import com.cluehaven.puzzlegames.ui.theme.Mint
import com.cluehaven.puzzlegames.ui.theme.Teal
import java.time.LocalDate

@Composable
fun SudokuScreen(
    padding: PaddingValues,
    goBack: () -> Unit,
    openDailyOnStart: Boolean = false,
) {
    val context = LocalContext.current
    val storage = remember(context) { SudokuStorage.get(context) }
    val today = remember { LocalDate.now() }
    val dailyId = "daily-$today"
    var game by remember { mutableStateOf<SudokuState?>(null) }
    var freeSave by remember { mutableStateOf<SudokuState?>(null) }
    var dailySave by remember { mutableStateOf<SudokuState?>(null) }
    var loading by remember { mutableStateOf(true) }
    var pendingDifficulty by remember { mutableStateOf<SudokuDifficulty?>(null) }
    var showSolved by remember { mutableStateOf(false) }
    var activeMode by rememberSaveable { mutableStateOf(if (openDailyOnStart) "daily" else "") }

    BackHandler {
        when {
            pendingDifficulty != null -> pendingDifficulty = null
            showSolved -> showSolved = false
            game != null -> { game = null; activeMode = "" }
            else -> goBack()
        }
    }

    LaunchedEffect(storage, today) {
        freeSave = storage.load(SudokuStorage.FREE_KEY)
        dailySave = storage.load("sudoku_$dailyId")
        when (activeMode) {
            "free" -> game = freeSave
            "daily" -> {
                val existing = dailySave
                if (existing != null) game = existing
                else {
                    val created = SudokuState(
                        SudokuEngine.generate(
                            SudokuDifficulty.MEDIUM, today.toEpochDay(), dailyId,
                        ),
                        SudokuMode.DAILY,
                    )
                    game = created
                    dailySave = created
                    storage.save(created)
                }
            }
        }
        loading = false
    }

    fun update(next: SudokuState) {
        val wasComplete = game?.isComplete == true
        game = next
        activeMode = if (next.mode == SudokuMode.FREE) "free" else "daily"
        storage.save(next)
        if (next.mode == SudokuMode.FREE) freeSave = next else dailySave = next
        if (!wasComplete && next.isComplete) showSolved = true
    }

    fun startFree(difficulty: SudokuDifficulty) {
        val puzzle = SudokuEngine.generate(difficulty, System.nanoTime())
        update(SudokuState(puzzle, SudokuMode.FREE))
    }

    val current = game
    when {
        loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        current == null -> SudokuChooser(
            padding = padding,
            freeSave = freeSave,
            dailySave = dailySave,
            goBack = goBack,
            continueFree = { game = freeSave; activeMode = "free" },
            openDaily = {
                val existing = dailySave
                if (existing != null) { game = existing; activeMode = "daily" }
                else update(SudokuState(
                    SudokuEngine.generate(SudokuDifficulty.MEDIUM, today.toEpochDay(), dailyId),
                    SudokuMode.DAILY,
                ))
            },
            startFree = { difficulty ->
                if (freeSave != null && freeSave?.isComplete == false) pendingDifficulty = difficulty
                else startFree(difficulty)
            },
        )
        else -> SudokuPlay(
            state = current,
            padding = padding,
            goBack = { game = null; activeMode = "" },
            update = ::update,
        )
    }

    pendingDifficulty?.let { difficulty ->
        AlertDialog(
            onDismissRequest = { pendingDifficulty = null },
            title = { Text("Start a new Sudoku?") },
            text = { Text("Your unfinished free-play board will be replaced.") },
            confirmButton = { TextButton(onClick = {
                pendingDifficulty = null
                startFree(difficulty)
            }) { Text("Start new") } },
            dismissButton = { TextButton(onClick = { pendingDifficulty = null }) {
                Text("Keep playing")
            } },
        )
    }
    if (showSolved) {
        AlertDialog(
            onDismissRequest = { showSolved = false },
            title = { Text("Sudoku complete!") },
            text = { Text("Nice work. Your solved board is saved on this device.") },
            confirmButton = { TextButton(onClick = {
                showSolved = false
                game = null
                activeMode = ""
            }) {
                Text("Choose another")
            } },
            dismissButton = { TextButton(onClick = { showSolved = false }) {
                Text("View board")
            } },
        )
    }
}

@Composable
private fun SudokuChooser(
    padding: PaddingValues,
    freeSave: SudokuState?,
    dailySave: SudokuState?,
    goBack: () -> Unit,
    continueFree: () -> Unit,
    openDaily: () -> Unit,
    startFree: (SudokuDifficulty) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = goBack, contentPadding = PaddingValues(0.dp)) { Text("‹  Back") }
        Text("Sudoku", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Text("Fill every row, column, and box without repeating a number.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(
            onClick = openDaily,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Today's Sudoku", fontWeight = FontWeight.Bold)
                Text(if (dailySave?.isComplete == true) "Completed today" else
                    if (dailySave != null) "Continue today's puzzle" else "Medium · New puzzle")
            }
        }
        if (freeSave != null && !freeSave.isComplete) {
            Button(onClick = continueFree, modifier = Modifier.fillMaxWidth()) {
                Text("Continue ${freeSave.puzzle.size}×${freeSave.puzzle.size} " +
                    "${freeSave.puzzle.difficulty.label()} puzzle")
            }
        }
        Text("Free play", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        SudokuDifficulty.entries.forEach { difficulty ->
            OutlinedButton(onClick = { startFree(difficulty) }, modifier = Modifier.fillMaxWidth()) {
                Text("New ${difficulty.label()} " +
                    "${if (difficulty == SudokuDifficulty.EASY) "6×6" else "9×9"} puzzle")
            }
        }
        Text("Puzzles and progress work offline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SudokuPlay(
    state: SudokuState,
    padding: PaddingValues,
    goBack: () -> Unit,
    update: (SudokuState) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            TextButton(
                onClick = goBack,
                modifier = Modifier.align(Alignment.CenterStart),
                contentPadding = PaddingValues(0.dp),
            ) { Text("‹  Back") }
            Text(
                "Sudoku",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val side = minOf(maxWidth, maxHeight)
            SudokuBoard(state, Modifier.size(side).align(Alignment.Center)) { cell ->
                update(SudokuEngine.select(state, cell))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            (1..state.puzzle.size).forEach { digit ->
                Box(
                    Modifier.weight(1f).height(46.dp)
                        .background(Mint.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
                        .clickable(enabled = !state.isComplete) {
                            update(SudokuEngine.enter(state, digit))
                        }
                        .semantics { contentDescription = "Enter $digit" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$digit", fontWeight = FontWeight.Bold, color = DarkTeal,
                        style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(
                onClick = { update(state.copy(noteMode = !state.noteMode)) },
                enabled = !state.isComplete,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) { Text(if (state.noteMode) "Notes ✓" else "Notes", maxLines = 1) }
            OutlinedButton(
                onClick = { update(SudokuEngine.erase(state)) },
                enabled = !state.isComplete,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) { Text("Erase", maxLines = 1) }
            OutlinedButton(
                onClick = { update(SudokuEngine.undo(state)) },
                enabled = state.history.isNotEmpty() && !state.isComplete,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) { Text("Undo", maxLines = 1) }
            Button(
                onClick = { update(SudokuEngine.freeHint(state)) },
                enabled = state.hintsUsed == 0 && !state.isComplete,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) { Text("Hint", maxLines = 1) }
        }
        Text(
            if (state.hintsUsed == 0) "One free hint available"
            else "Free hint used · More hint options coming later",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SudokuBoard(state: SudokuState, modifier: Modifier, select: (Int) -> Unit) {
    val size = state.puzzle.size
    val boxRows = state.puzzle.boxRows
    val boxColumns = state.puzzle.boxColumns
    val gridColor = MaterialTheme.colorScheme.primary
    val givenColor = MaterialTheme.colorScheme.onSurface
    Box(modifier.border(2.dp, gridColor)) {
        Column(Modifier.fillMaxSize()) {
            for (row in 0 until size) {
                Row(Modifier.weight(1f)) {
                    for (column in 0 until size) {
                        val index = row * size + column
                        val value = state.values[index]
                        val given = state.puzzle.givens[index] != 0
                        val selected = index == state.selected
                        val related = row == state.selected / size ||
                            column == state.selected % size
                        val wrong = value != 0 && value != state.puzzle.solution[index]
                        val background = when {
                            selected -> Mint
                            related -> Mint.copy(alpha = 0.25f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                        val description = buildString {
                            append("Row ${row + 1}, column ${column + 1}, ")
                            append(if (value == 0) "empty" else "$value")
                            if (given) append(", given")
                            if (wrong) append(", incorrect")
                            if (selected) append(", selected")
                        }
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .background(background)
                                .clickable { select(index) }
                                .semantics { contentDescription = description },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (value != 0) {
                                Text(
                                    "$value",
                                    fontWeight = if (given) FontWeight.Bold else FontWeight.Medium,
                                    color = when {
                                        wrong -> Coral
                                        selected -> DarkTeal
                                        given -> givenColor
                                        else -> gridColor
                                    },
                                    fontSize = if (size == 6) 26.sp else 20.sp,
                                )
                            } else if (state.notes[index] != 0) {
                                Column(Modifier.fillMaxSize()) {
                                    for (noteRow in 0 until boxRows) {
                                        Row(Modifier.weight(1f)) {
                                            for (noteColumn in 0 until boxColumns) {
                                                val digit = noteRow * boxColumns + noteColumn + 1
                                                Box(Modifier.weight(1f).fillMaxHeight(),
                                                    contentAlignment = Alignment.Center) {
                                                    if (state.notes[index] and (1 shl digit) != 0) {
                                                        Text("$digit", fontSize =
                                                            if (size == 6) 10.sp else 8.sp,
                                                            color = gridColor)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            for (line in 1 until size) {
                val verticalThick = line % boxColumns == 0
                val horizontalThick = line % boxRows == 0
                val x = this.size.width * line / size
                val y = this.size.height * line / size
                drawLine(
                    if (verticalThick) gridColor else Color(0xFFB7C7C4),
                    androidx.compose.ui.geometry.Offset(x, 0f),
                    androidx.compose.ui.geometry.Offset(x, this.size.height),
                    if (verticalThick) 2.dp.toPx() else 0.6.dp.toPx(),
                )
                drawLine(
                    if (horizontalThick) gridColor else Color(0xFFB7C7C4),
                    androidx.compose.ui.geometry.Offset(0f, y),
                    androidx.compose.ui.geometry.Offset(this.size.width, y),
                    if (horizontalThick) 2.dp.toPx() else 0.6.dp.toPx(),
                )
            }
        }
    }
}

private fun SudokuDifficulty.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }
