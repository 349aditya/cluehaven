package com.cluehaven.puzzlegames.game.tilemerge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileMergeEngineTest {
    @Test
    fun oddAndEvenSeedsSpawnApproximatelyTenPercentFours() {
        for (seed in listOf(1L, 2L, 3L, 42L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            var rng = seed
            var fours = 0
            repeat(10_000) {
                val start = state(List(15) { 0 } + 2).copy(rngState = rng)
                val next = TileMergeEngine.move(start, MoveDirection.LEFT)
                fours += next.board.count { it == 4 }
                rng = next.rngState
            }
            assertTrue("Seed $seed spawned $fours fours", fours in 850..1150)
        }
    }

    @Test
    fun everyEmptyPositionCanReceiveTilesForEitherSeedParity() {
        for (seed in listOf(1L, 2L)) {
            var rng = seed
            val positions = IntArray(16)
            repeat(10_000) {
                val next = TileMergeEngine.move(state(List(15) { 0 } + 2).copy(rngState = rng), MoveDirection.LEFT)
                // The original tile slides left within the bottom row, to cell twelve.
                positions[next.board.indices.single { it != 12 && next.board[it] != 0 }]++
                rng = next.rngState
            }
            assertTrue("Seed $seed: ${positions.toList()}", (0..15).filter { it != 12 }.all { positions[it] in 400..950 })
        }
    }

    @Test
    fun undoAndReplayProduceExactlyTheSameSpawn() {
        val start = state(listOf(2, 2, 0, 0) + List(12) { 0 })
        val first = TileMergeEngine.move(start, MoveDirection.LEFT)
        val replayed = TileMergeEngine.move(TileMergeEngine.undo(first), MoveDirection.LEFT)
        assertEquals(first.board, replayed.board)
        assertEquals(first.rngState, replayed.rngState)
        assertEquals(first.score, replayed.score)
        assertEquals(TileMergeEngine.newGame(42), TileMergeEngine.newGame(42))
    }

    @Test
    fun newGameStartsWithExactlyTwoTiles() {
        val game = TileMergeEngine.newGame(seed = 42L)
        assertEquals(2, game.board.count { it != 0 })
        assertEquals(0, game.score)
        assertEquals("run-42", game.runId)
        assertFalse(game.isGameOver)
    }

    @Test
    fun oneMoveMergesEachTileOnlyOnce() {
        val start = state(listOf(2, 2, 2, 2) + List(12) { 0 })
        val moved = TileMergeEngine.move(start, MoveDirection.LEFT)
        assertEquals(4, moved.board[0])
        assertEquals(4, moved.board[1])
        assertEquals(8, moved.score)
        assertEquals(1, moved.moveCount)
    }

    @Test
    fun noOpDoesNotSpawnTileOrUseRandomness() {
        val start = state(listOf(2, 0, 0, 0) + List(12) { 0 })
        assertEquals(start, TileMergeEngine.move(start, MoveDirection.LEFT))
    }

    @Test
    fun movingUpMergesTilesInTheSameColumn() {
        val start = state(listOf(
            2, 0, 0, 0,
            2, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
        ))
        val moved = TileMergeEngine.move(start, MoveDirection.UP)
        assertEquals(4, moved.board[0])
        assertEquals(4, moved.score)
    }

    @Test
    fun undoRestoresBoardScoreAndRandomState() {
        val start = state(listOf(2, 2, 0, 0) + List(12) { 0 })
        val moved = TileMergeEngine.move(start, MoveDirection.LEFT)
        val undone = TileMergeEngine.undo(moved)
        assertEquals(start.board, undone.board)
        assertEquals(start.score, undone.score)
        assertEquals(start.rngState, undone.rngState)
        assertEquals(start.runId, undone.runId)
        assertEquals(1, undone.undosUsed)
    }

    @Test
    fun fullBoardWithNoMatchingNeighborsIsOver() {
        val board = listOf(
            2, 4, 2, 4,
            4, 2, 4, 2,
            2, 4, 2, 4,
            4, 2, 4, 2,
        )
        assertFalse(TileMergeEngine.canMove(board))
        assertTrue(TileMergeEngine.canMove(board.toMutableList().also { it[0] = 0 }))
    }

    @Test
    fun gameOverCanBeUndoneUntilThreeUndoLimit() {
        val fullBoard = listOf(
            2, 4, 2, 4,
            4, 2, 4, 2,
            2, 4, 2, 4,
            4, 2, 4, 2,
        )
        val previousBoard = fullBoard.toMutableList().also { it[0] = 0 }
        val gameOver = TileMergeState(
            board = fullBoard,
            score = 100,
            bestScore = 100,
            rngState = 5L,
            isGameOver = true,
            undosUsed = 1,
            history = listOf(TileMergeSnapshot(previousBoard, 96, 4L, 10, false)),
        )
        val undone = TileMergeEngine.undo(gameOver)
        assertFalse(undone.isGameOver)
        assertEquals(2, undone.undosUsed)
        assertEquals(previousBoard, undone.board)
        val exhausted = gameOver.copy(undosUsed = 3)
        assertEquals(exhausted, TileMergeEngine.undo(exhausted))
    }

    private fun state(board: List<Int>) = TileMergeState(
        board = board,
        score = 0,
        bestScore = 0,
        rngState = 123L,
    )
}
