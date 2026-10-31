package com.cluehaven.puzzlegames.ui

import com.cluehaven.puzzlegames.data.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HavenDateObserverTest {
    @Test fun midnightMakesYesterdayForgivenAndLeavesTheNewTodayPending() = runBlocking {
        var now = ZonedDateTime.parse("2026-10-30T23:59:59.995+05:30[Asia/Kolkata]")
        val saved = listOf(HavenDailyResult(LocalDate.of(2026, 10, 29), 5, 5), HavenDailyResult(LocalDate.of(2026, 10, 30), 0, 5))
        val states = localDates({ now }, { now = now.plusNanos(it * 1_000_000) })
            .map { calculateHavenStreak(saved, it) }.take(2).toList()
        assertEquals(1, states[0].currentCount)
        assertTrue(states[0].restDayAvailable)
        assertEquals(HavenDayStatus.PENDING, states[0].week[4].status)
        assertEquals(1, states[1].currentCount)
        assertFalse(states[1].restDayAvailable)
        assertEquals(HavenDayStatus.FORGIVEN, states[1].week[4].status)
        assertEquals(HavenDayStatus.PENDING, states[1].week[5].status)
    }

    @Test fun resumeAfterSeveralDaysRecomputesAllDatesEvenWhenObserverSkipsThem() = runBlocking {
        var now = ZonedDateTime.parse("2026-10-26T12:00:00+05:30[Asia/Kolkata]")
        val saved = listOf(HavenDailyResult(LocalDate.of(2026, 10, 26), 5, 5), HavenDailyResult(LocalDate.of(2026, 10, 30), 5, 5))
        val states = localDates({ now }, { now = now.plusDays(4) })
            .map { calculateHavenStreak(saved, it) }.take(2).toList()
        assertEquals(1, states[0].currentCount)
        assertEquals(1, states[1].currentCount)
        assertEquals(listOf(HavenDayStatus.FORGIVEN, HavenDayStatus.MISSED, HavenDayStatus.MISSED),
            states[1].week.subList(1, 4).map { it.status })
    }

    @Test fun timezoneChangeReevaluatesLocalTodayWithoutReinterpretingSavedResultDates() = runBlocking {
        var now = ZonedDateTime.parse("2026-10-30T23:30:00-10:00[Pacific/Honolulu]")
        val saved = listOf(HavenDailyResult(LocalDate.of(2026, 10, 29), 5, 5), HavenDailyResult(LocalDate.of(2026, 10, 30), 0, 5))
        val states = localDates({ now }, { now = now.withZoneSameInstant(ZoneId.of("Asia/Kolkata")) })
            .map { calculateHavenStreak(saved, it) }.take(2).toList()
        assertEquals(LocalDate.of(2026, 10, 30), states[0].today)
        assertEquals(LocalDate.of(2026, 10, 31), states[1].today)
        assertTrue(states[0].restDayAvailable)
        assertFalse(states[1].restDayAvailable)
        assertEquals(1, states[1].currentCount)
        assertEquals(LocalDate.of(2026, 10, 29), saved.first().date)
    }
}
