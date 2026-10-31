package com.cluehaven.puzzlegames.game.blocks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cluehaven.puzzlegames.ui.LocalReducedMotion
import com.cluehaven.puzzlegames.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// Only drawing reads pointer position. Moving a finger must not recompose the
// score, timer, tray availability searches, or the board's 64 accessible cells.
@Stable
internal class BlockDrag(val slot: Int, val shape: BlockShape, pointer: Offset) {
    var pointer by mutableStateOf(pointer)
}

@Stable
internal class BlockInteraction {
    var drag by mutableStateOf<BlockDrag?>(null)
    var boardBounds by mutableStateOf(Rect.Zero)
    var rootOrigin by mutableStateOf(Offset.Zero)
}

internal data class BlockClearEffect(val move: Int, val tiles: Map<Int, Int>)
internal data class BlockRejection(val attempt: Long, val shape: BlockShape, val cell: BlockCell)

internal fun blockDropCell(shape: BlockShape, bounds: Rect, pointer: Offset, lift: Float): BlockCell? {
    val unit = bounds.width / BlockEngine.SIZE
    val center = pointer - Offset(0f, lift)
    if (unit <= 0f || !bounds.contains(center)) return null
    return BlockCell(((center.y - bounds.top) / unit - shape.height / 2f).roundToInt(),
        ((center.x - bounds.left) / unit - shape.width / 2f).roundToInt())
}

internal fun blockColor(value: Int) = when (value) {
    1 -> Color(0xFF328D87)
    2 -> Mint
    3 -> Gold
    else -> Coral
}

@Composable
internal fun BlockBoard(game: BlockState, interaction: BlockInteraction, clear: BlockClearEffect?,
    rejection: BlockRejection?, modifier: Modifier, place: (Int, Int) -> Unit) {
    val reduced = LocalReducedMotion.current
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val empty = if (dark) Color(0xFF24464B) else Color(0xFFE7E9DE)
    val base = if (dark) Color(0xFF16383D) else Color(0xFFD6DDD2)
    val gameOver = remember(game.board, game.tray) { game.isGameOver }
    val lift = with(LocalDensity.current) { 48.dp.toPx() }
    val drag = interaction.drag
    val invalid by remember(drag, game.board, interaction.boardBounds, lift) {
        derivedStateOf {
            val cell = drag?.let { blockDropCell(it.shape, interaction.boardBounds, it.pointer, lift) }
            cell != null && !BlockEngine.canPlace(game.board, drag!!.shape, cell.row, cell.column)
        }
    }
    val invalidAmount = animateFloatAsState(if (invalid) 1f else 0f,
        tween(if (reduced) 0 else 90), label = "Blocked landing")
    val clearProgress = remember { Animatable(1f) }
    LaunchedEffect(clear) {
        if (clear != null && !reduced) {
            clearProgress.snapTo(0f)
            clearProgress.animateTo(1f, tween(300))
        } else clearProgress.snapTo(1f)
    }
    LaunchedEffect(reduced) {
        if (reduced) clearProgress.snapTo(1f)
    }
    val rejectionAlpha = remember { Animatable(0f) }
    LaunchedEffect(rejection) {
        if (rejection != null) {
            rejectionAlpha.snapTo(1f)
            delay(120)
            if (reduced) rejectionAlpha.snapTo(0f)
            else rejectionAlpha.animateTo(0f, tween(180))
        } else rejectionAlpha.snapTo(0f)
    }
    Box(modifier.aspectRatio(1f).onGloballyPositioned { interaction.boardBounds = it.boundsInRoot() }) {
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
            }
        }
        // Clear animations invalidate this canvas only, not the board's semantics/layout.
        Canvas(Modifier.matchParentSize()) {
            val unit = size.width / 8
            val inset = 2.dp.toPx()
            val progress = clearProgress.value
            if (clear != null && progress < 1f) clear.tiles.forEach { (index, color) ->
                val local = ((progress - (index % 8 + index / 8) * .012f) / .83f).coerceIn(0f, 1f)
                val shrink = (unit - inset * 2) * local * .22f
                val top = Offset(index % 8 * unit + inset + shrink, index / 8 * unit + inset + shrink)
                val cellSize = Size(unit - inset * 2 - shrink * 2, unit - inset * 2 - shrink * 2)
                drawRoundRect(blockColor(color).copy(alpha = 1f - local), top, cellSize, CornerRadius(4.dp.toPx()))
                drawRoundRect(Gold.copy(alpha = (1f - local) * .45f), top, cellSize, CornerRadius(4.dp.toPx()))
            }
            val cell = drag?.let { blockDropCell(it.shape, interaction.boardBounds, it.pointer, lift) }
            if (cell != null) {
                val valid = BlockEngine.canPlace(game.board, drag.shape, cell.row, cell.column)
                drawLanding(drag.shape, cell, valid, 1f, if (valid) 0f else invalidAmount.value)
            } else if (drag == null && rejection != null && rejectionAlpha.value > 0f) {
                drawLanding(rejection.shape, rejection.cell, false, rejectionAlpha.value, 1f)
            }
        }
        Column(Modifier.fillMaxSize()) {
            repeat(8) { row ->
                Row(Modifier.weight(1f)) {
                    repeat(8) { column ->
                        Box(Modifier.weight(1f).fillMaxHeight().clickable(enabled = !gameOver) { place(row, column) }
                            .semantics { contentDescription = "Row ${row + 1}, column ${column + 1}, " +
                                if (game.board[row * 8 + column] == 0) "empty" else "occupied" })
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawLanding(shape: BlockShape, cell: BlockCell, valid: Boolean, alpha: Float, cross: Float) {
    val unit = size.width / 8
    val inset = 2.dp.toPx()
    val cellSize = Size(unit - inset * 2, unit - inset * 2)
    val color = if (valid) blockColor(shape.color) else Coral
    shape.cells.forEach { square ->
        val row = cell.row + square.row
        val column = cell.column + square.column
        if (row in 0..7 && column in 0..7) {
            val top = Offset(column * unit + inset, row * unit + inset)
            drawRoundRect(color.copy(alpha = alpha * if (valid) .25f else .4f), top, cellSize, CornerRadius(4.dp.toPx()))
            drawRoundRect(color.copy(alpha = alpha), top, cellSize, CornerRadius(4.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            if (!valid && cross > 0f) {
                val a = top + Offset(cellSize.width * .3f, cellSize.height * .3f)
                val b = top + Offset(cellSize.width * .7f, cellSize.height * .7f)
                val ink = Color.White.copy(alpha = alpha * cross)
                drawLine(ink, a, lerp(a, b, (cross * 2).coerceAtMost(1f)), 1.5.dp.toPx(), StrokeCap.Round)
                val c = Offset(a.x, b.y)
                drawLine(ink, c, lerp(c, Offset(b.x, a.y), (cross * 2 - 1).coerceIn(0f, 1f)), 1.5.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun BlockTray(game: BlockState, selected: Int, interaction: BlockInteraction,
    select: (Int) -> Unit, drop: (Int, BlockCell) -> Unit) {
    val fits = remember(game.board, game.tray) {
        game.tray.map { id -> id != null && BlockEngine.hasPlacement(game.board, BlockEngine.shape(id)) }
    }
    val latestDrop by rememberUpdatedState(drop)
    val latestSelect by rememberUpdatedState(select)
    val lift = with(LocalDensity.current) { 48.dp.toPx() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        game.tray.forEachIndexed { slot, id ->
            val piece = remember(id) { id?.let(BlockEngine::shape) }
            var pieceOrigin by remember { mutableStateOf(Offset.Zero) }
            Surface(Modifier.weight(1f).height(100.dp)
                .onGloballyPositioned { pieceOrigin = it.boundsInRoot().topLeft }
                .pointerInput(game.runId, game.moves, slot, id, fits[slot]) {
                    if (piece == null || !fits[slot]) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            latestSelect(slot)
                            interaction.drag = BlockDrag(slot, piece, pieceOrigin + offset)
                        },
                        onDragCancel = { interaction.drag = null },
                        onDragEnd = {
                            val cell = interaction.drag?.let { blockDropCell(piece, interaction.boardBounds, it.pointer, lift) }
                            interaction.drag = null
                            if (cell != null) latestDrop(slot, cell)
                        },
                        onDrag = { change, _ -> change.consume(); interaction.drag?.pointer = pieceOrigin + change.position },
                    )
                }
                .clickable(enabled = piece != null && fits[slot]) { latestSelect(if (selected == slot) -1 else slot) }
                .semantics {
                    contentDescription = if (piece == null) "Block ${slot + 1} used" else
                        "Block ${slot + 1}, ${piece.width} by ${piece.height}, ${piece.cells.size} squares" +
                            if (!fits[slot]) ", cannot fit" else if (selected == slot) ", selected" else ""
                }, shape = RoundedCornerShape(14.dp),
                color = if (selected == slot) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Box(contentAlignment = Alignment.Center) {
                    if (piece != null) Canvas(Modifier.fillMaxSize().padding(12.dp)
                        .alpha(if (fits[slot] && interaction.drag?.slot != slot) 1f else .3f)) {
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
}

@Composable
internal fun BlockDragOverlay(game: BlockState, interaction: BlockInteraction, modifier: Modifier) {
    val drag = interaction.drag ?: return
    val reduced = LocalReducedMotion.current
    val pickup = remember(drag) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(drag, reduced) {
        if (reduced) pickup.snapTo(1f) else pickup.animateTo(1f, tween(90))
    }
    Canvas(modifier) {
        val fullUnit = interaction.boardBounds.width / 8
        val unit = fullUnit * (.8f + .2f * pickup.value)
        val lift = 48.dp.toPx() * pickup.value
        val top = drag.pointer - interaction.rootOrigin - Offset(drag.shape.width * unit / 2, drag.shape.height * unit / 2 + lift)
        val landing = blockDropCell(drag.shape, interaction.boardBounds, drag.pointer, 48.dp.toPx())
        val blocked = landing != null && !BlockEngine.canPlace(game.board, drag.shape, landing.row, landing.column)
        drag.shape.cells.forEach { cell ->
            val position = top + Offset(cell.column * unit + 2.dp.toPx(), cell.row * unit + 2.dp.toPx())
            val cellSize = Size((unit - 4.dp.toPx()).coerceAtLeast(0f), (unit - 4.dp.toPx()).coerceAtLeast(0f))
            drawRoundRect(Color.Black.copy(alpha = .12f), position + Offset(0f, 3.dp.toPx()), cellSize, CornerRadius(4.dp.toPx()))
            drawRoundRect(blockColor(drag.shape.color).copy(alpha = if (blocked) .38f else .92f), position, cellSize, CornerRadius(4.dp.toPx()))
        }
    }
}
