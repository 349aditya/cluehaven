package com.cluehaven.puzzlegames.ui.daily

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cluehaven.puzzlegames.data.*
import com.cluehaven.puzzlegames.game.daily.*
import com.cluehaven.puzzlegames.game.sudoku.SudokuPlay
import com.cluehaven.puzzlegames.game.nonogram.*
import com.cluehaven.puzzlegames.game.pipes.PipePlay
import com.cluehaven.puzzlegames.game.tilemerge.TileMergeScreen
import com.cluehaven.puzzlegames.game.blocks.BlockScreen
import com.cluehaven.puzzlegames.ui.*
import kotlinx.coroutines.launch
import java.time.LocalDate

internal data class DailyTimerContext(val spec: DailySpec, val complete: Boolean)
internal val LocalDailyTimer = staticCompositionLocalOf<DailyTimerContext?> { null }

/** Internal flow for step-3 review. The Home entry and Haven scene follow after review. */
@Composable
fun DailyFiveScreen(padding: PaddingValues, goBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { DailyHavenRepository.get(context) }
    val today = rememberCurrentDate()
    var dateText by rememberSaveable { mutableStateOf(today.toString()) }
    val date = remember(dateText) { LocalDate.parse(dateText) }
    val entriesFlow = remember(repository, dateText) { repository.observeEntries(date) }
    val dayFlow = remember(repository, dateText) { repository.observeDay(date) }
    val observedEntries by entriesFlow.collectAsState(initial = emptyList())
    val observedDay by dayFlow.collectAsState(initial = null)
    val entries = observedEntries.filter { it.date == dateText }
    val day = observedDay?.takeIf { it.date == dateText }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // The list rolls over locally, while a puzzle already in play keeps its original date.
    LaunchedEffect(today, selected) { if (selected == null) dateText = today.toString() }
    LaunchedEffect(date) {
        error = null
        runCatching { repository.open(date) }.onFailure { error = it.message ?: "Could not open Daily Five" }
    }
    BackHandler { if (selected != null) selected = null else goBack() }
    val entry = entries.firstOrNull { it.gameId == selected }
    if (selected == null) {
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GameHeader("Daily Five", goBack)
            Text(dateText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${entries.count { it.completedAt != null }} / ${DailyGame.entries.size} complete",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (day == null && error == null) CircularProgressIndicator()
            DailyGame.entries.forEach { game ->
                val row = entries.firstOrNull { it.gameId == game.key }
                Card(onClick = { selected = game.key }, enabled = day != null,
                    colors = CardDefaults.cardColors(containerColor =
                    if (row?.completedAt != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(game.title, fontWeight = FontWeight.Bold)
                        Text(row?.spec?.objective ?: "Choose Easy, Medium, or Hard", style = MaterialTheme.typography.bodySmall)
                        Text(when {
                            row?.completedAt != null -> "✓ ${game.growth} earned"
                            row != null -> "Resume · ${row.spec.difficulty.label} locked →"
                            else -> "Choose difficulty →"
                        }, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (day?.bonusAwardedAt != null) Text("All five complete · Daily bonus earned", color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        return
    }
    if (entry == null) {
        val game = DailyGame.fromKey(requireNotNull(selected))
        var difficultyName by rememberSaveable(dateText, game.key) { mutableStateOf(DailyDifficulty.EASY.name) }
        val difficulty = DailyDifficulty.valueOf(difficultyName)
        var starting by remember(dateText, game.key) { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GameHeader(game.title) { selected = null }
            Text("Daily Five · $dateText", style = MaterialTheme.typography.labelMedium)
            Text("Choose today's difficulty", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DailyDifficulty.entries.forEach { option ->
                    FilterChip(selected = difficulty == option, enabled = !starting,
                        onClick = { difficultyName = option.name }, label = { Text(option.label) }, modifier = Modifier.weight(1f))
                }
            }
            Text(DailyChallengeFactory.spec(date, game, difficulty).objective)
            Text("Starting locks this difficulty for today. Resume and Retry keep the same puzzle.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = {
                starting = true
                error = null
                scope.launch {
                    runCatching { repository.start(date, game, difficulty) }
                        .onFailure { error = "Could not start this puzzle. Please try again."; starting = false }
                }
            }, enabled = !starting && day != null, modifier = Modifier.fillMaxWidth()) {
                Text(if (starting) "Starting…" else "Start ${difficulty.label}")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        return
    }
    key(entry.spec.key) {
        var live by remember { mutableStateOf(DailyCodec.decode(entry.spec.game, entry.currentPayload)) }
        var cross by remember { mutableStateOf(false) }
        var selectedCell by remember { mutableIntStateOf(-1) }
        var confirmRetry by remember { mutableStateOf(false) }
        var resetting by remember { mutableStateOf(false) }
        fun submit(next: DailyState?) {
            if (entry.completedAt != null) return
            if (next != null) live = next else resetting = true
            val pending = repository.enqueue(date, entry.spec.game, next)
            scope.launch {
                runCatching { pending.await() }.onSuccess { result ->
                    if (next == null || result.entry.completedAt != null) live = DailyCodec.decode(result.entry.spec.game, result.entry.currentPayload)
                    resetting = false
                }.onFailure {
                    error = "Progress could not be saved. Please try again."
                    live = DailyCodec.decode(entry.spec.game, entry.currentPayload)
                    resetting = false
                }
            }
        }
        CompositionLocalProvider(LocalDailyTimer provides DailyTimerContext(entry.spec, entry.completedAt != null)) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Daily Five · $dateText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(entry.spec.objective, style = MaterialTheme.typography.bodySmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                }
                if (entry.completedAt != null) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CompletionCard("${entry.spec.game.title} complete!", "${entry.spec.game.growth} earned for $dateText.",
                            "Back to Daily Five") { selected = null }
                        PlayTimerLine(live.historyRecord(), previouslyPlayed = true, finished = true)
                    }
                } else if (resetting) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else Box(Modifier.weight(1f)) {
                    when (val current = live) {
                        is DailyState.Sudoku -> SudokuPlay(current.value, PaddingValues(), { selected = null },
                            { submit(DailyState.Sudoku(it)) }, rememberCurrentDate())
                        is DailyState.Nonogram -> NonogramPlay(current.value, PaddingValues(), cross, selectedCell,
                            back = { selected = null }, selectMode = { cross = it },
                            mark = { selectedCell = it; submit(DailyState.Nonogram(NonogramEngine.mark(current.value, it, cross))) },
                            update = { submit(DailyState.Nonogram(it)) })
                        is DailyState.Pipes -> PipePlay(current.value, PaddingValues(), confirmRetry,
                            { selected = null }, { submit(DailyState.Pipes(it)) }, { confirmRetry = true }, newBoardLabel = "Retry")
                        is DailyState.Tile -> TileMergeScreen(PaddingValues(),
                            DailyGameSession(current.value, { submit(DailyState.Tile(it)) }, { submit(null) })) { selected = null }
                        is DailyState.Blocks -> BlockScreen(PaddingValues(),
                            DailyGameSession(current.value, { submit(DailyState.Blocks(it)) }, { submit(null) })) { selected = null }
                    }
                }
            }
        }
        if (confirmRetry) AlertDialog(onDismissRequest = { confirmRetry = false }, title = { Text("Retry today's challenge?") },
            text = { Text("The same starting board will be restored.") },
            confirmButton = { TextButton(onClick = { confirmRetry = false; submit(null) }) { Text("Retry") } },
            dismissButton = { TextButton(onClick = { confirmRetry = false }) { Text("Keep playing") } })
    }
}

private fun DailyState.historyRecord(): PlayRecord = when (this) {
    is DailyState.Sudoku -> value.playRecord()
    is DailyState.Nonogram -> value.playRecord()
    is DailyState.Pipes -> value.playRecord()
    is DailyState.Tile -> value.playRecord()
    is DailyState.Blocks -> value.playRecord()
}
