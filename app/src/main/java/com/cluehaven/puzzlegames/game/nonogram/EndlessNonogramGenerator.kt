package com.cluehaven.puzzlegames.game.nonogram

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class EndlessNonogramInfo(val seed: Long, val generatorVersion: Int, val family: String,
    val attempt: Int, val report: NonogramSolveReport)

data class EndlessGenerationLimits(val maxAttempts: Int = 4_096, val maxSolverWork: Int = 50_000,
    val maxTotalWork: Int = 4_000_000) {
    init { require(maxAttempts >= 0 && maxSolverWork >= 0 && maxTotalWork >= 0) }
}

enum class EndlessGenerationFailure { EXHAUSTED, TIMEOUT, CANCELLED }
sealed interface EndlessGenerationResult {
    data class Ready(val puzzle: NonogramPuzzle) : EndlessGenerationResult
    data class Failed(val reason: EndlessGenerationFailure, val attempts: Int) : EndlessGenerationResult
}

/** Version 1 is frozen: its PRNG, grammar, filters, solver and rating form one contract. */
object EndlessNonogramGenerator {
    const val VERSION = 1
    fun generate(seed: Long, size: Int, difficulty: NonogramDifficulty, generatorVersion: Int = VERSION,
        limits: EndlessGenerationLimits = EndlessGenerationLimits(), stopRequested: () -> Boolean = { false },
        expired: () -> Boolean = { false }): EndlessGenerationResult {
        require(size in setOf(5, 10) && generatorVersion == VERSION)
        val random = PatternRandom(seed xor (size.toLong() shl 32) xor ((difficulty.ordinal + 1L) * 104729L))
        var totalWork = 0
        var attempts = 0
        for (attempt in 1..limits.maxAttempts) {
            if (expired()) return EndlessGenerationResult.Failed(EndlessGenerationFailure.TIMEOUT, attempt - 1)
            if (stopRequested()) return EndlessGenerationResult.Failed(EndlessGenerationFailure.CANCELLED, attempt - 1)
            if (totalWork >= limits.maxTotalWork) break
            attempts = attempt
            val (family, picture) = candidate(size, random)
            if (!acceptsPicture(size, picture)) continue
            var timedOut = false
            val report = NonogramLineSolver.solve(
                (0 until size).map { NonogramEngine.clues(picture.subList(it * size, (it + 1) * size)) },
                (0 until size).map { c -> NonogramEngine.clues((0 until size).map { r -> picture[r * size + c] }) },
                maxWork = min(limits.maxSolverWork, limits.maxTotalWork - totalWork), stopRequested = {
                    timedOut = expired()
                    timedOut || stopRequested()
                })
            totalWork += report.work
            if (report.status == NonogramSolveStatus.CANCELLED) return EndlessGenerationResult.Failed(
                if (timedOut) EndlessGenerationFailure.TIMEOUT else EndlessGenerationFailure.CANCELLED, attempt)
            // A smaller budget may fail a request, but must never skip an undecided
            // candidate and return a different puzzle for the same seed/version.
            if (report.status == NonogramSolveStatus.LIMIT_REACHED)
                return EndlessGenerationResult.Failed(EndlessGenerationFailure.EXHAUSTED, attempt)
            if (report.status != NonogramSolveStatus.SOLVED || report.cells != picture || report.difficulty(size) != difficulty) continue
            val id = "endless-v$generatorVersion-$size-${difficulty.name.lowercase()}-${java.lang.Long.toUnsignedString(seed, 16)}"
            return EndlessGenerationResult.Ready(NonogramPuzzle(id, size, picture, family, difficulty,
                endless = EndlessNonogramInfo(seed, generatorVersion, family, attempt, report)))
        }
        return EndlessGenerationResult.Failed(EndlessGenerationFailure.EXHAUSTED, attempts)
    }

    /** Quality gate independent of the solver; generated pictures keep an axis of symmetry. */
    internal fun acceptsPicture(size: Int, picture: List<Int>): Boolean {
        if (size !in setOf(5, 10) || picture.size != size * size || picture.any { it !in 0..1 }) return false
        val filled = picture.sum()
        if (filled < size * size * .24 || filled > size * size * .76) return false
        val rows = picture.chunked(size)
        val columns = (0 until size).map { c -> (0 until size).map { r -> picture[r * size + c] } }
        if (rows.distinct().size < 3 || columns.distinct().size < 3) return false
        if (rows.count { it.sum() == 0 } > size / 3 || columns.count { it.sum() == 0 } > size / 3) return false
        val symmetric = rows.all { it == it.reversed() } || columns.all { it == it.reversed() }
        if (!symmetric) return false
        fun neighbors(index: Int): List<Int> = buildList {
            val row = index / size; val column = index % size
            if (row > 0) add(index - size); if (row < size - 1) add(index + size)
            if (column > 0) add(index - 1); if (column < size - 1) add(index + 1)
        }
        val live = picture.indices.filter { picture[it] == 1 }
        if (live.count { index -> neighbors(index).none { picture[it] == 1 } } > max(1, size / 3)) return false
        if (live.sumOf { index -> 4 - neighbors(index).count { picture[it] == 1 } } > filled * 2 + size) return false
        val unseen = live.toMutableSet()
        var largest = 0
        while (unseen.isNotEmpty()) {
            val queue = ArrayDeque<Int>().apply { add(unseen.first().also { unseen.remove(it) }) }
            var count = 0
            while (queue.isNotEmpty()) {
                val cell = queue.removeFirst(); count++
                neighbors(cell).forEach { if (unseen.remove(it)) queue.add(it) }
            }
            largest = max(largest, count)
        }
        return largest * 4 >= filled * 3
    }

    private fun candidate(size: Int, random: PatternRandom): Pair<String, List<Int>> {
        val picture = MutableList(size * size) { 0 }
        val middle = (size - 1) / 2
        fun pair(row: Int, column: Int, value: Int = 1) {
            if (row in 0 until size && column in 0 until size) {
                picture[row * size + column] = value
                picture[row * size + size - 1 - column] = value
            }
        }
        fun band(row: Int, radius: Int, hollow: Boolean = false) {
            for (column in (middle - radius).coerceAtLeast(0)..middle) {
                if (!hollow || column == (middle - radius).coerceAtLeast(0)) pair(row, column)
            }
        }
        val family = random.nextInt(5)
        when (family) {
            0 -> { // Canopy, trunk and pot.
                for (row in 0 until size - 2) band(row, (row / 2 + random.nextInt(2)).coerceAtMost(middle))
                band(size - 2, 0); band(size - 1, 1 + random.nextInt(max(1, middle)))
            }
            1 -> { // Flower head, stem and paired leaves.
                val head = max(3, size * 2 / 3)
                for (row in 0 until head) band(row, (middle - abs(row - head / 2) + random.nextInt(2)).coerceIn(0, middle),
                    hollow = row > 0 && row < head - 1 && random.nextInt(3) == 0)
                for (row in head until size) { band(row, 0); if (row % 2 == 0) band(row, 1 + random.nextInt(max(1, middle))) }
            }
            2 -> { // Crystal/ribbon, with seeded hollow strokes.
                var radius = random.nextInt(middle + 1)
                for (row in 0 until size) {
                    radius = (radius + random.nextInt(3) - 1).coerceIn(0, middle)
                    band(row, radius, random.nextInt(3) == 0)
                }
            }
            3 -> { // Butterfly: central body and mirrored wings.
                for (row in 0 until size) {
                    band(row, 0)
                    val reach = 1 + random.nextInt(max(1, middle))
                    for (column in middle - reach..middle - 1) if (column >= 0 && (row % 3 != 0 || column == middle - reach)) pair(row, column)
                }
            }
            else -> { // Geometric crest: connected bands with paired openings.
                for (row in 0 until size) band(row, random.nextInt(middle + 1), random.nextInt(4) == 0)
            }
        }
        // Small, mirrored stroke edits vary the motif; never sample a random grid.
        repeat(1 + random.nextInt(size)) {
            val row = random.nextInt(size)
            val column = random.nextInt(middle + 1)
            pair(row, column, 1 - picture[row * size + column])
        }
        val transpose = random.nextInt(2) == 0
        val flip = random.nextInt(2) == 0
        val transformed = List(size * size) { index ->
            var row = index / size; var column = index % size
            if (transpose) { val old = row; row = column; column = old }
            if (flip) row = size - 1 - row
            picture[row * size + column]
        }
        return listOf("Little tree", "Garden flower", "Crystal", "Butterfly", "Garden crest")[family] to transformed
    }

    /** SplitMix64 specified here, independent of platform/library Random implementations. */
    private class PatternRandom(private var state: Long) {
        fun nextInt(bound: Int): Int {
            require(bound > 0)
            state += -7046029254386353131L
            var value = state
            value = (value xor (value ushr 30)) * -4658895280553007687L
            value = (value xor (value ushr 27)) * -7723592293110705685L
            value = value xor (value ushr 31)
            return ((value ushr 1) % bound).toInt()
        }
    }
}
