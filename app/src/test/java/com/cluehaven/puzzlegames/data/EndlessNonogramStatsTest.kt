package com.cluehaven.puzzlegames.data

import org.junit.Assert.*
import org.junit.Test

class EndlessNonogramStatsTest {
    @Test fun endlessTotalsExcludeThePackDailyAndUnfinishedBoardsAndCountEachPuzzleOnce() {
        fun record(id: String, category: String, completed: Long? = 1) =
            PlayRecord(id, "Nonogram", category, "Flower", completedAt = completed)
        val small = record("endless-1", "Endless · 5×5 · Easy")
        val large = record("endless-2", "Endless · 10×10 · Hard").copy(hintsUsed = 1)
        val records = listOf(small, small, large,
            record("pack-5-0", "5×5 · Easy"), record("daily-2026-10-31", "10×10 · Medium"),
            record("endless-unfinished", "Endless · 5×5 · Easy", null))
        assertEquals(EndlessNonogramStats(2, 1, 1), endlessNonogramStats(records))
    }

    @Test fun endlessPersonalBestsAreSeparatedFromPackTimesAndExcludeHints() {
        val pack = PlayRecord("pack", "Nonogram", "5×5 · Easy", "Tree", 10_000, completedAt = 1)
        val endless = pack.copy(id = "endless", category = "Endless · 5×5 · Easy", elapsedMillis = 20_000)
        val assisted = endless.copy(id = "assisted", hintsUsed = 1, elapsedMillis = 1_000)
        assertEquals(setOf("pack", "endless"), personalBests(listOf(pack, endless, assisted)).map { it.id }.toSet())
    }
}
