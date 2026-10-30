package com.cluehaven.puzzlegames.game.sudoku

import kotlin.random.Random

enum class SudokuDifficulty { EASY, MEDIUM, HARD }
enum class SudokuMode { FREE, DAILY }

data class SudokuPuzzle(
    val id: String,
    val difficulty: SudokuDifficulty,
    val givens: List<Int>,
    val solution: List<Int>,
) {
    val size: Int get() = when (givens.size) {
        36 -> 6
        81 -> 9
        else -> error("Unsupported Sudoku grid size")
    }
    val boxRows: Int get() = if (size == 6) 2 else 3
    val boxColumns: Int get() = 3
}

data class SudokuSnapshot(val values: List<Int>, val notes: List<Int>)

data class SudokuState(
    val puzzle: SudokuPuzzle,
    val mode: SudokuMode,
    val values: List<Int> = puzzle.givens,
    val notes: List<Int> = List(puzzle.givens.size) { 0 },
    val selected: Int = 0,
    val noteMode: Boolean = false,
    val hintsUsed: Int = 0,
    val history: List<SudokuSnapshot> = emptyList(),
) {
    val isComplete: Boolean get() = values == puzzle.solution
}

object SudokuEngine {
    // Each layout has one solution. The labels reflect the techniques needed:
    // Easy is a 6x6 grid solved by naked singles. Medium and Hard use 9x9 grids.
    private val layouts = mapOf(
        SudokuDifficulty.EASY to "000050006123230061560234000602610000",
        SudokuDifficulty.MEDIUM to "100400700056000003089100450234060800067890030001000007305600000000900040000045078",
        SudokuDifficulty.HARD to "100056700056000023000000406204007090007000200890000060000000000670000005000345008",
    )

    fun generate(difficulty: SudokuDifficulty, seed: Long, id: String = "free-$seed"): SudokuPuzzle {
        val random = Random(seed)
        val size = if (difficulty == SudokuDifficulty.EASY) 6 else 9
        val boxRows = if (size == 6) 2 else 3
        val boxColumns = 3
        val digits = (1..size).shuffled(random)
        val rows = (0 until size / boxRows).shuffled(random).flatMap { band ->
            (band * boxRows until band * boxRows + boxRows).shuffled(random)
        }
        val columns = (0 until size / boxColumns).shuffled(random).flatMap { stack ->
            (stack * boxColumns until stack * boxColumns + boxColumns).shuffled(random)
        }
        val transpose = size == 9 && random.nextBoolean()
        val layout = layouts.getValue(difficulty)
        val givens = MutableList(size * size) { 0 }
        val solution = MutableList(size * size) { 0 }
        for (row in 0 until size) for (column in 0 until size) {
            val sourceRow = if (transpose) columns[column] else rows[row]
            val sourceColumn = if (transpose) rows[row] else columns[column]
            val source = sourceRow * size + sourceColumn
            val target = row * size + column
            val given = layout[source].digitToInt()
            val answer = (sourceRow * boxColumns + sourceRow / boxRows + sourceColumn) % size
            givens[target] = if (given == 0) 0 else digits[given - 1]
            solution[target] = digits[answer]
        }
        return SudokuPuzzle(id, difficulty, givens, solution)
    }

    fun select(state: SudokuState, cell: Int): SudokuState =
        if (cell in state.values.indices) state.copy(selected = cell) else state

    fun enter(state: SudokuState, digit: Int): SudokuState {
        if (digit !in 1..state.puzzle.size || state.isComplete ||
            state.puzzle.givens[state.selected] != 0) return state
        val cell = state.selected
        if (state.noteMode) {
            if (state.values[cell] != 0) return state
            val notes = state.notes.toMutableList()
            notes[cell] = notes[cell] xor (1 shl digit)
            return state.copy(notes = notes, history = pushHistory(state))
        }
        if (state.values[cell] == digit) return state
        val values = state.values.toMutableList().also { it[cell] = digit }
        val notes = state.notes.toMutableList().also { it[cell] = 0 }
        return state.copy(values = values, notes = notes, history = pushHistory(state))
    }

    fun erase(state: SudokuState): SudokuState {
        if (state.isComplete || state.puzzle.givens[state.selected] != 0) return state
        val cell = state.selected
        if (state.values[cell] == 0 && state.notes[cell] == 0) return state
        val values = state.values.toMutableList().also { it[cell] = 0 }
        val notes = state.notes.toMutableList().also { it[cell] = 0 }
        return state.copy(values = values, notes = notes, history = pushHistory(state))
    }

    fun undo(state: SudokuState): SudokuState {
        val previous = state.history.lastOrNull() ?: return state
        return state.copy(
            values = previous.values,
            notes = previous.notes,
            history = state.history.dropLast(1),
        )
    }

    fun freeHint(state: SudokuState): SudokuState {
        if (state.hintsUsed != 0 || state.isComplete) return state
        val cell = if (state.puzzle.givens[state.selected] == 0 &&
            state.values[state.selected] != state.puzzle.solution[state.selected]
        ) state.selected else state.values.indices.firstOrNull {
            state.puzzle.givens[it] == 0 && state.values[it] != state.puzzle.solution[it]
        } ?: return state
        val values = state.values.toMutableList().also { it[cell] = state.puzzle.solution[cell] }
        val notes = state.notes.toMutableList().also { it[cell] = 0 }
        // A hint cannot be undone to receive it again.
        return state.copy(values = values, notes = notes, selected = cell, hintsUsed = 1, history = emptyList())
    }

    private fun pushHistory(state: SudokuState): List<SudokuSnapshot> =
        (state.history + SudokuSnapshot(state.values, state.notes)).takeLast(30)
}
