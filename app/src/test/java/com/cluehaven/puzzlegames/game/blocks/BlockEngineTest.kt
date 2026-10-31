package com.cluehaven.puzzlegames.game.blocks

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class BlockEngineTest {
    @Test fun fastAvailabilityMatchesAllPlacementsOnSparseAndCrowdedBoards() {
        val random = Random(812)
        repeat(100) { sample ->
            val board = List(64) { if (random.nextInt(100) < sample) random.nextInt(1, 5) else 0 }
            BlockEngine.shapes.forEach { shape ->
                assertEquals("${shape.id} at occupancy $sample", BlockEngine.placements(board, shape).isNotEmpty(),
                    BlockEngine.hasPlacement(board, shape))
            }
        }
    }

    private fun state(board: List<Int> = List(64) { 0 }, tray: List<String?> = listOf("dot", "dot", "h2")) =
        BlockEngine.newGame(7).copy(board = board, tray = tray)

    @Test fun shapesHaveDistinctNormalizedCellsAndValidBounds() {
        assertEquals(BlockEngine.shapes.size, BlockEngine.shapes.map { it.id }.toSet().size)
        assertEquals(BlockEngine.shapes.size, BlockEngine.shapes.map { it.cells.toSet() }.toSet().size)
        BlockEngine.shapes.forEach {
            assertEquals(0, it.cells.minOf { cell -> cell.row })
            assertEquals(0, it.cells.minOf { cell -> cell.column })
            assertEquals(it.cells.size, it.cells.toSet().size)
            assertTrue(it.color in 1..4)
        }
    }

    @Test fun placementRejectsOverlapAndEveryOutsideEdgeWithoutMutatingState() {
        val game = state(List(64) { if (it == 0) 1 else 0 }, listOf("square2", "dot", null))
        listOf(-1 to 0, 0 to -1, 7 to 0, 0 to 7, 0 to 0).forEach { (r, c) ->
            assertEquals(game, BlockEngine.place(game, 0, r, c))
        }
        assertEquals(game, BlockEngine.place(game, 2, 3, 3))
        assertEquals(game, BlockEngine.place(game, -1, 3, 3))
        val placed = BlockEngine.place(game, 0, 2, 3)
        assertEquals(5, placed.board.count { it != 0 })
        assertEquals(40, placed.score)
        assertNull(placed.tray[0])
        assertEquals(1, placed.moves)
    }

    @Test fun crossingRowAndColumnClearTogetherAndCountIntersectionOnce() {
        val board = List(64) { if ((it / 8 == 7 || it % 8 == 7) && it != 63) 1 else 0 }
        val next = BlockEngine.place(state(board), 0, 7, 7)
        assertTrue(next.board.all { it == 0 })
        assertEquals(2, next.lines)
        assertEquals(15, next.lastCleared.size)
        assertEquals(410, next.score)
    }

    @Test fun consecutiveClearsIncreaseBonusAndNonClearResetsCombo() {
        val board = List(64) { if (it / 8 < 2 && it % 8 < 7) 1 else 0 }
        val first = BlockEngine.place(state(board), 0, 0, 7)
        val second = BlockEngine.place(first, 1, 1, 7)
        assertEquals(1, first.combo)
        assertEquals(110, first.score)
        assertEquals(2, second.combo)
        assertEquals(320, second.score)
        assertEquals(0, BlockEngine.place(second, 2, 4, 4).combo)
    }

    @Test fun trayRefillsOnlyAfterAllThreePiecesAndUndoRestoresDealExactly() {
        var game = state(tray = listOf("dot", "dot", "dot"))
        game = BlockEngine.place(game, 0, 0, 0)
        assertEquals(listOf(null, "dot", "dot"), game.tray)
        game = BlockEngine.place(game, 1, 0, 1)
        val dealt = BlockEngine.place(game, 2, 0, 2)
        assertTrue(dealt.tray.all { it != null })
        val undo = BlockEngine.undo(dealt)
        assertEquals(game.board, undo.board)
        assertEquals(game.tray, undo.tray)
        assertEquals(game.rngState, undo.rngState)
        assertEquals(dealt.bestScore, undo.bestScore)
        val replay = BlockEngine.place(undo, 2, 0, 2)
        assertEquals(dealt.tray, replay.tray)
        assertEquals(dealt.rngState, replay.rngState)
        assertEquals(replay, BlockEngine.undo(replay))
    }

    @Test fun gameOverChecksAllRemainingPiecesAndUndoCanReopenRun() {
        val board = List(64) { if (it % 2 == it / 8 % 2) 1 else 0 }
        assertTrue(state(board, listOf("square2", "h2", "v2")).isGameOver)
        assertFalse(state(board, listOf("square2", null, "dot")).isGameOver)
        val almost = state(List(64) { if (it / 8 == 0 || it % 8 == 0) 0 else 1 }, listOf("dot", "square3", null))
        val over = BlockEngine.place(almost, 0, 0, 0)
        assertTrue(over.isGameOver)
        assertFalse(BlockEngine.undo(over).isGameOver)
    }

    @Test fun freshGamesAreDeterministicAndIntroduceSmallerShapesFirst() {
        assertEquals(BlockEngine.newGame(123), BlockEngine.newGame(123))
        assertNotEquals(BlockEngine.newGame(123).runId, BlockEngine.newGame(124).runId)
        repeat(100) { seed ->
            val game = BlockEngine.newGame(seed.toLong())
            assertFalse(game.isGameOver)
            assertTrue(game.tray.filterNotNull().all { BlockEngine.shape(it).cells.size <= 4 })
        }
    }

    @Test fun freshTraysHaveAPlayableSequenceOnSparseAndCrowdedBoards() {
        val random = Random(123)
        repeat(24) { seed ->
            // No full lines: include sparse, crowded and checkerboard-like positions.
            val board = List(64) { index -> if (index % 8 == index / 8 || random.nextInt(100) > seed * 3) 0 else 1 }
            val deal = BlockEngine.deal(board, seed.toLong(), moves = 30)
            assertTrue("No legal sequence for seed $seed", hasSequence(board, deal.tray.filterNotNull()))
        }
    }

    private fun hasSequence(board: List<Int>, pieces: List<String>): Boolean {
        if (pieces.isEmpty()) return true
        return pieces.indices.any { slot ->
            val shape = BlockEngine.shape(pieces[slot])
            BlockEngine.placements(board, shape).any { cell ->
                val next = board.toMutableList()
                shape.cells.forEach { next[(cell.row + it.row) * 8 + cell.column + it.column] = 1 }
                val fullRows = (0..7).filter { r -> (0..7).all { next[r * 8 + it] != 0 } }
                val fullColumns = (0..7).filter { c -> (0..7).all { next[it * 8 + c] != 0 } }
                for (i in next.indices) if (i / 8 in fullRows || i % 8 in fullColumns) next[i] = 0
                hasSequence(next, pieces.filterIndexed { index, _ -> index != slot })
            }
        }
    }
}
