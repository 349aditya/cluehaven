package com.cluehaven.puzzlegames.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One saved date and the rule that applied when that date was first opened. */
data class HavenDailyResult(val date: LocalDate, val completedGames: Int, val requiredGamesForStreak: Int) {
    init {
        require(completedGames >= 0)
        require(requiredGamesForStreak > 0)
    }
    val qualifies: Boolean get() = completedGames >= requiredGamesForStreak
}

enum class HavenDayStatus { COMPLETED, FORGIVEN, PENDING, MISSED }

data class HavenWeekDay(val date: LocalDate, val status: HavenDayStatus, val beforeFirstUse: Boolean = false)

data class HavenStreak(
    val today: LocalDate,
    val currentCount: Int,
    val restDayAvailable: Boolean,
    val week: List<HavenWeekDay>,
    val firstUseDate: LocalDate?,
) {
    val countLabel: String get() = "$currentCount " + if (currentCount == 1) "day" else "days"
    val restLabel: String get() = if (restDayAvailable) "1 rest day available this week" else "Rest day used this week"
}

/**
 * No clock, config reads, writes, cached cursor, or rewards. Missing dates after first use
 * have zero completions. Today is completed as soon as it qualifies, otherwise pending.
 */
fun calculateHavenStreak(results: List<HavenDailyResult>, today: LocalDate): HavenStreak {
    require(results.map { it.date }.distinct().size == results.size) { "Expected one saved result per date" }
    val saved = results.associateBy { it.date }
    val firstUse = results.minOfOrNull { it.date }
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week = (0L..6L).map { offset ->
        val date = weekStart.plusDays(offset)
        HavenWeekDay(date, HavenDayStatus.PENDING, beforeFirstUse = firstUse == null || date.isBefore(firstUse))
    }.toMutableList()
    var count = 0
    var activeWeek: LocalDate? = null
    var forgivenessUsed = false
    var date = firstUse ?: today
    while (!date.isAfter(today) && firstUse != null) {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (monday != activeWeek) {
            activeWeek = monday
            forgivenessUsed = false
        }
        val status = when {
            saved[date]?.qualifies == true -> { count++; HavenDayStatus.COMPLETED }
            date == today -> HavenDayStatus.PENDING
            !forgivenessUsed -> { forgivenessUsed = true; HavenDayStatus.FORGIVEN }
            else -> { count = 0; HavenDayStatus.MISSED }
        }
        if (monday == weekStart) week[date.dayOfWeek.value - 1] = HavenWeekDay(date, status)
        date = date.plusDays(1)
    }
    return HavenStreak(today, count, activeWeek != weekStart || !forgivenessUsed, week, firstUse)
}
