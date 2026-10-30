package com.cluehaven.puzzlegames.game.pipes

import androidx.activity.compose.BackHandler
import com.cluehaven.puzzlegames.ui.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import com.cluehaven.puzzlegames.ui.theme.Gold
import com.cluehaven.puzzlegames.ui.theme.Coral
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cluehaven.puzzlegames.data.playRecord

@Composable
fun PipeScreen(padding: PaddingValues, initialSize: Int = 0, goBack: () -> Unit) {
    val feedback = com.cluehaven.puzzlegames.ui.LocalGameFeedback.current
    val context = LocalContext.current
    val storage = remember(context) { PipeStorage.get(context) }
    var boardSize by rememberSaveable { mutableStateOf(initialSize.takeIf { it in setOf(4, 6) } ?: 0) }
    var state by remember { mutableStateOf<PipeState?>(null) }
    var restart by remember { mutableStateOf(false) }
    LaunchedEffect(boardSize) {
        state = null
        if (boardSize != 0) state = storage.load(boardSize)
    }
    BackHandler { if (boardSize != 0) boardSize = 0 else goBack() }
    fun update(next: PipeState) {
        feedback()
        state = next
        storage.save(next)
    }
    val current = state
    if (boardSize == 0) {
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GameHeader("Pipe Connect", goBack)
            Text("Bring the whole network to life.", style = MaterialTheme.typography.titleLarge)
            Text("Rotate each tile to connect the network. No open ends, no wrapping edges.")
            listOf(4, 6).forEach { size ->
                Card(onClick = { boardSize = size }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.cluehaven.puzzlegames.ui.home.PipeArtwork(56.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("$size×$size network", fontWeight = FontWeight.Bold)
                            Text(if (size == 4) "A little moment of focus" else "A bigger connection", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    } else if (current == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else PipePlay(current, padding, restart, back = { boardSize = 0 }, update = ::update,
        newBoard = { restart = true })
    if (restart) AlertDialog(onDismissRequest = { restart = false }, title = { Text("Generate a new network?") },
        text = { Text("This replaces your current board for this size.") },
        confirmButton = { TextButton(onClick = {
            state?.let { update(PipeEngine.newGame(it.size)) }; restart = false
        }) { Text("New puzzle") } },
        dismissButton = { TextButton(onClick = { restart = false }) { Text("Keep board") } })
}

@Composable
private fun PipePlay(current: PipeState, padding: PaddingValues, paused: Boolean,
    back: () -> Unit, update: (PipeState) -> Unit, newBoard: () -> Unit) {
    val connected = remember(current.tiles) { PipeEngine.connected(current.size, current.tiles) }
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            GameHeader("Pipe Connect", back)
            StatusPill("${current.size}×${current.size} · ${current.moves} rotations")
            PipeBoard(current, update)
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${connected.size} / ${current.tiles.size} connected", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                    PlayTimerLine(current.playRecord(), current.moves > 0 || current.hintsUsed > 0 || current.history.isNotEmpty(),
                        current.isComplete, paused = paused, modifier = Modifier.weight(1f), alignment = Arrangement.End)
                }
                val dark = MaterialTheme.colorScheme.background.luminance() < .5f
                // A continuous bar matches the reference, without Material's stop dot and gap.
                Canvas(Modifier.fillMaxWidth().height(9.dp).semantics {
                    contentDescription = "${connected.size} of ${current.tiles.size} pipes connected"
                }) {
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(if (dark) Color(0xFF33544F) else Color(0xFFDDDDD5), cornerRadius = radius)
                    drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF15978D), Color(0xFF32A18C))),
                        size = Size(size.width * connected.size / current.tiles.size, size.height), cornerRadius = radius)
                }
            }
            if (current.isComplete) CompletionCard("Network complete!", "Every pipe is connected.", "New network", newBoard)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PuzzleAction("Undo", "undo", Modifier.weight(1f), enabled = current.history.isNotEmpty() && !current.isComplete) {
                    update(PipeEngine.undo(current))
                }
                PuzzleAction("Hint", "hint", Modifier.weight(1f), enabled = current.hintsUsed == 0 && !current.isComplete) {
                    update(PipeEngine.hint(current))
                }
                PuzzleAction("New", "undo", Modifier.weight(1f), primary = true, onClick = newBoard)
            }
        }
    }
}

@Composable
private fun PipeBoard(current: PipeState, update: (PipeState) -> Unit) {
    val connected = remember(current.tiles) { PipeEngine.connected(current.size, current.tiles) }
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val active = if (dark) Color(0xFF42BDB3) else Color(0xFF087D7B)
    val inactive = if (dark) Color(0xFF668080) else Color(0xFFA5AFAA)
    val tile = if (dark) Color(0xFF203E43) else Color(0xFFF0EDE4)
    val border = if (dark) Color(0xFF53726F) else Color(0xFFB4BDB5)
    val source = if (dark) Color(0xFF315D56) else Color(0xFFD1EBDF)
    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))) {
        Canvas(Modifier.matchParentSize()) {
            val unit = size.width / current.size
            current.tiles.indices.forEach { index ->
                val origin = Offset(index % current.size * unit, index / current.size * unit)
                val inset = .7.dp.toPx()
                val tileSize = Size(unit - inset * 2, unit - inset * 2)
                val corner = CornerRadius(3.dp.toPx())
                drawRoundRect(Brush.linearGradient(listOf(tile, tile.copy(alpha = .85f)), origin, origin + Offset(unit, unit)),
                    origin + Offset(inset, inset), tileSize, corner)
                if (index == 0) drawRoundRect(source, origin + Offset(inset, inset), tileSize, corner)
                drawRoundRect(if (index == 0) active.copy(alpha = .7f) else border.copy(alpha = .7f),
                    origin + Offset(inset, inset), tileSize, corner, style = Stroke(.65.dp.toPx()))
            }
        }
        // Full-size cells let matching ports meet exactly across tile boundaries.
        Column(Modifier.fillMaxSize()) {
            repeat(current.size) { row ->
                Row(Modifier.weight(1f)) {
                    repeat(current.size) { column ->
                        val index = row * current.size + column
                        val mask = current.tiles[index]
                        val color by animateColorAsState(if (index in connected) active else inactive,
                            tween(if (LocalReducedMotion.current) 0 else 200), label = "Pipe connection")
                        val ports = listOf("north", "east", "south", "west").filterIndexed { d, _ -> mask and (1 shl d) != 0 }
                        Canvas(Modifier.weight(1f).fillMaxHeight()
                            .clickable(enabled = !current.isComplete) { update(PipeEngine.turn(current, index)) }
                            .semantics { contentDescription = "Row ${row + 1}, column ${column + 1}, " +
                                "ports ${ports.joinToString()}, " + (if (index in connected) "connected" else "disconnected") +
                                when { index == 0 -> ", source"; Integer.bitCount(mask) == 1 -> ", endpoint"; else -> "" } }) {
                            drawPipe(mask, color)
                            if (index == 0 || Integer.bitCount(mask) == 1) {
                                val radius = size.minDimension * .23f
                                drawCircle(if (index == 0) Color(0xFF174B4E) else tile, radius + 1.dp.toPx())
                                drawCircle(if (index == 0) Gold else Coral, radius)
                                drawCircle(Color.White.copy(alpha = .45f), radius * .2f,
                                    center - Offset(radius * .3f, radius * .35f))
                            }
                        }
                    }
                }
            }
        }
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(border, cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()))
        }
    }
}

private fun DrawScope.drawPipe(mask: Int, color: Color) {
    val vectors = listOf(Offset(0f, -1f), Offset(1f, 0f), Offset(0f, 1f), Offset(-1f, 0f))
    val directions = (0..3).filter { mask and (1 shl it) != 0 }
    val half = size.minDimension / 2
    val width = size.minDimension * .34f
    val brush = Brush.linearGradient(listOf(color.copy(red = color.red * .85f, green = color.green * .85f,
        blue = color.blue * .85f), color, color), Offset.Zero, Offset(size.width, size.height))
    fun edge(direction: Int) = center + vectors[direction] * half
    if (directions.size == 2) {
        val a = directions[0]
        val b = directions[1]
        val path = Path().apply {
            moveTo(edge(a).x, edge(a).y)
            if ((a + 2) % 4 == b) lineTo(edge(b).x, edge(b).y)
            else {
                val radius = size.minDimension * .28f
                val start = center + vectors[a] * radius
                val end = center + vectors[b] * radius
                val controlA = start - vectors[a] * (radius * .5522848f)
                val controlB = end - vectors[b] * (radius * .5522848f)
                lineTo(start.x, start.y)
                cubicTo(controlA.x, controlA.y, controlB.x, controlB.y, end.x, end.y)
                lineTo(edge(b).x, edge(b).y)
            }
        }
        drawPath(path, brush, style = Stroke(width, cap = StrokeCap.Butt, join = StrokeJoin.Round))
    } else {
        directions.forEach { drawLine(brush, center, edge(it), width, StrokeCap.Butt) }
        if (directions.isNotEmpty()) drawCircle(brush, width / 2)
    }
}
