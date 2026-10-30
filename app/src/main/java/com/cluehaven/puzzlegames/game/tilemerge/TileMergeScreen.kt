package com.cluehaven.puzzlegames.game.tilemerge

import android.app.Activity
import com.cluehaven.puzzlegames.ui.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import com.cluehaven.puzzlegames.ui.LocalReducedMotion
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cluehaven.puzzlegames.ui.theme.Coral
import com.cluehaven.puzzlegames.ui.theme.Cream
import com.cluehaven.puzzlegames.ui.theme.DarkTeal
import com.cluehaven.puzzlegames.ui.theme.Gold
import com.cluehaven.puzzlegames.ui.theme.Mint
import com.cluehaven.puzzlegames.ui.theme.Teal
import com.cluehaven.puzzlegames.data.ProgressRepository
import com.cluehaven.puzzlegames.data.AgeGroup
import com.cluehaven.puzzlegames.data.AppSettings
import com.cluehaven.puzzlegames.data.SettingsRepository
import com.cluehaven.puzzlegames.ads.RewardedUndoAds
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun TileMergeScreen(padding: PaddingValues, goBack: () -> Unit) {
    val feedback = com.cluehaven.puzzlegames.ui.LocalGameFeedback.current
    val context = LocalContext.current
    val storage = remember(context) { TileMergeStorage.get(context) }
    val progress = remember(context) { ProgressRepository.get(context) }
    val settingsRepository = remember(context) { SettingsRepository(context) }
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val ads = remember(context) { context.findActivity()?.let(::RewardedUndoAds) }
    var loadedGame by remember(storage) { mutableStateOf<TileMergeState?>(null) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var showGameOverDialog by remember { mutableStateOf(false) }
    var showAgePrompt by remember { mutableStateOf(false) }
    var adLoading by remember { mutableStateOf(false) }
    var adError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(ads) { onDispose { ads?.cancel() } }

    BackHandler {
        when {
            showAgePrompt -> { showAgePrompt = false; showGameOverDialog = true }
            showRestartDialog -> showRestartDialog = false
            showGameOverDialog -> { ads?.cancel(); adLoading = false; showGameOverDialog = false }
            else -> goBack()
        }
    }

    LaunchedEffect(storage) {
        loadedGame = storage.load().also(progress::recordTile2048)
        showGameOverDialog = loadedGame?.isGameOver == true
    }
    val game = loadedGame ?: run {
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    fun update(next: TileMergeState) {
        feedback()
        loadedGame = next
        storage.save(next)
        if (!game.reached2048 && next.reached2048) progress.recordTile2048(next)
        if (!game.isGameOver && next.isGameOver) showGameOverDialog = true
        if (!next.isGameOver) showGameOverDialog = false
    }

    fun requestAdUndo(ageGroup: AgeGroup) {
        if (adLoading || game.undosUsed !in 1..2 || game.history.isEmpty()) return
        val rewardedAds = ads ?: run {
            adError = "Ad unavailable. You can start a new run."
            return
        }
        adLoading = true
        adError = null
        rewardedAds.show(
            ageGroup = ageGroup,
            onReward = {
                val current = loadedGame
                if (current != null && current.isGameOver &&
                    current.undosUsed in 1..2 && current.history.isNotEmpty()) {
                    update(TileMergeEngine.undo(current))
                }
            },
            onUnavailable = { adError = "Ad unavailable. Try again later or start a new run." },
            onFinished = { adLoading = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GameHeader("Tile Merge", goBack)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScoreCard("SCORE", game.score, Modifier.weight(1f))
            ScoreCard("BEST", game.bestScore, Modifier.weight(1f))
        }

        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val boardSize = minOf(maxWidth, maxHeight)
            Box(
                modifier = Modifier
                    .size(boardSize)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                    .padding(7.dp)
                    .pointerInput(game) {
                    var dragX = 0f
                    var dragY = 0f
                    val threshold = 24.dp.toPx()
                    detectDragGestures(
                        onDragStart = { dragX = 0f; dragY = 0f },
                        onDragEnd = {
                            if (maxOf(abs(dragX), abs(dragY)) > threshold) {
                                val direction = if (abs(dragX) > abs(dragY)) {
                                    if (dragX > 0) MoveDirection.RIGHT else MoveDirection.LEFT
                                } else {
                                    if (dragY > 0) MoveDirection.DOWN else MoveDirection.UP
                                }
                                val next = TileMergeEngine.move(game, direction)
                                if (next != game) update(next)
                            }
                        },
                        onDrag = { change, amount ->
                            dragX += amount.x
                            dragY += amount.y
                            change.consume()
                        },
                    )
                    },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in 0..3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (column in 0..3) {
                                val value = game.board[row * 4 + column]
                                val animatedColor by animateColorAsState(tileColor(value),
                                    tween(if (LocalReducedMotion.current) 0 else 180), label = "Tile color")
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .background(animatedColor, RoundedCornerShape(12.dp))
                                        .semantics {
                                            contentDescription = if (value == 0) "Empty tile" else "Tile $value"
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (value != 0) {
                                        Text(
                                            value.toString(),
                                            color = DarkTeal,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = when {
                                                value >= 1024 -> 18.sp
                                                value >= 128 -> 21.sp
                                                else -> 24.sp
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            when {
                game.isGameOver -> "No moves left · Tap Options"
                game.reached2048 -> "You reached 2048! Keep merging"
                else -> "Swipe tiles to merge · Goal: 2048"
            },
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (game.isGameOver) {
                Button(onClick = { showGameOverDialog = true }, modifier = Modifier.weight(1f)) {
                    Text("Options")
                }
                OutlinedButton(
                    onClick = { update(TileMergeEngine.newGame(bestScore = game.bestScore)) },
                    modifier = Modifier.weight(1f),
                ) { Text("New run") }
            } else {
                PuzzleAction("Undo", "undo", Modifier.weight(1f),
                    enabled = game.undosUsed == 0 && game.history.isNotEmpty()) { update(TileMergeEngine.undo(game)) }
                PuzzleAction("New run", "new", Modifier.weight(1f), primary = true) { showRestartDialog = true }
            }
        }
    }

    if (showGameOverDialog) {
        AlertDialog(
            onDismissRequest = {
                ads?.cancel()
                adLoading = false
                showGameOverDialog = false
            },
            title = { Text("No moves left") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your board is full and there are no matching tiles to merge.")
                    Text("Score ${game.score} · Best ${game.bestScore}",
                        fontWeight = FontWeight.Bold)
                    when {
                        game.history.isEmpty() -> Unit
                        game.undosUsed == 0 -> Button(
                            onClick = { update(TileMergeEngine.undo(game)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Undo last move · Free") }
                        game.undosUsed < 3 && ads?.isAvailable == true -> Button(
                            onClick = {
                                val ageGroup = settings.ageGroup
                                if (ageGroup == null) {
                                    showGameOverDialog = false
                                    showAgePrompt = true
                                } else requestAdUndo(ageGroup)
                            },
                            enabled = !adLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Watch ad to undo") }
                        game.undosUsed < 3 -> Text("Rewarded undo is not available in this build.")
                        else -> Text("All three undos have been used for this run.")
                    }
                    if (adLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Text("Loading ad…")
                        }
                    }
                    adError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ads?.cancel()
                    adLoading = false
                    update(TileMergeEngine.newGame(bestScore = game.bestScore))
                    showGameOverDialog = false
                }) { Text("New game") }
            },
            dismissButton = {
                TextButton(onClick = {
                    ads?.cancel()
                    adLoading = false
                    showGameOverDialog = false
                }) { Text("View board") }
            },
        )
    }

    if (showAgePrompt) {
        AlertDialog(
            onDismissRequest = { showAgePrompt = false; showGameOverDialog = true },
            title = { Text("Select your age group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Choose your age group before requesting an optional ad.")
                    listOf(
                        AgeGroup.UNDER_13 to "Under 13",
                        AgeGroup.AGE_13_TO_17 to "13–17",
                        AgeGroup.AGE_18_PLUS to "18 or older",
                    ).forEach { (group, label) ->
                        OutlinedButton(
                            onClick = {
                                scope.launch { settingsRepository.setAgeGroup(group) }
                                showAgePrompt = false
                                showGameOverDialog = true
                                requestAdUndo(group)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAgePrompt = false; showGameOverDialog = true }) {
                    Text("Not now")
                }
            },
        )
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("Start a new run?") },
            text = { Text("Your current board will be replaced. Your best score stays.") },
            confirmButton = {
                TextButton(onClick = {
                    update(TileMergeEngine.newGame(bestScore = game.bestScore))
                    showRestartDialog = false
                }) { Text("New run") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) { Text("Keep playing") }
            },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ScoreCard(label: String, score: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(13.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(score.toString(), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
        }
    }
}

private fun tileColor(value: Int): Color = when (value) {
    0 -> Color(0xFFF4EFE6)
    2 -> Cream
    4 -> Color(0xFFBFE5D2)
    8 -> Gold
    16 -> Coral
    32 -> Color(0xFFF4947B)
    64 -> Color(0xFFEB7E67)
    128 -> Color(0xFF7EC5CC)
    256 -> Color(0xFF9DB7E6)
    512 -> Color(0xFFBBA7D9)
    1024 -> Color(0xFFE3ABBE)
    else -> Color(0xFFF2D27A)
}
