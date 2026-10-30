package com.cluehaven.puzzlegames.game.pipes

import org.junit.Assert.*
import org.junit.Test

class PipeEngineTest {
    @Test fun rotationCyclesThroughAllDirections() {
        assertEquals(2, PipeEngine.rotate(1))
        assertEquals(4, PipeEngine.rotate(2))
        assertEquals(8, PipeEngine.rotate(4))
        assertEquals(1, PipeEngine.rotate(8))
        assertEquals(3, PipeEngine.rotate(9))
    }
    @Test fun generatedBoardsAreSolvableAndScrambled() {
        for (size in listOf(4, 6)) for (seed in 0L..99L) {
            val state = PipeEngine.newGame(size, seed)
            assertFalse(state.isComplete)
            assertTrue(state.copy(tiles = state.solution).isComplete)
            assertEquals(state, PipeEngine.newGame(size, seed))
            assertEquals(size * size - 1, state.solution.sumOf { Integer.bitCount(it) } / 2)
            state.tiles.indices.forEach { index ->
                var tile = state.tiles[index]
                val rotations = mutableSetOf<Int>()
                repeat(4) { rotations.add(tile); tile = PipeEngine.rotate(tile) }
                assertTrue(state.solution[index] in rotations)
            }
        }
    }
    @Test fun completionRejectsLeaksAndDisconnectedNetworks() {
        val square = listOf(6, 12, 3, 9)
        assertTrue(PipeEngine.noLeaks(2, square))
        assertEquals(4, PipeEngine.connected(2, square).size)
        assertFalse(PipeEngine.noLeaks(2, listOf(1, 12, 3, 9)))
        val separate = listOf(2, 8, 2, 8)
        assertTrue(PipeEngine.noLeaks(2, separate))
        assertEquals(2, PipeEngine.connected(2, separate).size)
        assertFalse(PipeState(2, 0, separate, separate).isComplete)
    }
    @Test fun undoRestoresTilesAndMoveCount() {
        val start = PipeEngine.newGame(4, 12)
        val moved = PipeEngine.turn(start, 0)
        assertEquals(1, moved.moves)
        assertEquals(start.tiles, PipeEngine.undo(moved).tiles)
        assertEquals(0, PipeEngine.undo(moved).moves)
        assertEquals(start, PipeEngine.turn(start, -1))
    }
    @Test fun hintIsLimitedAndCompletionLocksBoard() {
        val start = PipeEngine.newGame(6, 42)
        val hinted = PipeEngine.hint(PipeEngine.turn(start, 0))
        assertEquals(1, hinted.hintsUsed)
        assertTrue(hinted.history.isEmpty())
        assertEquals(hinted, PipeEngine.hint(hinted))
        assertEquals(hinted, PipeEngine.undo(hinted))
        val solved = start.copy(tiles = start.solution)
        assertEquals(solved, PipeEngine.turn(solved, 0))
    }
}
