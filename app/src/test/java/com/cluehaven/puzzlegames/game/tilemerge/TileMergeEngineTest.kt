package com.cluehaven.puzzlegames.game.tilemerge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileMergeEngineTest {
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

    private fun state(board: List<Int>) = TileMergeState(
        board = board,
        score = 0,
        bestScore = 0,
        rngState = 123L,
    )
}
