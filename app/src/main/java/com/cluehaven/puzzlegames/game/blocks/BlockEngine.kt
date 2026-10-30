package com.cluehaven.puzzlegames.game.blocks

import kotlin.random.Random

data class BlockCell(val row: Int, val column: Int)
data class BlockShape(val id: String, val cells: List<BlockCell>, val color: Int) {
    val width = cells.maxOf { it.column } + 1
    val height = cells.maxOf { it.row } + 1
}

data class BlockSnapshot(
    val board: List<Int>, val tray: List<String?>, val score: Int,
    val combo: Int, val lines: Int, val moves: Int, val rngState: Long,
)

data class BlockState(
    val runId: String,
    val board: List<Int> = List(64) { 0 },
    val tray: List<String?>,
    val rngState: Long,
    val score: Int = 0,
    val bestScore: Int = 0,
    val combo: Int = 0,
    val lines: Int = 0,
    val moves: Int = 0,
    val undoUsed: Boolean = false,
    val previous: BlockSnapshot? = null,
    val lastCleared: Set<Int> = emptySet(),
) {
    val isGameOver: Boolean get() = tray.filterNotNull().none { BlockEngine.placements(board, BlockEngine.shape(it)).isNotEmpty() }
}

object BlockEngine {
    const val SIZE = 8
    private fun shape(id: String, color: Int, vararg rows: String) = BlockShape(id,
        rows.flatMapIndexed { r, row -> row.mapIndexedNotNull { c, cell -> if (cell == '#') BlockCell(r, c) else null } }, color)

    val shapes: List<BlockShape> = listOf(
        shape("dot", 1, "#"),
        shape("h2", 2, "##"), shape("v2", 2, "#", "#"),
        shape("h3", 1, "###"), shape("v3", 1, "#", "#", "#"),
        shape("h4", 3, "####"), shape("v4", 3, "#", "#", "#", "#"),
        shape("h5", 4, "#####"), shape("v5", 4, "#", "#", "#", "#", "#"),
        shape("square2", 2, "##", "##"), shape("square3", 3, "###", "###", "###"),
        shape("corner0", 4, "##", "#."), shape("corner1", 4, "##", ".#"),
        shape("corner2", 4, "#.", "##"), shape("corner3", 4, ".#", "##"),
        shape("l0", 1, "#.", "#.", "##"), shape("l1", 1, "###", "#.."),
        shape("l2", 1, "##", ".#", ".#"), shape("l3", 1, "..#", "###"),
        shape("j0", 2, ".#", ".#", "##"), shape("j1", 2, "#..", "###"),
        shape("j2", 2, "##", "#.", "#."), shape("j3", 2, "###", "..#"),
        shape("t0", 3, "###", ".#."), shape("t1", 3, "#.", "##", "#."),
        shape("t2", 3, ".#.", "###"), shape("t3", 3, ".#", "##", ".#"),
        shape("s0", 4, ".##", "##."), shape("s1", 4, "#.", "##", ".#"),
        shape("z0", 4, "##.", ".##"), shape("z1", 4, ".#", "##", "#."),
    )
    private val byId = shapes.associateBy { it.id }
    fun shape(id: String): BlockShape = byId.getValue(id)

    fun canPlace(board: List<Int>, shape: BlockShape, row: Int, column: Int): Boolean =
        row >= 0 && column >= 0 && row + shape.height <= SIZE && column + shape.width <= SIZE &&
            shape.cells.all { board[(row + it.row) * SIZE + column + it.column] == 0 }

    fun placements(board: List<Int>, shape: BlockShape): List<BlockCell> =
        (0..SIZE - shape.height).flatMap { r -> (0..SIZE - shape.width).mapNotNull { c ->
            if (canPlace(board, shape, r, c)) BlockCell(r, c) else null
        } }

    internal data class ClearResult(val board: List<Int>, val indices: Set<Int>, val lines: Int)
    internal fun clear(board: List<Int>): ClearResult {
        val rows = (0 until SIZE).filter { r -> (0 until SIZE).all { c -> board[r * SIZE + c] != 0 } }
        val columns = (0 until SIZE).filter { c -> (0 until SIZE).all { r -> board[r * SIZE + c] != 0 } }
        val indices = board.indices.filter { it / SIZE in rows || it % SIZE in columns }.toSet()
        return ClearResult(board.mapIndexed { i, value -> if (i in indices) 0 else value }, indices, rows.size + columns.size)
    }

    private fun placed(board: List<Int>, shape: BlockShape, cell: BlockCell): List<Int> =
        board.toMutableList().also { next -> shape.cells.forEach { next[(cell.row + it.row) * SIZE + cell.column + it.column] = shape.color } }

    internal data class Deal(val tray: List<String?>, val rngState: Long)
    internal fun deal(board: List<Int>, seed: Long, moves: Int): Deal {
        val random = Random(seed)
        var working = board
        val tray = buildList {
            repeat(3) {
                // Construct a legal sequence, including its line clears. Shuffling the tray
                // preserves that sequence's existence while letting the player choose order.
                val candidates = shapes.filter { moves >= 9 || it.cells.size <= 4 }
                    .map { it to placements(working, it) }.filter { it.second.isNotEmpty() }
                val weighted = candidates.flatMap { candidate ->
                    List(when (candidate.first.id) { "dot", "square3" -> 1; else -> 3 }) { candidate }
                }
                val (shape, positions) = weighted.random(random)
                add(shape.id)
                working = clear(placed(working, shape, positions.random(random))).board
            }
        }.shuffled(random)
        return Deal(tray, random.nextLong())
    }

    fun newGame(seed: Long = System.nanoTime(), bestScore: Int = 0): BlockState {
        val board = List(64) { 0 }
        val deal = deal(board, seed, 0)
        return BlockState("garden-$seed", board, deal.tray, deal.rngState, bestScore = bestScore)
    }

    fun place(state: BlockState, slot: Int, row: Int, column: Int): BlockState {
        val piece = state.tray.getOrNull(slot)?.let(::shape) ?: return state
        if (!canPlace(state.board, piece, row, column)) return state
        val result = clear(placed(state.board, piece, BlockCell(row, column)))
        val combo = if (result.lines > 0) state.combo + 1 else 0
        val score = state.score + piece.cells.size * 10 + 100 * result.lines * result.lines * combo
        val remaining = state.tray.toMutableList().also { it[slot] = null }
        val deal = if (remaining.all { it == null }) deal(result.board, state.rngState, state.moves + 1) else null
        return state.copy(board = result.board, tray = deal?.tray ?: remaining, rngState = deal?.rngState ?: state.rngState,
            score = score, bestScore = maxOf(state.bestScore, score), combo = combo,
            lines = state.lines + result.lines, moves = state.moves + 1, lastCleared = result.indices,
            previous = if (state.undoUsed) null else BlockSnapshot(state.board, state.tray, state.score,
                state.combo, state.lines, state.moves, state.rngState))
    }

    fun undo(state: BlockState): BlockState {
        val previous = state.previous?.takeUnless { state.undoUsed } ?: return state
        return state.copy(board = previous.board, tray = previous.tray, score = previous.score,
            combo = previous.combo, lines = previous.lines, moves = previous.moves, rngState = previous.rngState,
            undoUsed = true, previous = null, lastCleared = emptySet())
    }
}
