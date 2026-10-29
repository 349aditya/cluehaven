package com.cluehaven.puzzlegames.game.sudoku

import kotlin.random.Random

enum class SudokuDifficulty { EASY, MEDIUM, HARD }
enum class SudokuMode { FREE, DAILY }

data class SudokuPuzzle(
    val id: String,
    val difficulty: SudokuDifficulty,
    val givens: List<Int>,
    val solution: List<Int>,
)

data class SudokuSnapshot(val values: List<Int>, val notes: List<Int>)

data class SudokuState(
    val puzzle: SudokuPuzzle,
    val mode: SudokuMode,
    val values: List<Int> = puzzle.givens,
    val notes: List<Int> = List(81) { 0 },
    val selected: Int = 0,
    val noteMode: Boolean = false,
    val hintsUsed: Int = 0,
    val history: List<SudokuSnapshot> = emptyList(),
) {
    val isComplete: Boolean get() = values == puzzle.solution
}

object SudokuEngine {
    // Each layout has one solution. The labels reflect the techniques needed:
    // Easy: naked singles; Medium: hidden singles; Hard: more than singles.
    private val layouts = mapOf(
        SudokuDifficulty.EASY to "003400709450709020780000000234500891007801200000000560040600002070900345912000678",
        SudokuDifficulty.MEDIUM to "100400700056000003089100450234060800067890030001000007305600000000900040000045078",
        SudokuDifficulty.HARD to "100056700056000023000000406204007090007000200890000060000000000670000005000345008",
    )

    fun generate(difficulty: SudokuDifficulty, seed: Long, id: String = "free-$seed"): SudokuPuzzle {
        val random = Random(seed)
        val digits = (1..9).shuffled(random)
        val rows = (0..2).shuffled(random).flatMap { band ->
            (band * 3..band * 3 + 2).shuffled(random)
        }
        val columns = (0..2).shuffled(random).flatMap { stack ->
            (stack * 3..stack * 3 + 2).shuffled(random)
        }
        val transpose = random.nextBoolean()
        val layout = layouts.getValue(difficulty)
        val givens = MutableList(81) { 0 }
        val solution = MutableList(81) { 0 }
        for (row in 0..8) for (column in 0..8) {
            val sourceRow = if (transpose) columns[column] else rows[row]
            val sourceColumn = if (transpose) rows[row] else columns[column]
            val source = sourceRow * 9 + sourceColumn
            val target = row * 9 + column
            val given = layout[source].digitToInt()
            val answer = (sourceRow * 3 + sourceRow / 3 + sourceColumn) % 9
            givens[target] = if (given == 0) 0 else digits[given - 1]
            solution[target] = digits[answer]
        }
        return SudokuPuzzle(id, difficulty, givens, solution)
    }

    fun select(state: SudokuState, cell: Int): SudokuState =
        if (cell in 0..80) state.copy(selected = cell) else state

    fun enter(state: SudokuState, digit: Int): SudokuState {
        if (digit !in 1..9 || state.isComplete || state.puzzle.givens[state.selected] != 0) return state
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
