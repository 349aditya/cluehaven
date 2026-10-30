package com.cluehaven.puzzlegames

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.cluehaven.puzzlegames.ui.*
import com.cluehaven.puzzlegames.data.ThemeMode
import com.cluehaven.puzzlegames.data.CluehavenDatabase
import com.cluehaven.puzzlegames.ui.home.resumeEntry
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cluehaven.puzzlegames.ui.theme.CluehavenTheme
import com.cluehaven.puzzlegames.ui.theme.Coral
import com.cluehaven.puzzlegames.ui.theme.Cream
import com.cluehaven.puzzlegames.ui.theme.Gold
import com.cluehaven.puzzlegames.ui.theme.Mint
import com.cluehaven.puzzlegames.ui.theme.Teal
import com.cluehaven.puzzlegames.game.tilemerge.TileMergeScreen
import com.cluehaven.puzzlegames.game.sudoku.SudokuScreen
import com.cluehaven.puzzlegames.game.nonogram.NonogramScreen
import com.cluehaven.puzzlegames.game.pipes.PipeScreen
import com.cluehaven.puzzlegames.ui.home.NonogramArtwork
import com.cluehaven.puzzlegames.ui.home.PipeArtwork
import com.cluehaven.puzzlegames.data.AppSettings
import com.cluehaven.puzzlegames.data.SettingsRepository
import com.cluehaven.puzzlegames.ui.home.SudokuArtwork
import com.cluehaven.puzzlegames.ui.home.TileMergeArtwork
import com.cluehaven.puzzlegames.ui.progress.ProgressScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsRepository = remember { SettingsRepository(applicationContext) }
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            val scope = rememberCoroutineScope()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            SideEffect {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            val feedbackView = LocalView.current
            val hapticFeedback = LocalHapticFeedback.current
            CompositionLocalProvider(LocalReducedMotion provides settings.reducedMotion,
                LocalGameFeedback provides {
                    if (settings.soundEffects) feedbackView.playSoundEffect(android.view.SoundEffectConstants.CLICK)
                    if (settings.haptics) hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }) {
                CluehavenTheme(darkTheme = dark) {
                    CluehavenApp(settings,
                        { mode -> scope.launch { settingsRepository.setThemeMode(mode) } },
                        { enabled -> scope.launch { settingsRepository.setReducedMotion(enabled) } },
                        { sound, haptics -> scope.launch { settingsRepository.setFeedback(sound, haptics) } })
                }
            }
        }
    }
}

private data class Game(
    val id: String,
    val title: String,
    val subtitle: String,
    val accent: Color,
)

private val games = listOf(
    Game("sudoku", "Sudoku", "Number logic", Cream),
    Game("tile_merge", "Tile Merge", "Reach 2048", Mint),
    Game("nonogram", "Nonogram", "Reveal a picture", Gold),
    Game("pipes", "Pipe Connect", "Connect the network", Coral),
)

@Composable
private fun CluehavenApp(settings: AppSettings, setTheme: (ThemeMode) -> Unit, setMotion: (Boolean) -> Unit, setFeedback: (Boolean, Boolean) -> Unit) {
    var route by rememberSaveable { mutableStateOf("home") }
    val visibleRoute = route.substringBefore('|').takeIf {
        it == "home" || it == "progress" || it == "settings" || it == "sudoku_daily" ||
            games.any { game -> game.id == it }
    } ?: "home"

    BackHandler(enabled = visibleRoute == "progress" || visibleRoute == "settings") {
        route = "home"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (visibleRoute != "sudoku" && visibleRoute != "sudoku_daily" &&
                visibleRoute != "tile_merge" && visibleRoute != "nonogram" && visibleRoute != "pipes") {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.navigationBarsPadding().height(64.dp),
                    windowInsets = WindowInsets(0, 0, 0, 0)) {
                    listOf("home" to "Home", "progress" to "Progress", "settings" to "Settings")
                        .forEach { (id, label) ->
                            NavigationBarItem(
                                selected = visibleRoute == id,
                                onClick = { route = id },
                                icon = {
                                    NavigationSymbol(id, if (visibleRoute == id)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = Color.Transparent,
                                ),
                            )
                        }
                }
            }
        },
    ) { padding ->
        when {
            visibleRoute == "sudoku" -> SudokuScreen(
                resumeFreeOnStart = route.endsWith("|free"),
                padding = padding,
                goBack = { route = "home" },
            )
            visibleRoute == "sudoku_daily" -> SudokuScreen(
                padding = padding,
                goBack = { route = "home" },
                openDailyOnStart = true,
            )
            visibleRoute == "tile_merge" -> TileMergeScreen(padding) { route = "home" }
            visibleRoute == "nonogram" -> NonogramScreen(padding, initialId = route.substringAfter('|', "").ifEmpty { null }) { route = "home" }
            visibleRoute == "pipes" -> PipeScreen(padding, initialSize = route.substringAfter('|', "0").toIntOrNull() ?: 0) { route = "home" }
            visibleRoute == "progress" -> ProgressScreen(padding)
            visibleRoute == "settings" -> SettingsScreen(padding, settings, setTheme, setMotion, setFeedback)
            else -> HomeScreen(padding) { route = it }
        }
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues, settings: AppSettings,
    setTheme: (ThemeMode) -> Unit, setMotion: (Boolean) -> Unit, setFeedback: (Boolean, Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) { Text("Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        Card {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val selected = settings.themeMode == mode
                        Surface(onClick = { setTheme(mode) }, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) {
                            Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(when (mode) { ThemeMode.SYSTEM -> "⚙"; ThemeMode.LIGHT -> "☼"; ThemeMode.DARK -> "☾" }, fontSize = 22.sp)
                                Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                Text("Follow your device or choose a theme.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("Preferences", Modifier.padding(top = 10.dp, bottom = 8.dp), fontWeight = FontWeight.Bold)
                PreferenceRow("Sound effects", settings.soundEffects) { setFeedback(it, settings.haptics) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PreferenceRow("Haptics", settings.haptics) { setFeedback(settings.soundEffects, it) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PreferenceRow("Reduced motion", settings.reducedMotion, setMotion)
                Text("Minimize animations across the app.", Modifier.padding(bottom = 12.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("About Cluehaven", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Version 1.0 · Four little puzzles, always offline")
                HorizontalDivider()
                Text("Settings and puzzle progress are saved on this device.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PreferenceRow(label: String, checked: Boolean, changed: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = changed)
    }
}

@Composable
private fun HomeScreen(padding: PaddingValues, openGame: (String) -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { CluehavenDatabase.get(context) }
    val sessionFlow = remember(database) { database.puzzleSessions().observeSessions() }
    val sessions by sessionFlow.collectAsState(initial = emptyList())
    val today = rememberCurrentDate()
    val resume = sessions.firstNotNullOfOrNull { resumeEntry(it, today) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Cluehaven", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Your next little puzzle awaits", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (resume == null) "Today's puzzle" else "Continue playing", color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(resume?.title ?: "A fresh Sudoku challenge, at your pace", color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodySmall)
                Text("At your pace", style = MaterialTheme.typography.labelSmall)
                }
                when (resume?.route?.substringBefore('|')) {
                    "pipes" -> PipeArtwork(64.dp)
                    "nonogram" -> NonogramArtwork(64.dp)
                    "tile_merge" -> TileMergeArtwork(64.dp)
                    else -> SudokuArtwork(64.dp)
                }
                }
                PuzzleAction(if (resume == null) "Play today's puzzle" else "Continue", "play",
                    Modifier.fillMaxWidth(), primary = true) { openGame(resume?.route ?: "sudoku_daily") }
            }
        }

        games.chunked(2).forEach { rowGames ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowGames.forEach { game ->
                    GameCard(game, Modifier.weight(1f)) { openGame(game.id) }
                }
                if (rowGames.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BrandMark() {
    androidx.compose.foundation.Canvas(Modifier.size(52.dp)) {
        val u = size.width / 52
        drawCircle(Gold, 7 * u, androidx.compose.ui.geometry.Offset(38 * u, 9 * u))
        val house = androidx.compose.ui.graphics.Path().apply {
            moveTo(5*u, 29*u); lineTo(21*u, 13*u); lineTo(37*u, 29*u)
            lineTo(37*u, 49*u); lineTo(5*u, 49*u); close()
        }
        drawPath(house, Teal)
        drawRoundRect(Color(0xFFF8F5EE), androidx.compose.ui.geometry.Offset(17*u, 36*u),
            androidx.compose.ui.geometry.Size(9*u,13*u), androidx.compose.ui.geometry.CornerRadius(3*u))
        drawLine(Teal, androidx.compose.ui.geometry.Offset(41*u, 47*u),
            androidx.compose.ui.geometry.Offset(41*u, 26*u), 2*u)
        drawOval(Mint, androidx.compose.ui.geometry.Offset(31*u, 25*u), androidx.compose.ui.geometry.Size(10*u, 6*u))
        drawOval(Teal, androidx.compose.ui.geometry.Offset(41*u, 31*u), androidx.compose.ui.geometry.Size(10*u, 6*u))
    }
}

@Composable
private fun DailyCard(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SudokuArtwork(60.dp)
            Column {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GameCard(game: Game, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(142.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier.fillMaxWidth().height(78.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (game.id) {
                    "sudoku" -> SudokuArtwork(64.dp)
                    "tile_merge" -> TileMergeArtwork(64.dp)
                    "nonogram" -> NonogramArtwork(64.dp)
                    "pipes" -> PipeArtwork(64.dp)
                }
            }
            Column {
                Text(game.title, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(game.subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    CluehavenTheme { HomeScreen(PaddingValues()) {} }
}
