package com.cluehaven.puzzlegames.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressStatsTest {
    private val today = LocalDate.of(2026, 10, 30)

    @Test fun emptyHistoryShowsZeroProgress() {
        val stats = summarizeSudoku(emptyList(), today)
        assertEquals(0, stats.total)
        assertEquals(0, stats.dailyStreak)
        assertFalse(stats.todayComplete)
    }

    @Test fun countsDifficultiesAndAssistedSolves() {
        val stats = summarizeSudoku(listOf(
            completion("free-1", "EASY", "FREE", 0),
            completion("free-2", "MEDIUM", "FREE", 1),
            completion("daily-2026-10-30", "MEDIUM", "DAILY", 0),
            completion("free-3", "HARD", "FREE", 2),
        ), today)
        assertEquals(4, stats.total)
        assertEquals(1, stats.easy)
        assertEquals(2, stats.medium)
        assertEquals(1, stats.hard)
        assertEquals(2, stats.solvedWithHints)
        assertEquals(1, stats.dailySolved)
        assertTrue(stats.todayComplete)
    }

    @Test fun streakIncludesYesterdayUntilTodaysPuzzleIsSolved() {
        val completions = listOf(
            completion("daily-2026-10-29", "MEDIUM", "DAILY", 0),
            completion("daily-2026-10-28", "MEDIUM", "DAILY", 0),
        )
        assertEquals(2, summarizeSudoku(completions, today).dailyStreak)
        assertEquals(3, summarizeSudoku(completions +
            completion("daily-2026-10-30", "MEDIUM", "DAILY", 0), today).dailyStreak)
        assertEquals(0, summarizeSudoku(listOf(
            completion("daily-2026-10-27", "MEDIUM", "DAILY", 0),
        ), today).dailyStreak)
    }

    private fun completion(id: String, difficulty: String, mode: String, hints: Int) =
        SudokuCompletion(id, difficulty, mode, 0L, hints)
}
