package com.cluehaven.puzzlegames

import android.os.Bundle
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
import com.cluehaven.puzzlegames.data.AppSettings
import com.cluehaven.puzzlegames.data.SettingsRepository
import com.cluehaven.puzzlegames.ui.home.SudokuArtwork
import com.cluehaven.puzzlegames.ui.home.TileMergeArtwork
import com.cluehaven.puzzlegames.ui.progress.ProgressScreen
import com.cluehaven.puzzlegames.game.pipes.PipeScreen
import com.cluehaven.puzzlegames.game.nonogram.NonogramScreen
import com.cluehaven.puzzlegames.ui.home.NonogramArtwork
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsRepository = remember { SettingsRepository(applicationContext) }
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            val scope = rememberCoroutineScope()
            CluehavenTheme(darkTheme = settings.darkTheme) {
                CluehavenApp(settings.darkTheme) { enabled ->
                    scope.launch { settingsRepository.setDarkTheme(enabled) }
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
private fun CluehavenApp(darkTheme: Boolean, setDarkTheme: (Boolean) -> Unit) {
    var route by rememberSaveable { mutableStateOf("home") }
    val visibleRoute = route.takeIf {
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
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf("home" to "Home", "progress" to "Progress", "settings" to "Settings")
                        .forEach { (id, label) ->
                            NavigationBarItem(
                                selected = visibleRoute == id,
                                onClick = { route = id },
                                icon = {
                                    Text(
                                        when (id) {
                                            "home" -> "⌂"
                                            "progress" -> "▥"
                                            else -> "⚙"
                                        },
                                        fontSize = 22.sp,
                                    )
                                },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Teal,
                                    selectedTextColor = Teal,
                                    indicatorColor = Mint,
                                ),
                            )
                        }
                }
            }
        },
    ) { padding ->
        when {
            visibleRoute == "sudoku" -> SudokuScreen(
                padding = padding,
                goBack = { route = "home" },
            )
            visibleRoute == "sudoku_daily" -> SudokuScreen(
                padding = padding,
                goBack = { route = "home" },
                openDailyOnStart = true,
            )
            visibleRoute == "tile_merge" -> TileMergeScreen(padding) { route = "home" }
            visibleRoute == "nonogram" -> NonogramScreen(padding) { route = "home" }
            visibleRoute == "pipes" -> PipeScreen(padding) { route = "home" }
            visibleRoute == "progress" -> ProgressScreen(padding)
            visibleRoute == "settings" -> SettingsScreen(padding, darkTheme, setDarkTheme)
            else -> HomeScreen(padding) { route = it }
        }
    }
}

@Composable
private fun SettingsScreen(
    padding: PaddingValues,
    darkTheme: Boolean,
    setDarkTheme: (Boolean) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(
                Modifier.fillMaxWidth().clickable { setDarkTheme(!darkTheme) }.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Dark theme", style = MaterialTheme.typography.titleMedium)
                Switch(checked = darkTheme, onCheckedChange = setDarkTheme)
            }
        }
        Text(
            "Your settings and puzzle progress are saved on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeScreen(padding: PaddingValues, openGame: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Spacer(Modifier.width(12.dp))
            Text("Cluehaven", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
        }
        Text("Your next little puzzle awaits", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(
            colors = CardDefaults.cardColors(containerColor = Teal),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Today's puzzle", color = Color.White,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("A fresh Sudoku challenge, at your pace", color = Color(0xFFD8E8E3),
                    style = MaterialTheme.typography.bodySmall)
                DailyCard("Sudoku", "Daily grid", Modifier.fillMaxWidth()) {
                    openGame("sudoku_daily")
                }
            }
        }

        Text("Choose a game", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
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
    Column(
        modifier = Modifier.size(36.dp).background(Teal, RoundedCornerShape(10.dp)).padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.size(10.dp).background(Gold, RoundedCornerShape(2.dp)))
            Box(Modifier.size(10.dp).background(Mint, RoundedCornerShape(2.dp)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.size(10.dp).background(Coral, RoundedCornerShape(2.dp)))
            Box(Modifier.size(10.dp).background(Cream, RoundedCornerShape(2.dp)))
        }
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
        modifier = modifier.height(186.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier.fillMaxWidth().height(112.dp)
                    .background(game.accent.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when (game.id) {
                    "sudoku" -> SudokuArtwork(96.dp)
                    "tile_merge" -> TileMergeArtwork(96.dp)
                    "nonogram" -> NonogramArtwork(96.dp)
                    "pipes" -> Text("╋", fontSize = 80.sp, color = Teal)
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
