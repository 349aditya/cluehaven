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
        assertEquals(40, NonogramEngine.puzzles.size)
        assertEquals(20, NonogramEngine.puzzles.count { it.size == 5 })
        assertEquals(20, NonogramEngine.puzzles.count { it.size == 10 })
        NonogramEngine.puzzles.forEach { puzzle ->
            assertEquals(puzzle.id, 1, countSolutions(puzzle))
        }
    }

    @Test fun originalEightBoardsKeepTheirIdsAndSolutions() {
        val original = listOf("0111011111111110111000100", "0010001110111110111000100",
            "0010001110111111010111111", "0010001110111110010001110")
        for (size in listOf(5, 10)) original.forEachIndexed { index, pattern ->
            val puzzle = NonogramEngine.puzzles.single { it.id == "pack-$size-$index" }
            val scale = size / 5
            val expected = List(size * size) { cell -> pattern[cell / size / scale * 5 + cell % size / scale].digitToInt() }
            assertEquals(puzzle.id, expected, puzzle.solution)
        }
    }

    @Test fun everyBoardHasADistinctLayoutAndStableSourceIdentity() {
        val puzzles = NonogramEngine.puzzles
        assertEquals(puzzles.size, puzzles.map { it.id }.toSet().size)
        assertEquals(puzzles.size, puzzles.map { it.size to it.solution }.toSet().size)
        assertTrue(puzzles.all { it.sourceId == it.id && it.number in 1..20 && it.title != "Picture" })
        assertEquals(NonogramDifficulty.entries.toSet(), puzzles.map { it.difficulty }.toSet())
    }

    @Test fun historicalDailyRotationIsUnchanged() {
        for (day in 0L..30L) {
            val date = LocalDate.of(2026, 10, 30).minusDays(day)
            val index = Math.floorMod(date.toEpochDay(), 8L).toInt()
            val original = NonogramEngine.puzzles.single { it.id == "pack-${if (index < 4) 5 else 10}-${index % 4}" }
            assertEquals(original.solution, NonogramEngine.daily(date).solution)
        }
    }

    @Test fun expandedDailyRotationVisitsEveryBoardExactlyOnce() {
        val start = LocalDate.of(2026, 10, 31)
        val daily = (0L until NonogramEngine.puzzles.size.toLong()).map { NonogramEngine.daily(start.plusDays(it)) }
        assertEquals(NonogramEngine.puzzles.map { it.sourceId }.toSet(), daily.map { it.sourceId }.toSet())
        assertEquals(daily.size, daily.map { it.id }.toSet().size)
    }

    @Test fun oldAndNewDailySavesRestoreTheirOriginalSource() {
        val date = LocalDate.of(2026, 11, 20)
        val requested = NonogramEngine.daily(date)
        val old = NonogramEngine.puzzleForSavedSession(requested, null)
        assertEquals(NonogramEngine.legacyDaily(date), old)
        val savedSource = NonogramEngine.puzzles.last()
        val restored = NonogramEngine.puzzleForSavedSession(requested, savedSource.id)
        assertEquals(savedSource.solution, restored.solution)
        assertEquals(requested.id, restored.id)
        assertEquals(savedSource.id, restored.sourceId)
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
