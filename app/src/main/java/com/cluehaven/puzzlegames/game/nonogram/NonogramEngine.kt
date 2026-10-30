package com.cluehaven.puzzlegames.game.nonogram

import java.time.LocalDate

data class NonogramPuzzle(val id: String, val size: Int, val solution: List<Int>) {
    init {
        require(size in setOf(5, 10) && solution.size == size * size)
        require(solution.all { it in 0..1 })
    }
    val rows: List<List<Int>> get() = (0 until size).map { row ->
        NonogramEngine.clues(solution.subList(row * size, (row + 1) * size))
    }
    val columns: List<List<Int>> get() = (0 until size).map { column ->
        NonogramEngine.clues((0 until size).map { solution[it * size + column] })
    }
}

// 0 = unknown, 1 = filled, 2 = crossed out.
data class NonogramState(
    val puzzle: NonogramPuzzle,
    val cells: List<Int> = List(puzzle.size * puzzle.size) { 0 },
    val history: List<List<Int>> = emptyList(),
    val hintsUsed: Int = 0,
) {
    val isComplete: Boolean get() = cells.indices.all {
        (cells[it] == 1) == (puzzle.solution[it] == 1)
    }
}

object NonogramEngine {
    private val patterns = listOf(
        listOf("01110", "11111", "11111", "01110", "00100"),
        listOf("00100", "01110", "11111", "01110", "00100"),
        listOf("00100", "01110", "11111", "10101", "11111"),
        listOf("00100", "01110", "11111", "00100", "01110"),
    )

    val puzzles: List<NonogramPuzzle> = listOf(5, 10).flatMap { size ->
        patterns.mapIndexed { index, pattern ->
            val scale = size / 5
            NonogramPuzzle("pack-$size-$index", size, List(size * size) { cell ->
                pattern[cell / size / scale][cell % size / scale].digitToInt()
            })
        }
    }

    fun daily(date: LocalDate): NonogramPuzzle {
        val base = puzzles[Math.floorMod(date.toEpochDay(), puzzles.size.toLong()).toInt()]
        return base.copy(id = "daily-$date")
    }

    fun clues(line: List<Int>): List<Int> {
        val runs = mutableListOf<Int>()
        var run = 0
        for (value in line + 0) {
            if (value == 1) run++
            else if (run > 0) { runs.add(run); run = 0 }
        }
        return runs.ifEmpty { listOf(0) }
    }

    fun mark(state: NonogramState, index: Int, cross: Boolean): NonogramState {
        if (state.isComplete || index !in state.cells.indices) return state
        val mark = if (cross) 2 else 1
        val cells = state.cells.toMutableList()
        cells[index] = if (cells[index] == mark) 0 else mark
        return state.copy(cells = cells, history = (state.history + listOf(state.cells)).takeLast(50))
    }

    fun undo(state: NonogramState): NonogramState {
        if (state.isComplete || state.history.isEmpty()) return state
        return state.copy(cells = state.history.last(), history = state.history.dropLast(1))
    }

    fun hint(state: NonogramState): NonogramState {
        if (state.isComplete || state.hintsUsed != 0) return state
        val index = state.cells.indices.firstOrNull {
            state.cells[it] != if (state.puzzle.solution[it] == 1) 1 else 2
        } ?: return state
        val cells = state.cells.toMutableList()
        cells[index] = if (state.puzzle.solution[index] == 1) 1 else 2
        return state.copy(cells = cells, history = emptyList(), hintsUsed = 1)
    }
}
