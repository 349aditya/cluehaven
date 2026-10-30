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
                val size = if (difficulty == SudokuDifficulty.EASY) 6 else 9
                assertEquals(size, puzzle.size)
                assertEquals(size * size, puzzle.givens.size)
                assertEquals(size * size, puzzle.solution.size)
                assertEquals(1, countSolutions(puzzle.givens.toMutableList(), puzzle))
                puzzle.givens.indices.forEach { cell ->
                    assertTrue(puzzle.givens[cell] == 0 ||
                        puzzle.givens[cell] == puzzle.solution[cell])
                }
                assertTrue(puzzle.solution.all { it in 1..size })
                for (row in 0 until size) {
                    assertEquals((1..size).toSet(),
                        (0 until size).map { puzzle.solution[row * size + it] }.toSet())
                }
                for (column in 0 until size) {
                    assertEquals((1..size).toSet(),
                        (0 until size).map { puzzle.solution[it * size + column] }.toSet())
                }
                for (boxRow in 0 until size / puzzle.boxRows) {
                    for (boxColumn in 0 until size / puzzle.boxColumns) {
                        val values = (0 until size).map { offset ->
                            val row = boxRow * puzzle.boxRows + offset / puzzle.boxColumns
                            val column = boxColumn * puzzle.boxColumns + offset % puzzle.boxColumns
                            puzzle.solution[row * size + column]
                        }
                        assertEquals((1..size).toSet(), values.toSet())
                    }
                }
            }
        }
    }

    @Test fun difficultyMatchesRequiredSinglesTechnique() {
        SudokuDifficulty.entries.forEach { difficulty ->
            val puzzle = SudokuEngine.generate(difficulty, 1234L)
            val nakedOnly = solveSingles(puzzle, includeHidden = false)
            val withHidden = solveSingles(puzzle, includeHidden = true)
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

    @Test fun sixBySixEntryNotesUndoAndHintRules() {
        val puzzle = SudokuEngine.generate(SudokuDifficulty.EASY, 7L)
        val blank = puzzle.givens.indexOfFirst { it == 0 }
        val given = puzzle.givens.indexOfFirst { it != 0 }
        val start = SudokuState(puzzle, SudokuMode.FREE)
        assertEquals(36, start.notes.size)
        val selectedGiven = SudokuEngine.select(start, given)
        assertEquals(selectedGiven, SudokuEngine.enter(selectedGiven, 3))
        val selected = SudokuEngine.select(start, blank)
        assertEquals(selected, SudokuEngine.enter(selected, 7))
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

    @Test fun legacyNineByNineEasyPuzzleRemainsUsable() {
        val solution = (0 until 81).map { cell ->
            val row = cell / 9
            val column = cell % 9
            (row * 3 + row / 3 + column) % 9 + 1
        }
        val givens = "003400709450709020780000000234500891007801200000000560040600002070900345912000678"
            .map { it.digitToInt() }
        val puzzle = SudokuPuzzle("legacy", SudokuDifficulty.EASY, givens, solution)
        val state = SudokuState(puzzle, SudokuMode.FREE)
        assertEquals(9, puzzle.size)
        assertEquals(81, state.notes.size)
        assertEquals(1, countSolutions(givens.toMutableList(), puzzle))
    }

    @Test fun sameSeedProducesSameDailyPuzzle() {
        val first = SudokuEngine.generate(SudokuDifficulty.MEDIUM, 21000L, "daily-test")
        val second = SudokuEngine.generate(SudokuDifficulty.MEDIUM, 21000L, "daily-test")
        assertEquals(first, second)
    }

    private fun peers(cell: Int, puzzle: SudokuPuzzle): Set<Int> {
        val size = puzzle.size
        val row = cell / size
        val column = cell % size
        return (0 until size * size).filter { other ->
            other != cell && (other / size == row || other % size == column ||
                (other / size / puzzle.boxRows == row / puzzle.boxRows &&
                    other % size / puzzle.boxColumns == column / puzzle.boxColumns))
        }.toSet()
    }

    private fun choices(board: List<Int>, cell: Int, puzzle: SudokuPuzzle): List<Int> {
        val used = peers(cell, puzzle).map { board[it] }.toSet()
        return (1..puzzle.size).filter { it !in used }
    }

    private fun countSolutions(board: MutableList<Int>, puzzle: SudokuPuzzle, cap: Int = 2): Int {
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return 1
        val cell = empty.minBy { choices(board, it, puzzle).size }
        val options = choices(board, cell, puzzle)
        if (options.isEmpty()) return 0
        var count = 0
        for (digit in options) {
            board[cell] = digit
            count += countSolutions(board, puzzle, cap - count)
            if (count >= cap) break
        }
        board[cell] = 0
        return count
    }

    private fun solveSingles(puzzle: SudokuPuzzle, includeHidden: Boolean): Boolean {
        val size = puzzle.size
        val board = puzzle.givens.toMutableList()
        val units = buildList {
            for (row in 0 until size) add((0 until size).map { row * size + it })
            for (column in 0 until size) add((0 until size).map { it * size + column })
            for (br in 0 until size / puzzle.boxRows) {
                for (bc in 0 until size / puzzle.boxColumns) {
                    add((0 until size).map {
                        (br * puzzle.boxRows + it / puzzle.boxColumns) * size +
                            bc * puzzle.boxColumns + it % puzzle.boxColumns
                    })
                }
            }
        }
        while (true) {
            val naked = board.indices.firstOrNull {
                board[it] == 0 && choices(board, it, puzzle).size == 1
            }
            if (naked != null) {
                board[naked] = choices(board, naked, puzzle).single()
                continue
            }
            if (!includeHidden) break
            var move: Pair<Int, Int>? = null
            for (unit in units) for (digit in 1..size) {
                if (unit.any { board[it] == digit }) continue
                val cells = unit.filter {
                    board[it] == 0 && digit in choices(board, it, puzzle)
                }
                if (cells.size == 1) { move = cells.single() to digit; break }
            }
            if (move == null) break
            board[move.first] = move.second
        }
        return board.all { it != 0 }
    }
}
