package com.cluehaven.puzzlegames.data

import com.cluehaven.puzzlegames.ui.PlayTracker
import org.junit.Assert.*
import org.junit.Test

class PlayRecordsTest {
    private fun record(id: String = "one", time: Long = 1000, category: String = "5×5") =
        PlayRecord(id, "Nonogram", category, "Picture", elapsedMillis = time, completedAt = 123L)

    @Test fun clockAccumulatesOnlyForegroundIntervalsAndRestoresSavedTime() {
        var now = 100L
        val clock = ActivePlayClock(2000) { now }
        clock.resume()
        now += 1500
        clock.resume() // Recomposition must not restart the interval.
        assertEquals(3500L, clock.elapsed())
        clock.pause()
        now += 90_000
        assertEquals(3500L, clock.elapsed())
        clock.resume()
        now += 500
        clock.pause()
        assertEquals(4000L, clock.elapsed())
        val restored = ActivePlayClock(clock.elapsed()) { now }
        restored.resume()
        now += 1000
        assertEquals(5000L, restored.elapsed())
    }

    @Test fun bestsExcludeHintsUnknownTimeUnfinishedAndSeparateCategories() {
        val bests = personalBests(listOf(
            record("slow", 8000), record("best", 5000), record("assisted", 10).copy(hintsUsed = 1),
            record("old", 0).copy(timeKnown = false), record("unfinished", 5).copy(completedAt = null),
            record("large", 12000, "10×10"), record("tile", 3).copy(game = "Tile Merge"),
            record("blocks", 2).copy(game = "Block Garden"),
        ))
        assertEquals(setOf("best", "large"), bests.map { it.id }.toSet())
    }

    @Test fun completionFreezesTimeAndKeepsOneStableTimestampOnRepeatedSaves() {
        var now = 0L
        var wall = 100L
        val writes = mutableMapOf<String, PlayRecord>()
        val initial = record().copy(elapsedMillis = 0, completedAt = null)
        val tracker = PlayTracker(initial, { now }, { writes[it.id] = it }, { wall })
        tracker.update(initial, false, true)
        now = 2500
        tracker.update(initial.copy(hintsUsed = 1), true, true)
        wall = 999
        now += 60_000
        tracker.update(initial.copy(hintsUsed = 1), true, true)
        tracker.pause()
        assertEquals(1, writes.size)
        assertEquals(2500L, writes.values.single().elapsedMillis)
        assertEquals(100L, writes.values.single().completedAt)
        assertEquals(1, writes.values.single().hintsUsed)
    }

    @Test fun gameOverUndoReopensSameRunAndResumesTimer() {
        var now = 0L
        val initial = record().copy(game = "Tile Merge", completedAt = null, elapsedMillis = 0)
        val tracker = PlayTracker(initial, { now }, {}) { 123L }
        tracker.update(initial, false, true)
        now = 2000
        tracker.update(initial, true, true)
        now = 10000
        tracker.update(initial, false, true)
        assertNull(tracker.record.completedAt)
        now = 11000
        tracker.update(initial, true, true)
        assertEquals(3000L, tracker.record.elapsedMillis)
    }

    @Test fun pausePersistsImmediatelyAndTimeFormattingHandlesLongGames() {
        var now = 0L
        var saved: PlayRecord? = null
        val initial = record().copy(completedAt = null, elapsedMillis = 0)
        val tracker = PlayTracker(initial, { now }, { saved = it })
        tracker.update(initial, false, true)
        now = 1234
        tracker.pause()
        assertEquals(1234L, saved!!.elapsedMillis)
        assertEquals("00:00", formatPlayTime(-5))
        assertEquals("01:05", formatPlayTime(65_999))
        assertEquals("1:00:01", formatPlayTime(3_601_000))
    }
}
