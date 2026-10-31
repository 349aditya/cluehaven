package com.cluehaven.puzzlegames

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cluehaven.puzzlegames.data.*
import com.cluehaven.puzzlegames.game.daily.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HavenPersistenceTest {
    private val date = LocalDate.of(2026, 10, 31)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun viewingHomeAndHavenProgressDoesNotCreateADayOrAwardAnything() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, CluehavenDatabase::class.java).build()
        try {
            val repo = DailyHavenRepository(db)
            repeat(3) { assertEquals(0, HavenProgress.from(date, repo.observeEntries(date).first()).doneCount) }
            assertNull(db.dailyHaven().day(date.toString()))
            assertEquals(0, db.dailyHaven().completedCount(date.toString()))
        } finally { db.close() }
    }

    @Test fun reopeningPartialAndCompleteGardensNeverReawardsGrowthOrTheBonus() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, CluehavenDatabase::class.java).build()
        try {
            val repo = DailyHavenRepository(db)
            repo.open(date)
            val wins = havenGameOrder.map { game ->
                val row = repo.start(date, game, DailyDifficulty.EASY)
                when (val state = DailyCodec.decode(game, row.currentPayload)) {
                    is DailyState.Sudoku -> DailyState.Sudoku(state.value.copy(values = state.value.puzzle.solution))
                    is DailyState.Nonogram -> DailyState.Nonogram(state.value.copy(cells = state.value.puzzle.solution))
                    is DailyState.Pipes -> DailyState.Pipes(state.value.copy(tiles = state.value.solution))
                    is DailyState.Tile -> DailyState.Tile(state.value.copy(score = row.target))
                    is DailyState.Blocks -> DailyState.Blocks(state.value.copy(score = row.target))
                }
            }
            wins.take(3).forEach { assertTrue(repo.save(date, it).newCompletion) }
            val partial = db.dailyHaven().entries(date.toString())
            repeat(3) {
                assertEquals(3, HavenProgress.from(date, DailyHavenRepository(db).observeEntries(date).first()).doneCount)
                assertEquals(partial, db.dailyHaven().entries(date.toString()))
                assertNull(db.dailyHaven().day(date.toString())!!.bonusAwardedAt)
            }
            repo.save(date, wins[3])
            assertTrue(repo.save(date, wins[4]).newBonus)
            val completedRows = db.dailyHaven().entries(date.toString())
            val completedDay = db.dailyHaven().day(date.toString())
            repeat(3) {
                assertEquals(5, HavenProgress.from(date, repo.observeEntries(date).first()).doneCount)
                assertEquals(completedRows, db.dailyHaven().entries(date.toString()))
                assertEquals(completedDay, db.dailyHaven().day(date.toString()))
            }
            val replay = repo.save(date, wins.last())
            assertFalse(replay.newCompletion)
            assertFalse(replay.newBonus)
        } finally { db.close() }
    }
}
