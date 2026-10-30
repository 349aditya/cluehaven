package com.cluehaven.puzzlegames.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.cluehaven.puzzlegames.data.*
import com.cluehaven.puzzlegames.game.sudoku.SudokuStorage
import com.cluehaven.puzzlegames.game.tilemerge.TileMergeStorage
import com.cluehaven.puzzlegames.game.tilemerge.TileMergeState
import com.cluehaven.puzzlegames.game.nonogram.*
import com.cluehaven.puzzlegames.game.pipes.*
import com.cluehaven.puzzlegames.ui.home.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ProgressScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val progress = remember { ProgressRepository.get(context) }
    val records = remember { PlayRecordRepository.get(context) }
    val history by records.history.collectAsState(initial = emptyList())
    val nonogramStorage = remember { NonogramStorage.get(context) }
    val completions by progress.sudokuCompletions.collectAsState(initial = emptyList())
    val milestones by progress.tileMilestones.collectAsState(initial = emptyList())
    val nonogram by nonogramStorage.progress.collectAsState(initial = NonogramProgress())
    val today = com.cluehaven.puzzlegames.ui.rememberCurrentDate()
    val sudoku = summarizeSudoku(completions, today)
    var tile by remember { mutableStateOf<TileMergeState?>(null) }
    var pipes by remember { mutableStateOf<List<PipeState>>(emptyList()) }
    LaunchedEffect(Unit) {
        val storage = SudokuStorage.get(context)
        storage.load(SudokuStorage.FREE_KEY)?.let(progress::recordSudoku)
        storage.completedDailyPuzzles().forEach(progress::recordSudoku)
        tile = TileMergeStorage.get(context).load().also(progress::recordTile2048)
        val pipeStorage = PipeStorage.get(context)
        pipes = listOf(4, 6).map { pipeStorage.load(it) }
    }
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            Text("Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Daily Sudoku streak · ${sudoku.dailyStreak} days", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (6 downTo 0).forEach { ago ->
                        val day = today.minusDays(ago.toLong())
                        val solved = completions.any { it.mode == "DAILY" && it.puzzleId == "daily-$day" }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = CircleShape, color = if (solved) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface, modifier = Modifier.size(30.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(if (solved) "✓" else "·", color = if (solved) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressCard("Sudoku", Modifier.weight(1f), { SudokuBadge(38.dp) },
                sudoku.total.toString(), "puzzles solved", "Daily: ${sudoku.dailySolved} · Hints: ${sudoku.solvedWithHints}")
            ProgressCard("Tile Merge", Modifier.weight(1f), { TileMergeArtwork(38.dp) },
                (tile?.bestScore ?: 0).toString(), "best score", "${milestones.size} runs reached 2048")
        }
        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressCard("Nonogram", Modifier.weight(1f), { NonogramArtwork(38.dp) },
                "${nonogram.starterSolved} / ${NonogramEngine.puzzles.size}", "pack puzzles solved", "${nonogram.dailySolved} daily pictures")
            ProgressCard("Pipe Connect", Modifier.weight(1f), { PipeArtwork(38.dp) },
                history.count { it.game == "Pipe Connect" }.toString(), "networks solved",
                "${pipes.count { it.isComplete }} completed boards saved")
        }
        PersonalBestSection(history)
        CompletionHistory(history)
        Text("Saved on this device. Older results without a full timer are excluded from fastest times. Older board dates reflect their last save.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PersonalBestSection(history: List<PlayRecord>) {
    val bests = remember(history) { personalBests(history) }
    Text("Personal bests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Fastest unassisted solves", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (bests.isEmpty()) Text("Complete a new puzzle without hints to set your first time.", style = MaterialTheme.typography.bodySmall)
            bests.forEach { best ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${best.game} · ${best.category}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(formatPlayTime(best.elapsedMillis), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun CompletionHistory(history: List<PlayRecord>) {
    var game by remember { mutableStateOf("All") }
    var limit by remember(game) { mutableIntStateOf(10) }
    val filtered = history.filter { game == "All" || it.game == game }
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.getDefault()) }
    Text("Completion history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("All", "Sudoku", "Tile Merge", "Nonogram", "Pipe Connect").forEach { name ->
            FilterChip(selected = name == game, onClick = { game = name }, label = { Text(name) })
        }
    }
    if (filtered.isEmpty()) Text("Your completed puzzles and finished Tile Merge runs will appear here.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    filtered.take(limit).forEach { record ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${record.game} · ${record.category}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(record.title, style = MaterialTheme.typography.bodySmall)
                Text(if (record.game == "Tile Merge") "Score ${record.score} · " +
                    if (record.timeKnown) formatPlayTime(record.elapsedMillis) else "Time unavailable"
                    else (if (record.timeKnown) formatPlayTime(record.elapsedMillis) else "Time unavailable") +
                        if (record.hintsUsed > 0) " · Assisted (${record.hintsUsed} hint)" else " · No hints",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text(dateFormat.format(Instant.ofEpochMilli(record.completedAt!!).atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (filtered.size > limit) OutlinedButton(onClick = { limit += 10 }, modifier = Modifier.fillMaxWidth()) {
        Text("Show more (${filtered.size - limit} remaining)")
    }
}

@Composable
private fun ProgressCard(title: String, modifier: Modifier, artwork: @Composable () -> Unit,
    value: String, label: String, detail: String) {
    Card(modifier = modifier.fillMaxHeight(),
        border = androidx.compose.foundation.BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                artwork()
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    minLines = 2)
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, minLines = 2)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
        }
    }
}
