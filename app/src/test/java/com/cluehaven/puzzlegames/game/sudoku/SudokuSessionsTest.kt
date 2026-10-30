package com.cluehaven.puzzlegames.game.sudoku

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SudokuSessionsTest {
    private val yesterday = LocalDate.of(2026, 10, 30)
    private val today = yesterday.plusDays(1)

    @Test fun rolloverPreservesTheDailyBoardBeingPlayed() {
        val old = resolveSudokuSession(null, "daily", null, null, yesterday)!!
        val inProgress = old.copy(selected = 12, hintsUsed = 1)
        val resolved = resolveSudokuSession(inProgress, "daily", null, null, today)
        assertSame(inProgress, resolved)
        assertEquals("daily-$yesterday", resolved!!.puzzle.id)
    }

    @Test fun openingDailyAfterRolloverSelectsTodaysPuzzle() {
        val stale = resolveSudokuSession(null, "daily", null, null, yesterday)
        val refreshed = resolveSudokuSession(null, "daily", null, stale, today)!!
        assertEquals("daily-$today", refreshed.puzzle.id)
        assertEquals(SudokuEngine.generate(SudokuDifficulty.MEDIUM, today.toEpochDay(), "daily-$today"), refreshed.puzzle)
        assertNotEquals(stale!!.puzzle.id, refreshed.puzzle.id)
    }

    @Test fun currentDaySavedBoardResumesWithProgress() {
        val saved = resolveSudokuSession(null, "daily", null, null, today)!!.copy(hintsUsed = 1)
        assertSame(saved, resolveSudokuSession(null, "daily", null, saved, today))
    }

    @Test fun rolloverDoesNotReplaceFreePlayOrOpenABoardFromTheChooser() {
        val free = SudokuState(SudokuEngine.generate(SudokuDifficulty.EASY, 42), SudokuMode.FREE)
        assertSame(free, resolveSudokuSession(free, "free", free, null, today))
        assertSame(free, resolveSudokuSession(null, "free", free, null, today))
        assertNull(resolveSudokuSession(null, "", free, null, today))
    }
}
