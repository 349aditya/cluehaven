package com.cluehaven.puzzlegames

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cluehaven.puzzlegames.data.*
import com.cluehaven.puzzlegames.game.daily.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HavenStreakPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val date = LocalDate.of(2026, 10, 31)
    private suspend fun withStore(block: suspend (CluehavenDatabase, DailyHavenRepository) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(context, CluehavenDatabase::class.java).build()
        try { block(db, DailyHavenRepository(db)) } finally { db.close() }
    }
    private suspend fun win(repo: DailyHavenRepository, day: LocalDate, game: DailyGame) {
        val row = repo.start(day, game, DailyDifficulty.EASY)
        val won = when (val state = DailyCodec.decode(game, row.currentPayload)) {
            is DailyState.Sudoku -> DailyState.Sudoku(state.value.copy(values = state.value.puzzle.solution))
            is DailyState.Nonogram -> DailyState.Nonogram(state.value.copy(cells = state.value.puzzle.solution))
            is DailyState.Pipes -> DailyState.Pipes(state.value.copy(tiles = state.value.solution))
            is DailyState.Tile -> DailyState.Tile(state.value.copy(score = row.target))
            is DailyState.Blocks -> DailyState.Blocks(state.value.copy(score = row.target))
        }
        repo.save(day, won)
    }

    @Test fun reopeningKeepsSavedRequirementsAndCountsEachKnownCompletedGameOnce() = runBlocking { withStore { db, repo ->
        val yesterday = date.minusDays(1)
        repo.open(yesterday)
        repo.open(date)
        db.openHelper.writableDatabase.execSQL("UPDATE daily_haven_days SET requiredGamesForStreak = 3 WHERE date = ?", arrayOf(date.toString()))
        havenGameOrder.take(3).forEach { win(repo, yesterday, it); win(repo, date, it) }
        val unknown = db.dailyHaven().entries(date.toString()).first().copy(gameId = "unknown")
        db.dailyHaven().insertEntry(unknown)
        repeat(3) {
            val reopened = DailyHavenRepository(db)
            reopened.open(date) // INSERT IGNORE must preserve the saved three-game requirement.
            val saved = reopened.observeHaven(date).first()
            assertEquals(listOf(5, 3), saved.dailyResults.map { it.requiredGamesForStreak })
            assertEquals(listOf(3, 3), saved.dailyResults.map { it.completedGames })
            val streak = calculateHavenStreak(saved.dailyResults, date)
            assertEquals(1, streak.currentCount)
            assertFalse(streak.restDayAvailable)
            assertEquals(HavenDayStatus.FORGIVEN, streak.week[4].status)
            assertEquals(HavenDayStatus.COMPLETED, streak.week[5].status)
        }
        assertNull(db.dailyHaven().day(date.toString())!!.bonusAwardedAt)
        assertTrue(db.progress().observeSudokuCompletions().first().isEmpty())
    } }

    @Test fun liveSnapshotKeepsGrowthAndStreakCountsConsistentDuringCompletions() = runBlocking { withStore { db, repo ->
        repo.open(date)
        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(10_000) {
                repo.observeHaven(date).onEach { saved ->
                    assertEquals(saved.entries.count { it.completedAt != null }, saved.dailyResults.single().completedGames)
                }.first { it.dailyResults.single().completedGames == 5 }
            }
        }
        havenGameOrder.forEach { win(repo, date, it) }
        val saved = observed.await()
        assertEquals(1, HavenProgress.from(date, saved.entries, saved.dailyResults).streak.currentCount)
        val before = db.dailyHaven().day(date.toString())!!
        assertNotNull(before.bonusAwardedAt)
        repeat(3) { assertEquals(saved, repo.observeHaven(date).first()) }
        assertEquals(before, db.dailyHaven().day(date.toString()))
    } }

    @Test fun emptyFirstDaySurvivesTheSummaryAndEarlierDatesStayNeutral() = runBlocking { withStore { db, repo ->
        repo.open(date)
        val saved = repo.observeHaven(date).first()
        assertEquals(listOf(HavenDailyResult(date, 0, 5)), saved.dailyResults)
        val value = calculateHavenStreak(saved.dailyResults, date)
        assertEquals(date, value.firstUseDate)
        assertTrue(value.restDayAvailable)
        assertTrue(value.week.take(5).all { it.beforeFirstUse && it.status == HavenDayStatus.PENDING })
        assertEquals(0, db.dailyHaven().completedCount(date.toString()))
    } }
}
