package com.cluehaven.puzzlegames.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cluehaven.puzzlegames.data.PlayRecordRepository
import com.cluehaven.puzzlegames.data.formatPlayTime
import com.cluehaven.puzzlegames.data.isScoreGame
import com.cluehaven.puzzlegames.data.isEndlessNonogram
import com.cluehaven.puzzlegames.ui.GameHeader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CompletionHistoryScreen(padding: PaddingValues, back: () -> Unit) {
    val context = LocalContext.current
    val records = remember(context) { PlayRecordRepository.get(context) }
    val history by records.history.collectAsState(initial = emptyList())
    var game by rememberSaveable { mutableStateOf("All") }
    var limit by rememberSaveable(game) { mutableIntStateOf(10) }
    val filtered = history.filter { game == "All" || if (game == "Endless") isEndlessNonogram(it) else it.game == game }
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
        GameHeader("Completion history", back)
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Sudoku", "Tile Merge", "Nonogram", "Endless", "Pipe Connect", "Block Garden", "Daily Five").forEach { name ->
                    FilterChip(selected = name == game, onClick = { game = name }, label = { Text(name) })
                }
            }
            if (filtered.isEmpty()) Text("Your completed puzzles and finished runs will appear here.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            filtered.take(limit).forEach { record ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${record.game} · ${record.category}", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall)
                        Text(record.title, style = MaterialTheme.typography.bodySmall)
                        Text(if (isScoreGame(record.game)) "Score ${record.score} · " +
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
            Text("Saved on this device. Older board dates reflect their last save.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
