package com.cluehaven.puzzlegames.data

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HavenStreakTest {
    private val monday = LocalDate.of(2026, 10, 26)
    private fun result(offset: Long, count: Int = 5, required: Int = 5) = HavenDailyResult(monday.plusDays(offset), count, required)
    private fun streak(offset: Long, vararg rows: HavenDailyResult) = calculateHavenStreak(rows.toList(), monday.plusDays(offset))

    @Test fun noFirstUseMeansNoMissedDatesAndNoConsumedRestDay() {
        val empty = streak(5)
        assertEquals(0, empty.currentCount)
        assertNull(empty.firstUseDate)
        assertTrue(empty.restDayAvailable)
        assertEquals(7, empty.week.size)
        assertTrue(empty.week.all { it.status == HavenDayStatus.PENDING && it.beforeFirstUse })
    }

    @Test fun firstUseMidweekDoesNotPenalizeEarlierDates() {
        val first = streak(3, result(3, count = 1))
        assertEquals(monday.plusDays(3), first.firstUseDate)
        assertEquals(0, first.currentCount)
        assertTrue(first.restDayAvailable)
        assertTrue(first.week.take(3).all { it.beforeFirstUse && it.status == HavenDayStatus.PENDING })
        assertFalse(first.week[3].beforeFirstUse)
        assertEquals(HavenDayStatus.PENDING, first.week[3].status)
        val tomorrow = streak(4, result(3, count = 1))
        assertEquals(HavenDayStatus.FORGIVEN, tomorrow.week[3].status)
        assertEquals(0, tomorrow.currentCount)
    }

    @Test fun firstMissPreservesCountWithoutIncrementingIt() {
        val rows = arrayOf(result(0), result(2))
        val afterRest = streak(2, *rows)
        assertEquals(2, afterRest.currentCount)
        assertFalse(afterRest.restDayAvailable)
        assertEquals(listOf(HavenDayStatus.COMPLETED, HavenDayStatus.FORGIVEN, HavenDayStatus.COMPLETED),
            afterRest.week.take(3).map { it.status })
        assertEquals(2, streak(3, *rows).currentCount) // Today stays pending; there is no extra day for the rest.
    }

    @Test fun secondMissBreaksCountAndDoesNotReplenishTheWeeksForgiveness() {
        val value = streak(5, result(0), result(3), result(5))
        assertEquals(1, value.currentCount)
        assertFalse(value.restDayAvailable)
        assertEquals(listOf(HavenDayStatus.COMPLETED, HavenDayStatus.FORGIVEN, HavenDayStatus.MISSED,
            HavenDayStatus.COMPLETED, HavenDayStatus.MISSED, HavenDayStatus.COMPLETED, HavenDayStatus.PENDING),
            value.week.map { it.status })
    }

    @Test fun todayNeverConsumesRestOrBreaksTheStreakBeforeMidnight() {
        val rows = arrayOf(result(0), result(2))
        val today = streak(3, *rows)
        assertEquals(2, today.currentCount)
        assertFalse(today.restDayAvailable) // Tuesday already used this week's forgiveness.
        assertEquals(HavenDayStatus.PENDING, today.week[3].status)
        val tomorrow = streak(4, *rows)
        assertEquals(0, tomorrow.currentCount)
        assertEquals(HavenDayStatus.MISSED, tomorrow.week[3].status)
        assertEquals(HavenDayStatus.PENDING, tomorrow.week[4].status)
    }

    @Test fun qualifyingTodayCountsImmediatelyAndOnlyOnceOnRecomputation() {
        val rows = listOf(result(0), result(1, count = 2))
        val pending = calculateHavenStreak(rows, monday.plusDays(1))
        assertEquals(1, pending.currentCount)
        assertTrue(pending.restDayAvailable)
        val completed = rows.dropLast(1) + result(1)
        val won = calculateHavenStreak(completed, monday.plusDays(1))
        assertEquals(2, won.currentCount)
        assertTrue(won.restDayAvailable)
        assertEquals(HavenDayStatus.COMPLETED, won.week[1].status)
        repeat(3) { assertEquals(won, calculateHavenStreak(completed, monday.plusDays(1))) }
    }

    @Test fun consecutiveSundayAndMondayMissesCanBothBeForgivenInDifferentWeeks() {
        val rows = arrayOf(result(4), result(5))
        val sunday = streak(6, *rows)
        assertEquals(2, sunday.currentCount)
        assertTrue(sunday.restDayAvailable)
        val mondayAfter = streak(7, *rows)
        assertEquals(2, mondayAfter.currentCount)
        assertTrue(mondayAfter.restDayAvailable)
        assertEquals(monday.plusWeeks(1), mondayAfter.week.first().date)
        assertEquals(HavenDayStatus.PENDING, mondayAfter.week.first().status)
        val tuesdayAfter = streak(8, *rows)
        assertEquals(2, tuesdayAfter.currentCount)
        assertFalse(tuesdayAfter.restDayAvailable)
        assertEquals(HavenDayStatus.FORGIVEN, tuesdayAfter.week.first().status)
    }

    @Test fun newWeekRestDayIsAvailableAfterLastWeeksBreak() {
        val value = streak(9, result(0), result(7))
        assertEquals(1, value.currentCount)
        assertFalse(value.restDayAvailable)
        assertEquals(HavenDayStatus.COMPLETED, value.week[0].status)
        assertEquals(HavenDayStatus.FORGIVEN, value.week[1].status)
        assertEquals(HavenDayStatus.PENDING, value.week[2].status)
        assertTrue(streak(7, result(0), result(7)).restDayAvailable)
    }

    @Test fun reopeningAfterSeveralDaysProcessesEveryGapInOrder() {
        val value = streak(4, result(0), result(4))
        assertEquals(1, value.currentCount)
        assertFalse(value.restDayAvailable)
        assertEquals(listOf(HavenDayStatus.COMPLETED, HavenDayStatus.FORGIVEN,
            HavenDayStatus.MISSED, HavenDayStatus.MISSED, HavenDayStatus.COMPLETED), value.week.take(5).map { it.status })
    }

    @Test fun longGapsAcrossMultipleWeeksBreakTheOldStreakAndProcessTheCurrentWeek() {
        val value = streak(22, result(0), result(22))
        assertEquals(1, value.currentCount)
        assertFalse(value.restDayAvailable)
        assertEquals(monday.plusDays(21), value.week.first().date)
        assertEquals(HavenDayStatus.FORGIVEN, value.week[0].status)
        assertEquals(HavenDayStatus.COMPLETED, value.week[1].status)
    }

    @Test fun eachDateUsesItsSavedRequirementRatherThanTheNewConfig() {
        val lowered = streak(1, result(0, count = 3, required = 5), result(1, count = 3, required = 3))
        assertEquals(1, lowered.currentCount)
        assertEquals(HavenDayStatus.FORGIVEN, lowered.week[0].status)
        assertEquals(HavenDayStatus.COMPLETED, lowered.week[1].status)
        val raised = streak(1, result(0, count = 3, required = 3), result(1, count = 3, required = 5))
        assertEquals(1, raised.currentCount)
        assertTrue(raised.restDayAvailable)
        assertEquals(HavenDayStatus.COMPLETED, raised.week[0].status)
        assertEquals(HavenDayStatus.PENDING, raised.week[1].status)
    }

    @Test fun completeWeekCarriesItsCountIntoTheNextWeekWithoutResettingIt() {
        val rows = (0L..6L).map { result(it) }
        assertEquals(7, calculateHavenStreak(rows, monday.plusDays(6)).currentCount)
        val nextMonday = calculateHavenStreak(rows, monday.plusWeeks(1))
        assertEquals(7, nextMonday.currentCount)
        assertTrue(nextMonday.restDayAvailable)
        assertTrue(nextMonday.week.all { it.status == HavenDayStatus.PENDING })
    }

    @Test fun movingTheCurrentDateBackIgnoresFutureResultsWithoutChangingSavedDates() {
        val rows = listOf(result(0), result(1, count = 0), result(2))
        assertEquals(2, calculateHavenStreak(rows, monday.plusDays(2)).currentCount)
        val back = calculateHavenStreak(rows, monday.plusDays(1))
        assertEquals(1, back.currentCount)
        assertTrue(back.restDayAvailable)
        assertEquals(HavenDayStatus.PENDING, back.week[2].status)
        val beforeFirstUse = calculateHavenStreak(rows, monday.minusDays(1))
        assertEquals(0, beforeFirstUse.currentCount)
        assertTrue(beforeFirstUse.restDayAvailable)
        assertTrue(beforeFirstUse.week.all { it.beforeFirstUse })
        assertEquals(2, calculateHavenStreak(rows, monday.plusDays(2)).currentCount)
    }

    @Test fun savedResultOrderDoesNotAffectRecomputationOrModifyInputs() {
        val rows = listOf(result(0), result(2), result(4))
        val copy = rows.toList()
        assertEquals(calculateHavenStreak(rows, monday.plusDays(5)), calculateHavenStreak(rows.reversed(), monday.plusDays(5)))
        assertEquals(copy, rows)
    }

    @Test fun invalidResultsAndAmbiguousDuplicateDatesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { HavenDailyResult(monday, -1, 5) }
        assertThrows(IllegalArgumentException::class.java) { HavenDailyResult(monday, 1, 0) }
        assertThrows(IllegalArgumentException::class.java) { streak(0, result(0), result(0, count = 3, required = 3)) }
    }
}
