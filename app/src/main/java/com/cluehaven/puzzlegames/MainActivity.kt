package com.cluehaven.puzzlegames

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CluehavenTheme(darkTheme = false) { CluehavenApp() } }
    }
}

private data class Game(
    val id: String,
    val title: String,
    val subtitle: String,
    val symbol: String,
    val accent: Color,
    val description: String,
)

private val games = listOf(
    Game("sudoku", "Sudoku", "Number logic", "9", Cream,
        "Fill each row, column, and 3×3 box with the digits 1 through 9."),
    Game("crosswords", "Crosswords", "Words & clues", "A", Coral,
        "Solve short 5×5 crosswords using across and down clues."),
    Game("word_guess", "Word Guess", "Six tries", "?", Gold,
        "Find the five-letter word in six guesses."),
    Game("tile_merge", "Tile Merge", "Reach 2048", "2", Mint,
        "Swipe to combine matching tiles and work toward 2048."),
)

@Composable
private fun CluehavenApp() {
    var route by rememberSaveable { mutableStateOf("home") }
    val selectedGame = games.firstOrNull { it.id == route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (selectedGame == null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf("home" to "Home", "progress" to "Progress", "settings" to "Settings")
                        .forEach { (id, label) ->
                            NavigationBarItem(
                                selected = route == id,
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
            route == "tile_merge" -> TileMergeScreen(padding) { route = "home" }
            selectedGame != null -> GameInfoScreen(selectedGame, padding) { route = "home" }
            route == "progress" -> SimpleScreen(
                "Progress",
                "Your puzzle stats and daily streaks will appear here as you play.",
                padding,
            )
            route == "settings" -> SimpleScreen(
                "Settings",
                "Cluehaven saves your progress on this device. Appearance, sound, and accessibility options will live here.",
                padding,
            )
            else -> HomeScreen(padding) { route = it }
        }
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
                Text("Today's puzzles", color = Color.White,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Two fresh challenges, at your pace", color = Color(0xFFD8E8E3),
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DailyCard("Sudoku", "Daily grid", Modifier.weight(1f)) { openGame("sudoku") }
                    DailyCard("Word Guess", "Daily word", Modifier.weight(1f)) { openGame("word_guess") }
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
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GameCard(game: Game, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(132.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier.size(42.dp).background(game.accent, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(game.symbol, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Teal)
            }
            Column {
                Text(game.title, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(game.subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GameInfoScreen(game: Game, padding: PaddingValues, goBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("‹  Back", modifier = Modifier.clickable(onClick = goBack),
            style = MaterialTheme.typography.titleMedium, color = Teal)
        Box(
            Modifier.size(72.dp).background(game.accent, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(game.symbol, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Teal)
        }
        Text(game.title, style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Text(game.description, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SimpleScreen(title: String, message: String, padding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Text(message, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    CluehavenTheme { HomeScreen(PaddingValues()) {} }
}
