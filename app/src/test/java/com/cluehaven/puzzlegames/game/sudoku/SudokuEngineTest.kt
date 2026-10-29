package com.cluehaven.puzzlegames.game.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuEngineTest {
    @Test fun generatedPuzzlesHaveOneSolutionAndValidGivens() {
        SudokuDifficulty.entries.forEach { difficulty ->
            repeat(8) { seed ->
                val puzzle = SudokuEngine.generate(difficulty, seed.toLong())
                assertEquals(81, puzzle.givens.size)
                assertEquals(81, puzzle.solution.size)
                assertEquals(1, countSolutions(puzzle.givens.toMutableList()))
                puzzle.givens.indices.forEach { cell ->
                    assertTrue(puzzle.givens[cell] == 0 ||
                        puzzle.givens[cell] == puzzle.solution[cell])
                }
                assertTrue(puzzle.solution.all { it in 1..9 })
            }
        }
    }

    @Test fun difficultyMatchesRequiredSinglesTechnique() {
        SudokuDifficulty.entries.forEach { difficulty ->
            val puzzle = SudokuEngine.generate(difficulty, 1234L)
            val nakedOnly = solveSingles(puzzle.givens, includeHidden = false)
            val withHidden = solveSingles(puzzle.givens, includeHidden = true)
            when (difficulty) {
                SudokuDifficulty.EASY -> assertTrue(nakedOnly)
                SudokuDifficulty.MEDIUM -> {
                    assertFalse(nakedOnly)
                    assertTrue(withHidden)
                }
                SudokuDifficulty.HARD -> assertFalse(withHidden)
            }
        }
    }

    @Test fun entryNotesUndoAndHintRules() {
        val puzzle = SudokuEngine.generate(SudokuDifficulty.EASY, 7L)
        val blank = puzzle.givens.indexOfFirst { it == 0 }
        val given = puzzle.givens.indexOfFirst { it != 0 }
        val start = SudokuState(puzzle, SudokuMode.FREE)
        val selectedGiven = SudokuEngine.select(start, given)
        assertEquals(selectedGiven, SudokuEngine.enter(selectedGiven, 3))
        val selected = SudokuEngine.select(start, blank)
        val note = SudokuEngine.enter(selected.copy(noteMode = true), 3)
        assertTrue(note.notes[blank] and (1 shl 3) != 0)
        val entered = SudokuEngine.enter(note.copy(noteMode = false), puzzle.solution[blank])
        assertEquals(puzzle.solution[blank], entered.values[blank])
        assertEquals(0, entered.notes[blank])
        val undone = SudokuEngine.undo(entered)
        assertEquals(0, undone.values[blank])
        assertTrue(undone.notes[blank] and (1 shl 3) != 0)
        val hinted = SudokuEngine.freeHint(undone)
        assertEquals(1, hinted.hintsUsed)
        assertEquals(puzzle.solution[blank], hinted.values[blank])
        assertEquals(hinted, SudokuEngine.freeHint(hinted))
        assertEquals(hinted, SudokuEngine.undo(hinted))
    }

    @Test fun sameSeedProducesSameDailyPuzzle() {
        val first = SudokuEngine.generate(SudokuDifficulty.MEDIUM, 21000L, "daily-test")
        val second = SudokuEngine.generate(SudokuDifficulty.MEDIUM, 21000L, "daily-test")
        assertEquals(first, second)
    }

    private fun peers(cell: Int): Set<Int> {
        val row = cell / 9
        val column = cell % 9
        return (0..80).filter { other ->
            other != cell && (other / 9 == row || other % 9 == column ||
                (other / 27 == row / 3 && other % 9 / 3 == column / 3))
        }.toSet()
    }

    private fun choices(board: List<Int>, cell: Int): List<Int> {
        val used = peers(cell).map { board[it] }.toSet()
        return (1..9).filter { it !in used }
    }

    private fun countSolutions(board: MutableList<Int>, cap: Int = 2): Int {
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return 1
        val cell = empty.minBy { choices(board, it).size }
        val options = choices(board, cell)
        if (options.isEmpty()) return 0
        var count = 0
        for (digit in options) {
            board[cell] = digit
            count += countSolutions(board, cap - count)
            if (count >= cap) break
        }
        board[cell] = 0
        return count
    }

    private fun solveSingles(input: List<Int>, includeHidden: Boolean): Boolean {
        val board = input.toMutableList()
        val units = buildList {
            for (row in 0..8) add((0..8).map { row * 9 + it })
            for (column in 0..8) add((0..8).map { it * 9 + column })
            for (br in 0..2) for (bc in 0..2) add(
                (0..8).map { (br * 3 + it / 3) * 9 + bc * 3 + it % 3 }
            )
        }
        while (true) {
            val naked = board.indices.firstOrNull { board[it] == 0 && choices(board, it).size == 1 }
            if (naked != null) {
                board[naked] = choices(board, naked).single()
                continue
            }
            if (!includeHidden) break
            var move: Pair<Int, Int>? = null
            for (unit in units) for (digit in 1..9) {
                if (unit.any { board[it] == digit }) continue
                val cells = unit.filter { board[it] == 0 && digit in choices(board, it) }
                if (cells.size == 1) { move = cells.single() to digit; break }
            }
            if (move == null) break
            board[move.first] = move.second
        }
        return board.all { it != 0 }
    }
}
