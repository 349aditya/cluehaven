package com.cluehaven.puzzlegames.ui

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class CurrentDateTest {
    @Test fun refreshesAtMidnightWithoutReopeningTheScreen() = runBlocking {
        var time = ZonedDateTime.parse("2026-10-30T23:59:59.995+05:30[Asia/Kolkata]")
        val waits = mutableListOf<Long>()
        val dates = localDates(now = { time }, wait = {
            waits.add(it)
            time = time.plusNanos(it * 1_000_000)
        }).take(2).toList()
        assertEquals(listOf(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31)), dates)
        assertEquals(listOf(5L), waits)
    }

    @Test fun catchesUpAfterSuspensionAcrossMultipleDays() = runBlocking {
        var time = ZonedDateTime.parse("2026-10-30T15:00:00+05:30[Asia/Kolkata]")
        val dates = localDates(now = { time }, wait = { time = time.plusDays(3) }).take(2).toList()
        assertEquals(listOf(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2)), dates)
    }

    @Test fun respectsTimeZoneChanges() = runBlocking {
        var time = ZonedDateTime.parse("2026-10-30T23:30:00-10:00[Pacific/Honolulu]")
        val dates = localDates(now = { time }, wait = {
            time = time.withZoneSameInstant(ZoneId.of("Asia/Kolkata"))
        }).take(2).toList()
        assertEquals(listOf(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31)), dates)
    }

    @Test fun handlesDaylightSavingWithoutAssumingTwentyFourHourDays() = runBlocking {
        var time = ZonedDateTime.parse("2026-03-08T00:00:00-05:00[America/New_York]")
        var elapsed = 0L
        localDates(now = { time }, wait = {
            elapsed += it
            time = time.plusNanos(it * 1_000_000)
        }).take(2).toList()
        assertEquals(23 * 60 * 60 * 1000L, elapsed)
    }
}
