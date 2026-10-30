package com.cluehaven.puzzlegames.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cluehaven.puzzlegames.data.ProgressRepository
import com.cluehaven.puzzlegames.data.SudokuStats
import com.cluehaven.puzzlegames.data.summarizeSudoku
import com.cluehaven.puzzlegames.game.sudoku.SudokuStorage
import com.cluehaven.puzzlegames.game.tilemerge.TileMergeState
import com.cluehaven.puzzlegames.game.tilemerge.TileMergeStorage
import com.cluehaven.puzzlegames.ui.home.SudokuArtwork
import com.cluehaven.puzzlegames.ui.home.TileMergeArtwork
import java.time.LocalDate

@Composable
fun ProgressScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val progress = remember(context) { ProgressRepository.get(context) }
    val sudokuStorage = remember(context) { SudokuStorage.get(context) }
    val tileStorage = remember(context) { TileMergeStorage.get(context) }
    val completions by progress.sudokuCompletions.collectAsState(initial = emptyList())
    val milestones by progress.tileMilestones.collectAsState(initial = emptyList())
    val today = remember { LocalDate.now() }
    val sudoku = summarizeSudoku(completions, today)
    var tile by remember { mutableStateOf<TileMergeState?>(null) }

    LaunchedEffect(sudokuStorage, tileStorage, today) {
        sudokuStorage.load(SudokuStorage.FREE_KEY)?.let(progress::recordSudoku)
        sudokuStorage.completedDailyPuzzles().forEach(progress::recordSudoku)
        tile = tileStorage.load().also(progress::recordTile2048)
    }

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Progress", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Text("Your puzzles, your pace",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SudokuProgressCard(sudoku)
        TileMergeProgressCard(
            bestScore = tile?.bestScore ?: 0,
            currentScore = tile?.score ?: 0,
            highestTile = tile?.board?.maxOrNull() ?: 0,
            moveCount = tile?.moveCount ?: 0,
            milestoneRuns = milestones.size,
        )
        Text("Progress is saved on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SudokuProgressCard(stats: SudokuStats) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SudokuArtwork(56.dp)
                Column {
                    Text("Sudoku", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text("6×6 and 9×9 puzzles",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
            Metric("Puzzles solved", stats.total.toString())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallMetric("Easy", stats.easy.toString(), Modifier.weight(1f))
                SmallMetric("Medium", stats.medium.toString(), Modifier.weight(1f))
                SmallMetric("Hard", stats.hard.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallMetric("Daily solved", stats.dailySolved.toString(), Modifier.weight(1f))
                SmallMetric("Daily streak", "${stats.dailyStreak} days", Modifier.weight(1f))
            }
            Text(
                if (stats.todayComplete) "Today's Sudoku is complete"
                else "Today's Sudoku is waiting for you",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun TileMergeProgressCard(
    bestScore: Int,
    currentScore: Int,
    highestTile: Int,
    moveCount: Int,
    milestoneRuns: Int,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TileMergeArtwork(56.dp)
                Column {
                    Text("Tile Merge", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text("Keep building toward 2048",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
            Metric("Best score", bestScore.toString())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallMetric("Current score", currentScore.toString(), Modifier.weight(1f))
                SmallMetric("Highest tile now", highestTile.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallMetric("Moves this run", moveCount.toString(), Modifier.weight(1f))
                SmallMetric("2048 runs", milestoneRuns.toString(), Modifier.weight(1f))
            }
            if (bestScore == 0 && moveCount == 0) {
                Text("Play a run to start tracking your score.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SmallMetric(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
