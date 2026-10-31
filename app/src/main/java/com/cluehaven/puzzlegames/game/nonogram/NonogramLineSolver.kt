package com.cluehaven.puzzlegames.game.nonogram

enum class NonogramSolveStatus { SOLVED, STALLED, CONTRADICTION, LIMIT_REACHED, CANCELLED }
enum class NonogramTechnique { OVERLAP, LINE_COMPLETION, CONSTRAINED_OVERLAP }

data class NonogramSolveReport(
    val status: NonogramSolveStatus,
    val passes: Int,
    val overlapCells: Int,
    val completionCells: Int,
    val constrainedCells: Int,
    val work: Int,
    /** -1 unknown, 0 empty, 1 filled. The solver never reads the original picture. */
    val cells: List<Int>,
) {
    val techniques: Set<NonogramTechnique> get() = buildSet {
        if (overlapCells > 0) add(NonogramTechnique.OVERLAP)
        if (completionCells > 0) add(NonogramTechnique.LINE_COMPLETION)
        if (constrainedCells > 0) add(NonogramTechnique.CONSTRAINED_OVERLAP)
    }
    fun difficulty(size: Int): NonogramDifficulty {
        require(status == NonogramSolveStatus.SOLVED)
        return when {
            passes <= 2 && constrainedCells == 0 -> NonogramDifficulty.EASY
            passes <= 3 && constrainedCells <= size -> NonogramDifficulty.MEDIUM
            else -> NonogramDifficulty.HARD
        }
    }
}

/** Complete line-domain intersection only: no board branching, guesses, or solution oracle. */
object NonogramLineSolver {
    fun solve(rows: List<List<Int>>, columns: List<List<Int>>, maxWork: Int = 50_000,
        maxPasses: Int = 100, stopRequested: () -> Boolean = { false }): NonogramSolveReport {
        val size = rows.size
        require(size in setOf(5, 10) && columns.size == size)
        require(maxWork >= 0 && maxPasses >= 0)
        val cells = MutableList(size * size) { -1 }
        var work = 0
        var passes = 0
        var overlap = 0
        var completion = 0
        var constrained = 0
        fun report(status: NonogramSolveStatus) = NonogramSolveReport(status, passes, overlap, completion,
            constrained, work, cells.toList())
        fun limit(): NonogramSolveStatus? = when {
            stopRequested() -> NonogramSolveStatus.CANCELLED
            work >= maxWork -> NonogramSolveStatus.LIMIT_REACHED
            else -> null
        }
        val cache = mutableMapOf<List<Int>, List<Int>>()
        fun patterns(clue: List<Int>): List<Int> {
            return cache.getOrPut(clue) {
                if (clue == listOf(0)) listOf(0)
                else {
                    require(clue.isNotEmpty() && clue.all { it > 0 } && clue.sum() + clue.size - 1 <= size)
                    buildList {
                        fun append(run: Int, from: Int, mask: Int) {
                            val length = clue[run]
                            val tail = clue.drop(run + 1).sum() + clue.size - run - 1
                            for (start in from..size - length - tail) {
                                if (limit() != null) return
                                work++
                                val next = mask or (((1 shl length) - 1) shl start)
                                if (run == clue.lastIndex) add(next)
                                else append(run + 1, start + length + 1, next)
                            }
                        }
                        append(0, 0, 0)
                    }
                }
            }
        }
        limit()?.let { return report(it) }
        val domains = (rows + columns).map { clue ->
            val options = patterns(clue)
            limit()?.let { return report(it) }
            options
        }.toMutableList()
        while (passes < maxPasses) {
            limit()?.let { return report(it) }
            passes++
            var changed = false
            for (line in 0 until size * 2) {
                val indices = (0 until size).map { offset -> if (line < size) line * size + offset else offset * size + line - size }
                var filled = 0
                var empty = 0
                indices.forEachIndexed { bit, index ->
                    when (cells[index]) { 1 -> filled = filled or (1 shl bit); 0 -> empty = empty or (1 shl bit) }
                }
                val compatible = ArrayList<Int>()
                for (mask in domains[line]) {
                    limit()?.let { return report(it) }
                    work++
                    if (mask and filled == filled && mask and empty == 0) compatible.add(mask)
                }
                if (compatible.isEmpty()) return report(NonogramSolveStatus.CONTRADICTION)
                domains[line] = compatible
                val all = compatible.reduce(Int::and)
                val any = compatible.reduce(Int::or)
                indices.forEachIndexed { bit, index ->
                    if (cells[index] == -1) {
                        val value = when { all and (1 shl bit) != 0 -> 1; any and (1 shl bit) == 0 -> 0; else -> -1 }
                        if (value != -1) {
                            cells[index] = value
                            changed = true
                            when {
                                compatible.size == 1 -> completion++
                                filled == 0 && empty == 0 -> overlap++
                                else -> constrained++
                            }
                        }
                    }
                }
            }
            if (cells.none { it == -1 }) return report(NonogramSolveStatus.SOLVED)
            if (!changed) return report(NonogramSolveStatus.STALLED)
        }
        return report(NonogramSolveStatus.LIMIT_REACHED)
    }
}
