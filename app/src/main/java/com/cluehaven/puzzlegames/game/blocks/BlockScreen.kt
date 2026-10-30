package com.cluehaven.puzzlegames.game.blocks

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cluehaven.puzzlegames.data.playRecord
import com.cluehaven.puzzlegames.ui.*
import com.cluehaven.puzzlegames.ui.theme.*
import kotlin.math.roundToInt

private data class BlockDrag(val slot: Int, val pointer: Offset)
private fun blockColor(value: Int) = when (value) {
    1 -> Color(0xFF328D87)
    2 -> Mint
    3 -> Gold
    else -> Coral
}

@Composable
fun BlockScreen(padding: PaddingValues, goBack: () -> Unit) {
    val context = LocalContext.current
    val storage = remember(context) { BlockStorage.get(context) }
    val feedback = LocalGameFeedback.current
    var loaded by remember { mutableStateOf<BlockState?>(null) }
    var restart by remember { mutableStateOf(false) }
    var showOver by remember { mutableStateOf(false) }
    LaunchedEffect(storage) { loaded = storage.load(); showOver = loaded?.isGameOver == true }
    val game = loaded ?: run {
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    var selected by remember(game.runId) { mutableIntStateOf(-1) }
    var drag by remember(game.runId) { mutableStateOf<BlockDrag?>(null) }
    var boardBounds by remember { mutableStateOf(Rect.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var message by remember(game.runId) { mutableStateOf("Drag a block, or select one and tap the board.") }
    val reduced = LocalReducedMotion.current
    val clearAlpha = remember { Animatable(0f) }
    LaunchedEffect(game.runId, game.moves) {
        if (game.lastCleared.isNotEmpty() && !reduced) {
            clearAlpha.snapTo(.65f)
            clearAlpha.animateTo(0f, tween(260))
        } else clearAlpha.snapTo(0f)
    }
    BackHandler {
        when {
            drag != null -> drag = null
            restart -> restart = false
            showOver -> showOver = false
            else -> goBack()
        }
    }
    fun update(next: BlockState) {
        if (next == game) return
        feedback()
        loaded = next
        storage.save(next)
        selected = -1
        drag = null
        showOver = next.isGameOver
        message = when {
            next.lastCleared.isNotEmpty() -> "${next.lastCleared.size} squares cleared" + if (next.combo > 1) " · Combo ×${next.combo}!" else "!"
            next.undoUsed && !game.undoUsed -> "Last placement restored · Undo used for this run"
            else -> "Drag a block, or select one and tap the board."
        }
    }
    fun place(slot: Int, row: Int, column: Int) {
        val next = BlockEngine.place(game, slot, row, column)
        if (next == game) message = "That block doesn't fit there. Try another space."
        else update(next)
    }
    val lift = with(LocalDensity.current) { 48.dp.toPx() }
    fun dropCell(slot: Int, pointer: Offset): BlockCell? {
        val shape = game.tray.getOrNull(slot)?.let(BlockEngine::shape) ?: return null
        val unit = boardBounds.width / 8
        if (unit <= 0) return null
        return BlockCell(((pointer.y - lift - boardBounds.top) / unit - shape.height / 2f).roundToInt(),
            ((pointer.x - boardBounds.left) / unit - shape.width / 2f).roundToInt())
    }
    val preview = drag?.let { dropCell(it.slot, it.pointer) }
    val previewShape = drag?.slot?.let { game.tray[it]?.let(BlockEngine::shape) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { rootOrigin = it.boundsInRoot().topLeft }) {
    PuzzlePage(padding, "Block Garden", goBack, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PuzzleAction("Undo", "undo", Modifier.weight(1f), enabled = !game.undoUsed && game.previous != null) {
                update(BlockEngine.undo(game))
            }
            PuzzleAction("New run", "new", Modifier.weight(1f), primary = true) { restart = true }
        }
    }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BlockScore("Score", game.score, Modifier.weight(1f))
            BlockScore("Best", game.bestScore, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            StatusPill("8×8 · ${game.lines} lines cleared")
            if (game.combo > 1) Text("Combo ×${game.combo}", color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        BlockBoard(game, previewShape, preview, clearAlpha.value,
            Modifier.widthIn(max = 440.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                .onGloballyPositioned { boardBounds = it.boundsInRoot() }) { row, column ->
            if (selected < 0) message = "Select a block below, then tap its top-left position."
            else place(selected, row, column)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            game.tray.forEachIndexed { slot, id ->
                val piece = id?.let(BlockEngine::shape)
                val fits = piece != null && BlockEngine.placements(game.board, piece).isNotEmpty()
                var pieceOrigin by remember { mutableStateOf(Offset.Zero) }
                Surface(Modifier.weight(1f).height(100.dp)
                    .onGloballyPositioned { pieceOrigin = it.boundsInRoot().topLeft }
                    .pointerInput(game.runId, game.moves, slot, id) {
                        if (piece == null || !fits || game.isGameOver) return@pointerInput
                        detectDragGestures(
                            onDragStart = { offset -> selected = slot; drag = BlockDrag(slot, pieceOrigin + offset) },
                            onDragCancel = { drag = null },
                            onDragEnd = {
                                val cell = drag?.let { dropCell(slot, it.pointer) }
                                if (cell != null) place(slot, cell.row, cell.column)
                                drag = null
                            },
                            onDrag = { change, _ -> change.consume(); drag = BlockDrag(slot, pieceOrigin + change.position) },
                        )
                    }
                    .clickable(enabled = piece != null && fits && !game.isGameOver) {
                        selected = if (selected == slot) -1 else slot
                        message = "Tap the board at the block's top-left position, or drag it into place."
                    }
                    .semantics {
                        contentDescription = if (piece == null) "Block ${slot + 1} used" else
                            "Block ${slot + 1}, ${piece.width} by ${piece.height}, ${piece.cells.size} squares" +
                                if (!fits) ", cannot fit" else if (selected == slot) ", selected" else ""
                    }, shape = RoundedCornerShape(14.dp),
                    color = if (selected == slot) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (piece != null) Canvas(Modifier.fillMaxSize().padding(12.dp).alpha(if (fits && drag?.slot != slot) 1f else .3f)) {
                            val unit = minOf(size.width / maxOf(piece.width, 3), size.height / maxOf(piece.height, 3), 19.dp.toPx())
                            val offset = Offset((size.width - piece.width * unit) / 2, (size.height - piece.height * unit) / 2)
                            piece.cells.forEach { cell ->
                                drawRoundRect(blockColor(piece.color), offset + Offset(cell.column * unit + 1, cell.row * unit + 1),
                                    Size(unit - 2, unit - 2), CornerRadius(3.dp.toPx()))
                            }
                        } else Text("·", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
        Text(if (game.isGameOver) "No remaining blocks fit · Run complete" else message,
            Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        PlayTimerLine(game.playRecord(), game.moves > 0 || game.undoUsed, game.isGameOver, paused = restart)
    }
    val floating = drag
    if (floating != null && previewShape != null && !boardBounds.contains(floating.pointer - Offset(0f, lift))) {
        Canvas(Modifier.matchParentSize()) {
            val unit = boardBounds.width / 8
            val top = floating.pointer - rootOrigin - Offset(previewShape.width * unit / 2, previewShape.height * unit / 2 + lift)
            previewShape.cells.forEach { cell ->
                drawRoundRect(blockColor(previewShape.color).copy(alpha = .9f),
                    top + Offset(cell.column * unit + 2.dp.toPx(), cell.row * unit + 2.dp.toPx()),
                    Size(unit - 4.dp.toPx(), unit - 4.dp.toPx()), CornerRadius(4.dp.toPx()))
            }
        }
    }
    }
    if (restart) AlertDialog(onDismissRequest = { restart = false }, title = { Text("Start a new garden?") },
        text = { Text("Your current board will be replaced. Your best score stays.") },
        confirmButton = { TextButton(onClick = { restart = false; update(BlockEngine.newGame(bestScore = game.bestScore)) }) { Text("New run") } },
        dismissButton = { TextButton(onClick = { restart = false }) { Text("Keep playing") } })
    if (showOver) AlertDialog(onDismissRequest = { showOver = false }, title = { Text("Garden complete") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("No remaining blocks fit. Score ${game.score} · ${game.lines} lines cleared.")
                Text("Best score ${game.bestScore}", fontWeight = FontWeight.Bold)
                if (!game.undoUsed && game.previous != null) OutlinedButton(onClick = { update(BlockEngine.undo(game)) }) {
                    Text("Undo last placement")
                }
            }
        },
        confirmButton = { TextButton(onClick = { showOver = false; update(BlockEngine.newGame(bestScore = game.bestScore)) }) { Text("New run") } },
        dismissButton = { TextButton(onClick = { showOver = false }) { Text("View board") } })
}

@Composable
private fun BlockScore(label: String, score: Int, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(score.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BlockBoard(game: BlockState, previewShape: BlockShape?, preview: BlockCell?, clearAlpha: Float,
    modifier: Modifier, place: (Int, Int) -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val empty = if (dark) Color(0xFF24464B) else Color(0xFFE7E9DE)
    val base = if (dark) Color(0xFF16383D) else Color(0xFFD6DDD2)
    val valid = preview != null && previewShape != null && BlockEngine.canPlace(game.board, previewShape, preview.row, preview.column)
    Box(modifier.aspectRatio(1f)) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(base, cornerRadius = CornerRadius(12.dp.toPx()))
            val unit = size.width / 8
            val inset = 2.dp.toPx()
            val cellSize = Size(unit - inset * 2, unit - inset * 2)
            val radius = CornerRadius(4.dp.toPx())
            game.board.forEachIndexed { index, value ->
                val top = Offset(index % 8 * unit + inset, index / 8 * unit + inset)
                drawRoundRect(if (value == 0) empty else blockColor(value), top, cellSize, radius)
                if (value != 0) drawLine(Color.White.copy(alpha = .2f), top + Offset(4.dp.toPx(), 1.dp.toPx()),
                    top + Offset(cellSize.width - 4.dp.toPx(), 1.dp.toPx()), 1.dp.toPx())
                if (index in game.lastCleared && clearAlpha > 0) drawRoundRect(Gold.copy(alpha = clearAlpha), top, cellSize, radius)
            }
            if (preview != null && previewShape != null) previewShape.cells.forEach { cell ->
                val row = preview.row + cell.row
                val column = preview.column + cell.column
                if (row in 0..7 && column in 0..7) {
                    val top = Offset(column * unit + inset, row * unit + inset)
                    val color = if (valid) blockColor(previewShape.color) else Color(0xFFE75D52)
                    drawRoundRect(color.copy(alpha = .65f), top, cellSize, radius)
                    drawRoundRect(color, top, cellSize, radius, style = Stroke(1.5.dp.toPx()))
                    if (!valid) {
                        val a = top + Offset(cellSize.width * .3f, cellSize.height * .3f)
                        val b = top + Offset(cellSize.width * .7f, cellSize.height * .7f)
                        drawLine(Color.White, a, b, 1.5.dp.toPx())
                        drawLine(Color.White, Offset(a.x, b.y), Offset(b.x, a.y), 1.5.dp.toPx())
                    }
                }
            }
        }
        Column(Modifier.fillMaxSize()) {
            repeat(8) { row ->
                Row(Modifier.weight(1f)) {
                    repeat(8) { column ->
                        Box(Modifier.weight(1f).fillMaxHeight().clickable(enabled = !game.isGameOver) { place(row, column) }
                            .semantics { contentDescription = "Row ${row + 1}, column ${column + 1}, " +
                                if (game.board[row * 8 + column] == 0) "empty" else "occupied" })
                    }
                }
            }
        }
    }
}
