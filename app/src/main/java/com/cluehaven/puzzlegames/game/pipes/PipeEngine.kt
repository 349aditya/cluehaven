package com.cluehaven.puzzlegames.game.pipes

import kotlin.random.Random

// Clockwise ports: north=1, east=2, south=4, west=8. Edges do not wrap.
data class PipeState(
    val size: Int,
    val seed: Long,
    val solution: List<Int>,
    val tiles: List<Int>,
    val moves: Int = 0,
    val hintsUsed: Int = 0,
    val history: List<List<Int>> = emptyList(),
) {
    val isComplete: Boolean get() = PipeEngine.connected(size, tiles).size == tiles.size &&
        PipeEngine.noLeaks(size, tiles)
}

object PipeEngine {
    fun rotate(mask: Int): Int = ((mask shl 1) and 15) or (mask shr 3)

    fun newGame(size: Int, seed: Long = System.nanoTime()): PipeState {
        require(size in setOf(4, 6))
        val random = Random(seed)
        val solution = MutableList(size * size) { 0 }
        val visited = mutableSetOf(0)
        fun grow(cell: Int) {
            (0..3).shuffled(random).forEach { direction ->
                val next = neighbor(size, cell, direction)
                if (next != null && visited.add(next)) {
                    solution[cell] = solution[cell] or (1 shl direction)
                    solution[next] = solution[next] or (1 shl ((direction + 2) % 4))
                    grow(next)
                }
            }
        }
        grow(0)
        val tiles = solution.map { mask ->
            var rotated = mask
            repeat(random.nextInt(4)) { rotated = rotate(rotated) }
            rotated
        }.toMutableList()
        // Never offer an already completed board.
        if (noLeaks(size, tiles)) tiles[0] = rotate(tiles[0])
        return PipeState(size, seed, solution.toList(), tiles.toList())
    }

    fun turn(state: PipeState, index: Int): PipeState {
        if (state.isComplete || index !in state.tiles.indices) return state
        val tiles = state.tiles.toMutableList().also { it[index] = rotate(it[index]) }
        return state.copy(tiles = tiles, moves = state.moves + 1,
            history = (state.history + listOf(state.tiles)).takeLast(100))
    }

    fun undo(state: PipeState): PipeState {
        if (state.isComplete || state.history.isEmpty()) return state
        return state.copy(tiles = state.history.last(), moves = (state.moves - 1).coerceAtLeast(0),
            history = state.history.dropLast(1))
    }

    fun hint(state: PipeState): PipeState {
        if (state.isComplete || state.hintsUsed != 0) return state
        val index = state.tiles.indices.firstOrNull { state.tiles[it] != state.solution[it] } ?: return state
        return state.copy(tiles = state.tiles.toMutableList().also { it[index] = state.solution[index] },
            hintsUsed = 1, history = emptyList())
    }

    fun neighbor(size: Int, cell: Int, direction: Int): Int? {
        val row = cell / size + listOf(-1, 0, 1, 0)[direction]
        val column = cell % size + listOf(0, 1, 0, -1)[direction]
        return if (row in 0 until size && column in 0 until size) row * size + column else null
    }

    fun connected(size: Int, tiles: List<Int>): Set<Int> {
        require(tiles.size == size * size)
        val reached = mutableSetOf(0)
        val queue = ArrayDeque<Int>().apply { add(0) }
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            for (direction in 0..3) {
                val next = neighbor(size, cell, direction) ?: continue
                if (tiles[cell] and (1 shl direction) != 0 &&
                    tiles[next] and (1 shl ((direction + 2) % 4)) != 0 && reached.add(next)) queue.add(next)
            }
        }
        return reached
    }

    fun noLeaks(size: Int, tiles: List<Int>): Boolean = tiles.indices.all { cell ->
        (0..3).all { direction ->
            if (tiles[cell] and (1 shl direction) == 0) true else {
                val next = neighbor(size, cell, direction)
                next != null && tiles[next] and (1 shl ((direction + 2) % 4)) != 0
            }
        }
    }
}
