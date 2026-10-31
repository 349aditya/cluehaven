package com.cluehaven.puzzlegames.ui.daily

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cluehaven.puzzlegames.data.*
import com.cluehaven.puzzlegames.game.daily.DailyGame
import com.cluehaven.puzzlegames.ui.ActionSymbol
import com.cluehaven.puzzlegames.ui.PuzzleAction
import com.cluehaven.puzzlegames.ui.home.*
import com.cluehaven.puzzlegames.ui.rememberCurrentDate
import com.cluehaven.puzzlegames.ui.theme.Gold
import com.cluehaven.puzzlegames.ui.theme.Coral
import kotlinx.coroutines.flow.map
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun rememberHavenProgress(): HavenProgress {
    val context = LocalContext.current
    val repository = remember(context) { DailyHavenRepository.get(context) }
    val today = rememberCurrentDate()
    val progressFlow = remember(repository, today) {
        repository.observeHaven(today).map { HavenProgress.from(today, it.entries, it.dailyResults) }
    }
    val saved by progressFlow.collectAsState(initial = HavenProgress(today, loaded = false))
    return saved.takeIf { it.date == today } ?: HavenProgress(today, loaded = false)
}

@Composable
fun DailyFiveHomeCard(progress: HavenProgress, openDaily: () -> Unit, openGame: (DailyGame) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Daily Five", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(progress.countLabel, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            DailyFiveGameRow(progress, artwork = true, openGame = openGame)
            DailyFiveProgressBar(progress)
            Text(progress.waitingMessage, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (progress.loaded) Text("Haven streak · ${progress.streak.countLabel}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            PuzzleAction("Continue Daily Five", "play", Modifier.fillMaxWidth(), primary = true,
                enabled = progress.loaded, onClick = openDaily)
        }
    }
}

@Composable
fun DailyFiveProgressBar(progress: HavenProgress) {
    val active = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .12f)
    Canvas(Modifier.fillMaxWidth().height(7.dp).semantics {
        contentDescription = progress.countLabel
        progressBarRangeInfo = ProgressBarRangeInfo(progress.doneCount.toFloat(), 0f..5f)
    }) {
        val gap = 6.dp.toPx()
        val segment = (size.width - gap * 4) / 5
        repeat(5) { index ->
            drawRoundRect(if (progress.loaded && index < progress.doneCount) active else track,
                Offset(index * (segment + gap), 0f), Size(segment, size.height), CornerRadius(size.height / 2))
        }
    }
}

@Composable
fun HavenScreen(padding: PaddingValues, progress: HavenProgress, openGame: (DailyGame) -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Your Haven", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(progress.grownLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
        }
        Card(shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (progress.loaded) HavenScene(progress)
                else Box(Modifier.fillMaxWidth().aspectRatio(1.32f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                Text(if (!progress.loaded) "Loading saved growth…" else if (progress.grown.isEmpty())
                    "A quiet beginning. Your first puzzle will make something grow."
                    else "Grown: ${progress.grown.joinToString { it.growth.lowercase() }}" +
                        if (progress.waiting.isEmpty()) ". Your Haven is complete today." else
                            ". Waiting: ${progress.waiting.joinToString { it.growth.lowercase() }}.",
                    Modifier.padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Today's Daily Five", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                DailyFiveGameRow(progress, artwork = false, openGame = openGame)
            }
        }
        HavenStreakCard(progress.streak, progress.loaded)
    }
}

@Composable
private fun HavenStreakCard(streak: HavenStreak, loaded: Boolean) {
    Card(shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (loaded) "Haven streak · ${streak.countLabel}" else "Loading streak…",
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Text(if (loaded) streak.restLabel else "Checking saved days…", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val locale = Locale.getDefault()
            val dateFormat = remember(locale) { DateTimeFormatter.ofPattern("EEEE, d MMM", locale) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                streak.week.forEach { day ->
                    val status = day.status
                    val accent = when (status) {
                        HavenDayStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        HavenDayStatus.FORGIVEN -> Gold
                        HavenDayStatus.MISSED -> Coral
                        HavenDayStatus.PENDING -> MaterialTheme.colorScheme.outline
                    }
                    val detail = when {
                        !loaded -> "loading"
                        day.beforeFirstUse -> "pending, before your first Daily Five"
                        day.date.isAfter(streak.today) -> "pending, upcoming"
                        status == HavenDayStatus.FORGIVEN -> "forgiven rest day"
                        else -> status.name.lowercase() + if (day.date == streak.today) ", today" else ""
                    }
                    Column(Modifier.weight(1f).clearAndSetSemantics {
                        contentDescription = "${day.date.format(dateFormat)}: $detail"
                    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(Modifier.size(28.dp), shape = CircleShape,
                            color = when (status) {
                                HavenDayStatus.COMPLETED -> accent
                                HavenDayStatus.FORGIVEN, HavenDayStatus.MISSED -> accent.copy(alpha = .17f)
                                HavenDayStatus.PENDING -> Color.Transparent
                            }, border = BorderStroke(if (day.date == streak.today) 2.dp else 1.dp,
                                if (day.date == streak.today && status == HavenDayStatus.PENDING) MaterialTheme.colorScheme.primary else accent)) {
                            Box(contentAlignment = Alignment.Center) {
                                if (status == HavenDayStatus.COMPLETED) DoneMark(MaterialTheme.colorScheme.onPrimary, Modifier.size(16.dp))
                                else Text(when (status) { HavenDayStatus.FORGIVEN -> "–"; HavenDayStatus.MISSED -> "×"; else -> "·" },
                                    color = if (status == HavenDayStatus.PENDING) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Text(day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text("✓ Done   – Rest   · Pending   × Missed", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (loaded && streak.firstUseDate == null) Text("Complete today's Daily Five to start your streak.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DailyFiveGameRow(progress: HavenProgress, artwork: Boolean, openGame: (DailyGame) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        havenGameOrder.forEach { game ->
            val done = game in progress.completed
            Surface(onClick = { openGame(game) }, enabled = progress.loaded, modifier = Modifier.weight(1f)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${game.title}, " + if (done) "done" else "pending"
                    stateDescription = if (!progress.loaded) "Loading" else if (done) "Complete" else "Pending"
                }, shape = RoundedCornerShape(12.dp), color = Color.Transparent) {
                Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        if (artwork) {
                            when (game) {
                                DailyGame.SUDOKU -> SudokuBadge(30.dp)
                                DailyGame.NONOGRAM -> NonogramArtwork(30.dp)
                                DailyGame.PIPES -> PipeArtwork(30.dp)
                                DailyGame.TILE_MERGE -> TileMergeArtwork(30.dp)
                                DailyGame.BLOCKS -> BlockGardenArtwork(30.dp)
                            }
                            Surface(Modifier.size(15.dp).align(Alignment.BottomEnd), shape = CircleShape,
                                color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
                                if (done) DoneMark(MaterialTheme.colorScheme.onPrimary)
                            }
                        } else Surface(Modifier.fillMaxSize(), shape = CircleShape,
                            color = if (done) MaterialTheme.colorScheme.primary else Color.Transparent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
                            Box(contentAlignment = Alignment.Center) {
                                if (done) DoneMark(MaterialTheme.colorScheme.onPrimary, Modifier.size(22.dp))
                                else ActionSymbol("play", MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Text(when (game) { DailyGame.PIPES -> "Pipe"; DailyGame.TILE_MERGE -> "Merge"
                        DailyGame.BLOCKS -> "Blocks"; else -> game.title },
                        style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                        maxLines = 2, fontWeight = if (done) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun DoneMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize().padding(3.dp)) {
        val check = Path().apply {
            moveTo(size.width * .08f, size.height * .52f)
            lineTo(size.width * .38f, size.height * .82f)
            lineTo(size.width * .94f, size.height * .16f)
        }
        drawPath(check, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
    }
}
