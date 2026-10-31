package com.cluehaven.puzzlegames.data

fun isEndlessNonogram(record: PlayRecord) = record.game == "Nonogram" && record.category.startsWith("Endless · ")

data class EndlessNonogramStats(val solved: Int, val smallSolved: Int, val largeSolved: Int)

fun endlessNonogramStats(history: List<PlayRecord>): EndlessNonogramStats {
    val solved = history.filter { isEndlessNonogram(it) && it.completedAt != null }.distinctBy { it.id }
    return EndlessNonogramStats(solved.size, solved.count { it.category.startsWith("Endless · 5×5 ·") },
        solved.count { it.category.startsWith("Endless · 10×10 ·") })
}
