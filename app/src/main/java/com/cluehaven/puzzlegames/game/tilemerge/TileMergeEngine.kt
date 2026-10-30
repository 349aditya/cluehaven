package com.cluehaven.puzzlegames.game.tilemerge

enum class MoveDirection { UP, DOWN, LEFT, RIGHT }

data class TileMergeSnapshot(
    val board: List<Int>,
    val score: Int,
    val rngState: Long,
    val moveCount: Int,
    val reached2048: Boolean,
)

data class TileMergeState(
    val board: List<Int>,
    val score: Int,
    val bestScore: Int,
    val rngState: Long,
    val runId: String = "",
    val moveCount: Int = 0,
    val undosUsed: Int = 0,
    val reached2048: Boolean = false,
    val isGameOver: Boolean = false,
    val history: List<TileMergeSnapshot> = emptyList(),
) {
    init {
        require(board.size == 16) { "Tile Merge needs a 4×4 board" }
        require(board.all { it == 0 || (it > 0 && (it and (it - 1)) == 0) }) {
            "Tiles must be zero or powers of two"
        }
    }
}

object TileMergeEngine {
    private const val MULTIPLIER = 6364136223846793005L
    private const val INCREMENT = 1442695040888963407L

    fun newGame(seed: Long = System.nanoTime(), bestScore: Int = 0): TileMergeState {
        val board = MutableList(16) { 0 }
        val afterFirst = spawnTile(board, seed)
        val afterSecond = spawnTile(board, afterFirst)
        return TileMergeState(
            board.toList(), score = 0, bestScore = bestScore,
            rngState = afterSecond, runId = "run-$seed",
        )
    }

    fun move(state: TileMergeState, direction: MoveDirection): TileMergeState {
        if (state.isGameOver) return state
        val nextBoard = state.board.toMutableList()
        var points = 0

        for (line in 0..3) {
            val positions = linePositions(direction, line)
            val values = positions.map { state.board[it] }.filter { it != 0 }
            val merged = ArrayList<Int>(4)
            var index = 0
            while (index < values.size) {
                if (index + 1 < values.size && values[index] == values[index + 1]) {
                    val value = values[index] * 2
                    merged.add(value)
                    points += value
                    index += 2
                } else {
                    merged.add(values[index])
                    index++
                }
            }
            while (merged.size < 4) merged.add(0)
            positions.forEachIndexed { column, position -> nextBoard[position] = merged[column] }
        }

        if (nextBoard == state.board) return state
        val previous = TileMergeSnapshot(
            state.board, state.score, state.rngState, state.moveCount, state.reached2048,
        )
        val nextRandomState = spawnTile(nextBoard, state.rngState)
        val nextScore = state.score + points
        return state.copy(
            board = nextBoard.toList(),
            score = nextScore,
            bestScore = maxOf(state.bestScore, nextScore),
            rngState = nextRandomState,
            moveCount = state.moveCount + 1,
            reached2048 = state.reached2048 || nextBoard.any { it >= 2048 },
            isGameOver = !canMove(nextBoard),
            history = (state.history + previous).takeLast(3),
        )
    }

    fun undo(state: TileMergeState): TileMergeState {
        if (state.undosUsed >= 3 || state.history.isEmpty()) return state
        val previous = state.history.last()
        return state.copy(
            board = previous.board,
            score = previous.score,
            rngState = previous.rngState,
            moveCount = previous.moveCount,
            undosUsed = state.undosUsed + 1,
            reached2048 = previous.reached2048,
            isGameOver = !canMove(previous.board),
            history = state.history.dropLast(1),
        )
    }

    fun canMove(board: List<Int>): Boolean {
        require(board.size == 16)
        if (board.any { it == 0 }) return true
        for (row in 0..3) {
            for (column in 0..3) {
                val value = board[row * 4 + column]
                if (column < 3 && value == board[row * 4 + column + 1]) return true
                if (row < 3 && value == board[(row + 1) * 4 + column]) return true
            }
        }
        return false
    }

    private fun linePositions(direction: MoveDirection, line: Int): List<Int> = when (direction) {
        MoveDirection.LEFT -> (0..3).map { line * 4 + it }
        MoveDirection.RIGHT -> (3 downTo 0).map { line * 4 + it }
        MoveDirection.UP -> (0..3).map { it * 4 + line }
        MoveDirection.DOWN -> (3 downTo 0).map { it * 4 + line }
    }

    private fun spawnTile(board: MutableList<Int>, seed: Long): Long {
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return seed
        val positionState = seed * MULTIPLIER + INCREMENT
        val position = empty[(positionState.toULong() % empty.size.toULong()).toInt()]
        val valueState = positionState * MULTIPLIER + INCREMENT
        board[position] = if (valueState.toULong() % 10uL == 0uL) 4 else 2
        return valueState
    }
}
