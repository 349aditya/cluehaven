package com.cluehaven.puzzlegames.data

import com.cluehaven.puzzlegames.game.blocks.BlockEngine
import com.cluehaven.puzzlegames.game.sudoku.*
import com.cluehaven.puzzlegames.game.daily.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ProgressLabelsTest {
    @Test fun highScoreDoesNotImplyFinishedRun() {
        val state = BlockEngine.newGame(3).copy(score = 9140, bestScore = 9140)
        assertFalse(state.isGameOver)
        assertEquals("In progress · Score 9140", blockRunStatus(state))
    }
    @Test fun incompleteSudokuExplainsWhySolvedTotalDoesNotIncrease() {
        val puzzle = SudokuEngine.generate(SudokuDifficulty.MEDIUM, 3)
        val state = SudokuState(puzzle, SudokuMode.FREE,
            values = puzzle.solution.mapIndexed { index, value -> if (index < 17) 0 else value })
        assertFalse(state.isComplete)
        assertEquals("In progress · 17 empty cells", sudokuBoardStatus(state))
        assertEquals("Solved board saved", sudokuBoardStatus(state.copy(values = puzzle.solution)))
    }
    @Test fun dailyGoalHistoryCannotInflateRegularFinishedRunCountsOrPersonalBests() {
        val spec = DailyChallengeFactory.spec(LocalDate.of(2026, 10, 31), DailyGame.BLOCKS)
        val record = BlockEngine.newGame(3).playRecord().copy(completedAt = 123).forDailyFive(spec)
        assertEquals("Daily Five", record.game)
        assertEquals(spec.key, record.id)
        assertTrue(record.category.contains("Block Garden"))
        assertTrue(personalBests(listOf(record)).isEmpty())
    }
}
