package com.cluehaven.puzzlegames.data

import com.cluehaven.puzzlegames.game.daily.DailyGame
import java.time.LocalDate

val havenGameOrder = listOf(DailyGame.SUDOKU, DailyGame.NONOGRAM, DailyGame.PIPES,
    DailyGame.TILE_MERGE, DailyGame.BLOCKS)

/** Read-only projection. Showing a garden never runs a completion or bonus transaction. */
data class HavenProgress(val date: LocalDate, val completed: Set<DailyGame> = emptySet(), val loaded: Boolean = true) {
    val doneCount: Int get() = completed.size
    val countLabel: String get() = if (loaded) "$doneCount of 5 done" else "Loading today's progress…"
    val grownLabel: String get() = if (loaded) "$doneCount of 5 grown today" else "Loading your Haven…"
    val grown: List<DailyGame> get() = havenGameOrder.filter { it in completed }
    val waiting: List<DailyGame> get() = havenGameOrder.filter { it !in completed }
    val sceneDescription: String get() = "Your Haven. $grownLabel. " +
        "Grown: ${grown.joinToString { it.growth.lowercase() }.ifEmpty { "none yet" }}. " +
        "Waiting: ${waiting.joinToString { it.growth.lowercase() }.ifEmpty { "none" }}."
    val waitingMessage: String get() = when {
        !loaded -> "Your saved garden will appear in a moment."
        waiting.isEmpty() -> "All five grown. Your daily bonus is saved."
        grown.isEmpty() -> "Five little puzzles. A little more life in your Haven."
        else -> {
            val names = waiting.map { it.growth.lowercase() }
            val subject = if (names.size == 1) names.first() else names.dropLast(1).joinToString(", ") + " and " + names.last()
            subject.replaceFirstChar { it.uppercase() } + if (names.size == 1) " is waiting to grow." else " are waiting to grow."
        }
    }

    companion object {
        fun from(date: LocalDate, entries: List<DailyHavenEntry>): HavenProgress = HavenProgress(date,
            entries.asSequence().filter { it.date == date.toString() && it.completedAt != null }
                .mapNotNull { row -> DailyGame.entries.firstOrNull { it.key == row.gameId } }.toSet())
    }
}
