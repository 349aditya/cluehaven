package com.cluehaven.puzzlegames.data

import com.cluehaven.puzzlegames.game.daily.DailyGame
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HavenProgressTest {
    private val date = LocalDate.of(2026, 10, 31)
    private fun entry(game: DailyGame, finished: Boolean = true, day: LocalDate = date) =
        DailyHavenEntry(day.toString(), game.key, 1, 1, 2, "snapshot", "snapshot", 100,
            completedAt = if (finished) 100 else null)

    @Test fun noSavedCompletionsLeavesAllFiveItemsWaiting() {
        val progress = HavenProgress.from(date, emptyList())
        assertEquals(0, progress.doneCount)
        assertEquals(havenGameOrder, progress.waiting)
        assertTrue(progress.grown.isEmpty())
        assertEquals("0 of 5 done", progress.countLabel)
        assertTrue(progress.sceneDescription.contains("Grown: none yet"))
    }

    @Test fun onlyCompletedGamesForThisDateGrowTheirMappedItems() {
        val rows = listOf(entry(DailyGame.SUDOKU), entry(DailyGame.NONOGRAM), entry(DailyGame.PIPES),
            entry(DailyGame.TILE_MERGE, finished = false), entry(DailyGame.BLOCKS, day = date.minusDays(1)),
            entry(DailyGame.BLOCKS, day = date.plusDays(1)))
        val progress = HavenProgress.from(date, rows)
        assertEquals(listOf("Tree", "Flowers", "Water channel"), progress.grown.map { it.growth })
        assertEquals(listOf("Lanterns", "Garden bed"), progress.waiting.map { it.growth })
        assertEquals("3 of 5 grown today", progress.grownLabel)
        assertEquals("Lanterns and garden bed are waiting to grow.", progress.waitingMessage)
        assertEquals(progress, HavenProgress.from(date, rows + rows))
    }

    @Test fun reopeningAndReorderingSavedEntriesRebuildsTheSameCompleteGarden() {
        val rows = DailyGame.entries.map { entry(it) }
        val first = HavenProgress.from(date, rows)
        val reopened = HavenProgress.from(date, rows.reversed())
        assertEquals(first, reopened)
        assertEquals(5, reopened.doneCount)
        assertTrue(reopened.waiting.isEmpty())
        assertEquals(havenGameOrder, reopened.grown)
        assertTrue(reopened.waitingMessage.contains("All five grown"))
        assertEquals(0, HavenProgress.from(date.plusDays(1), rows).doneCount)
    }

    @Test fun unfinishedOrUnknownEntriesCannotPretendToBeEarnedGrowth() {
        val unfinished = DailyGame.entries.map { entry(it, finished = false) }
        val unknown = entry(DailyGame.SUDOKU).copy(gameId = "unknown")
        assertEquals(0, HavenProgress.from(date, unfinished + unknown).doneCount)
        assertEquals("Loading today's progress…", HavenProgress(date, loaded = false).countLabel)
    }
}
