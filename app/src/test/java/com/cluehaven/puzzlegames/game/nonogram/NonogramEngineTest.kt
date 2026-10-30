package com.cluehaven.puzzlegames.game.nonogram

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class NonogramEngineTest {
    @Test fun cluesIncludeSeparatedRunsAndEmptyLines() {
        assertEquals(listOf(2, 1), NonogramEngine.clues(listOf(1, 1, 0, 1, 0)))
        assertEquals(listOf(0), NonogramEngine.clues(List(5) { 0 }))
        assertEquals(listOf(5), NonogramEngine.clues(List(5) { 1 }))
    }

    @Test fun everyStarterPuzzleHasExactlyOneSolution() {
        assertEquals(8, NonogramEngine.puzzles.size)
        NonogramEngine.puzzles.forEach { puzzle ->
            assertEquals(puzzle.id, 1, countSolutions(puzzle))
        }
    }

    @Test fun markingTogglingAndUndoRestoreTheBoard() {
        val start = NonogramState(NonogramEngine.puzzles.first())
        val filled = NonogramEngine.mark(start, 0, false)
        assertEquals(1, filled.cells[0])
        val crossed = NonogramEngine.mark(filled, 0, true)
        assertEquals(2, crossed.cells[0])
        assertEquals(filled.cells, NonogramEngine.undo(crossed).cells)
        assertEquals(0, NonogramEngine.mark(crossed, 0, true).cells[0])
        assertEquals(start, NonogramEngine.mark(start, -1, false))
    }

    @Test fun hintIsLimitedAndCannotBeUndone() {
        val start = NonogramState(NonogramEngine.puzzles.first())
        val marked = NonogramEngine.mark(start, 0, false)
        val hinted = NonogramEngine.hint(marked)
        assertEquals(2, hinted.cells[0])
        assertEquals(1, hinted.hintsUsed)
        assertTrue(hinted.history.isEmpty())
        assertEquals(hinted, NonogramEngine.undo(hinted))
        assertEquals(hinted, NonogramEngine.hint(hinted))
    }

    @Test fun completionRequiresAllFillsAndNoExtraFills() {
        val puzzle = NonogramEngine.puzzles.first()
        val solved = NonogramState(puzzle, puzzle.solution)
        assertTrue(solved.isComplete)
        assertFalse(NonogramState(puzzle).isComplete)
        val extra = puzzle.solution.toMutableList().also { it[0] = 1 }
        assertFalse(solved.copy(cells = extra).isComplete)
        assertEquals(solved, NonogramEngine.mark(solved, 0, false))
        val crossed = puzzle.solution.map { if (it == 0) 2 else 1 }
        assertTrue(solved.copy(cells = crossed).isComplete)
    }

    @Test fun dailyIsDeterministicAndHasDateIdentity() {
        val date = LocalDate.of(2026, 10, 30)
        assertEquals(NonogramEngine.daily(date), NonogramEngine.daily(date))
        assertEquals("daily-2026-10-30", NonogramEngine.daily(date).id)
        assertNotEquals(NonogramEngine.daily(date).id, NonogramEngine.daily(date.plusDays(1)).id)
        assertTrue(NonogramEngine.puzzles.any { it.solution == NonogramEngine.daily(date).solution })
    }

    // Independent exhaustive row search, pruning against all valid column prefixes.
    private fun countSolutions(puzzle: NonogramPuzzle): Int {
        val size = puzzle.size
        fun runs(line: List<Int>): List<Int> = line.joinToString("").split('0')
            .filter { it.isNotEmpty() }.map { it.length }.ifEmpty { listOf(0) }
        val lines = (0 until (1 shl size)).map { bits -> List(size) { (bits shr it) and 1 } }
        val rowOptions = puzzle.rows.map { clue -> lines.filter { runs(it) == clue } }
        val columnOptions = puzzle.columns.map { clue -> lines.filter { runs(it) == clue } }
        val board = mutableListOf<List<Int>>()
        var count = 0
        fun search(row: Int) {
            if (count >= 2) return
            if (row == size) { count++; return }
            for (line in rowOptions[row]) {
                board.add(line)
                if ((0 until size).all { column ->
                    columnOptions[column].any { candidate ->
                        (0..row).all { candidate[it] == board[it][column] }
                    }
                }) search(row + 1)
                board.removeAt(board.lastIndex)
            }
        }
        search(0)
        return count
    }
}
