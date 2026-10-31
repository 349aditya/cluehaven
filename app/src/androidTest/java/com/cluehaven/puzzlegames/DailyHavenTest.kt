package com.cluehaven.puzzlegames

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cluehaven.puzzlegames.data.*
import com.cluehaven.puzzlegames.game.daily.*
import com.cluehaven.puzzlegames.game.pipes.*
import com.cluehaven.puzzlegames.game.sudoku.SudokuCodec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DailyHavenTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val date = LocalDate.of(2026, 10, 31)
    private suspend fun withStore(block: suspend (CluehavenDatabase, DailyHavenRepository) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(context, CluehavenDatabase::class.java).build()
        try { block(db, DailyHavenRepository(db)) } finally { db.close() }
    }
    private suspend fun startAll(repo: DailyHavenRepository, day: LocalDate = date): List<DailyHavenEntry> {
        repo.open(day)
        return DailyGame.entries.map { repo.start(day, it, DailyDifficulty.MEDIUM) }
    }
    private fun solved(entry: DailyHavenEntry): DailyState = when (val state = DailyCodec.decode(entry.spec.game, entry.currentPayload)) {
        is DailyState.Sudoku -> DailyState.Sudoku(state.value.copy(values = state.value.puzzle.solution))
        is DailyState.Nonogram -> DailyState.Nonogram(state.value.copy(cells = state.value.puzzle.solution))
        is DailyState.Pipes -> DailyState.Pipes(state.value.copy(tiles = state.value.solution))
        is DailyState.Tile -> DailyState.Tile(state.value.copy(score = entry.target))
        is DailyState.Blocks -> DailyState.Blocks(state.value.copy(score = entry.target))
    }

    @Test fun snapshotsRoundTripAllFiveGamesAndPersistAcrossReopen() = runBlocking { withStore { db, repo ->
        val opened = startAll(repo)
        assertEquals(5, opened.size)
        for (row in opened) {
            val state = DailyCodec.decode(row.spec.game, row.currentPayload)
            assertEquals(state, DailyCodec.decode(row.spec.game, DailyCodec.encode(state)))
        }
        assertEquals(opened.toSet(), DailyHavenRepository(db).open(date).toSet())
        val pipes = PipeEngine.turn(PipeEngine.newGame(4, 42), 0)
        val old = JSONObject(PipeCodec.encode(pipes)).apply { remove("size"); remove("solution") }.toString()
        assertEquals(pipes, PipeCodec.decode(4, old))
    } }

    @Test fun concurrentDuplicatesAwardFiveGrowthsAndOnlyOneBonus() = runBlocking { withStore { db, repo ->
        val entries = startAll(repo)
        val secondWriter = DailyHavenRepository(db)
        val results = coroutineScope { entries.flatMap { row -> List(8) { index -> async {
            (if (index % 2 == 0) repo else secondWriter).save(date, solved(row))
        } } }.awaitAll() }
        assertEquals(5, results.count { it.newCompletion })
        assertEquals(1, results.count { it.newBonus })
        assertEquals(5, db.dailyHaven().completedCount(date.toString()))
        assertNotNull(db.dailyHaven().day(date.toString())!!.bonusAwardedAt)
    } }

    @Test fun undoRetryAndReplayCannotReverseOrRepeatEarnedCompletion() = runBlocking { withStore { db, repo ->
        val row = startAll(repo).first { it.gameId == DailyGame.TILE_MERGE.key }
        val win = repo.save(date, solved(row))
        val undo = repo.save(date, DailyCodec.decode(row.spec.game, row.initialPayload))
        val retry = repo.enqueue(date, row.spec.game, null).await()
        assertTrue(win.newCompletion)
        assertFalse(undo.newCompletion)
        assertFalse(retry.newCompletion)
        assertEquals(win.entry, undo.entry)
        assertEquals(win.entry, retry.entry)
        assertEquals(win.entry, repo.start(date, row.spec.game, DailyDifficulty.HARD))
        assertEquals(1, db.dailyHaven().completedCount(date.toString()))
    } }

    @Test fun regularPlayDoesNotAwardDailyProgressOrOverwriteItsSaves() = runBlocking { withStore { db, repo ->
        val entries = startAll(repo)
        val sudoku = solved(entries.first { it.gameId == "sudoku" }) as DailyState.Sudoku
        db.puzzleSessions().save(PuzzleSession("sudoku_free", SudokuCodec.encode(sudoku.value), 123))
        db.puzzleSessions().save(PuzzleSession("block_garden", "regular-save-kept", 456))
        db.progress().addSudokuCompletion(SudokuCompletion("daily-$date", "MEDIUM", "DAILY", 123, 0))
        assertEquals(0, db.dailyHaven().completedCount(date.toString()))
        repo.save(date, solved(entries.first { it.gameId == "blocks" }))
        assertEquals("regular-save-kept", db.puzzleSessions().get("block_garden")!!.payload)
        assertEquals(1, db.progress().observeSudokuCompletions().first().size)
        assertEquals(1, summarizeSudoku(db.progress().observeSudokuCompletions().first(), date).dailyStreak)
    } }

    @Test fun savedTargetsSurviveConfigChangesAndRetriesKeepTheStartingBoard() = runBlocking { withStore { db, repo ->
        val row = startAll(repo).first { it.gameId == "tile_merge" }
        db.openHelper.writableDatabase.execSQL("UPDATE daily_haven_entries SET target = 1024 WHERE gameId = 'tile_merge'")
        val reopened = repo.open(date).first { it.gameId == "tile_merge" }
        assertEquals(1024, reopened.target)
        val under = solved(row)
        assertFalse(repo.save(date, under).newCompletion)
        val reset = repo.enqueue(date, DailyGame.TILE_MERGE, null).await()
        assertEquals(row.initialPayload, reset.entry.currentPayload)
        assertEquals(1024, reset.entry.target)
    } }

    @Test fun bonusAndFifthCompletionRollBackTogetherOnFailure() = runBlocking { withStore { db, repo ->
        val entries = startAll(repo)
        entries.take(4).forEach { repo.save(date, solved(it)) }
        db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_bonus BEFORE UPDATE OF bonusAwardedAt ON daily_haven_days
            WHEN NEW.bonusAwardedAt IS NOT NULL BEGIN SELECT RAISE(ABORT, 'test rollback'); END""")
        assertTrue(runCatching { repo.save(date, solved(entries.last())) }.isFailure)
        assertEquals(4, db.dailyHaven().completedCount(date.toString()))
        assertNull(db.dailyHaven().day(date.toString())!!.bonusAwardedAt)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_bonus")
        assertTrue(repo.save(date, solved(entries.last())).newBonus)
    } }

    @Test fun datesAreIndependentAndWrongChallengeStatesAreRejected() = runBlocking { withStore { db, repo ->
        val today = startAll(repo).first { it.gameId == "blocks" }
        val tomorrow = startAll(repo, date.plusDays(1)).first { it.gameId == "blocks" }
        assertTrue(runCatching { repo.save(date, solved(tomorrow)) }.isFailure)
        assertTrue(repo.save(date, solved(today)).newCompletion)
        assertEquals(1, db.dailyHaven().completedCount(date.toString()))
        assertEquals(0, db.dailyHaven().completedCount(date.plusDays(1).toString()))
    } }

    @Test fun migrationFromV3PreservesExistingProgressAndCreatesEmptyDailyTables() {
        val name = "daily-haven-migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE puzzle_sessions (gameId TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE sudoku_completions (puzzleId TEXT NOT NULL PRIMARY KEY, difficulty TEXT NOT NULL, mode TEXT NOT NULL, completedAt INTEGER NOT NULL, hintsUsed INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE tile_merge_milestones (runId TEXT NOT NULL PRIMARY KEY, score INTEGER NOT NULL, achievedAt INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE play_records (id TEXT NOT NULL PRIMARY KEY, game TEXT NOT NULL, category TEXT NOT NULL, title TEXT NOT NULL, elapsedMillis INTEGER NOT NULL, timeKnown INTEGER NOT NULL, completedAt INTEGER, hintsUsed INTEGER NOT NULL, score INTEGER NOT NULL)")
            old.execSQL("INSERT INTO puzzle_sessions VALUES ('block_garden', 'existing-9140-run', 123)")
            old.execSQL("INSERT INTO sudoku_completions VALUES ('daily-2026-10-30', 'MEDIUM', 'DAILY', 123, 0)")
            old.version = 3
        }
        val db = Room.databaseBuilder(context, CluehavenDatabase::class.java, name)
            .addMigrations(CluehavenDatabase.migration3to4, CluehavenDatabase.migration4to5).build()
        try {
            db.openHelper.writableDatabase.query("SELECT payload FROM puzzle_sessions").use { assertTrue(it.moveToFirst()); assertEquals("existing-9140-run", it.getString(0)) }
            db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM sudoku_completions").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM daily_haven_entries").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun openingHubDoesNotLockGamesAndConcurrentStartKeepsTheFirstChoice() = runBlocking { withStore { db, repo ->
        assertTrue(repo.open(date).isEmpty())
        assertEquals(5, db.dailyHaven().day(date.toString())!!.requiredGamesForStreak)
        val starts = coroutineScope { List(18) { index -> async {
            DailyHavenRepository(db).start(date, DailyGame.PIPES, DailyDifficulty.entries[index % 3])
        } }.awaitAll() }
        assertEquals(1, starts.toSet().size)
        val locked = starts.first()
        assertEquals(1, repo.open(date).size)
        assertEquals(locked, repo.start(date, DailyGame.PIPES, DailyDifficulty.HARD))
        val moved = (DailyCodec.decode(DailyGame.PIPES, locked.currentPayload) as DailyState.Pipes).let {
            DailyState.Pipes(PipeEngine.turn(it.value, 0))
        }
        repo.save(date, moved)
        assertEquals(DailyCodec.encode(moved), repo.start(date, DailyGame.PIPES, DailyDifficulty.EASY).currentPayload)
        val tomorrow = date.plusDays(1)
        repo.open(tomorrow)
        assertEquals(DailyDifficulty.HARD, repo.start(tomorrow, DailyGame.PIPES, DailyDifficulty.HARD).spec.difficulty)
    } }

    @Test fun allDifficultiesRoundTripAndCannotBeSwitchedAfterStart() = runBlocking { withStore { _, repo ->
        DailyDifficulty.entries.forEachIndexed { index, difficulty ->
            val day = date.plusDays(index.toLong())
            repo.open(day)
            DailyGame.entries.forEach { game ->
                val row = repo.start(day, game, difficulty)
                val state = DailyCodec.decode(game, row.currentPayload)
                assertEquals(state, DailyCodec.decode(game, DailyCodec.encode(state)))
                assertEquals(difficulty, repo.start(day, game, DailyDifficulty.EASY).spec.difficulty)
                assertEquals(row.target, repo.start(day, game, DailyDifficulty.HARD).target)
                assertTrue(repo.save(day, solved(row)).newCompletion)
                assertFalse(repo.save(day, solved(row)).newCompletion)
            }
        }
    } }

    @Test fun savedThreeGameRequirementQualifiesWithoutAwardingAllFiveBonus() = runBlocking { withStore { db, repo ->
        val rows = startAll(repo)
        db.openHelper.writableDatabase.execSQL("UPDATE daily_haven_days SET requiredGamesForStreak = 3 WHERE date = ?", arrayOf(date.toString()))
        rows.take(3).forEach { repo.save(date, solved(it)) }
        repo.open(date)
        val day = db.dailyHaven().day(date.toString())!!
        assertEquals(3, day.requiredGamesForStreak)
        val complete = db.dailyHaven().entries(date.toString()).filter { it.completedAt != null }.map { it.spec.game }.toSet()
        assertTrue(DailyRules.qualifiesForStreak(complete, day.requiredGamesForStreak))
        assertNull(day.bonusAwardedAt)
        repo.save(date, solved(rows[3]))
        assertTrue(repo.save(date, solved(rows[4])).newBonus)
    } }

    @Test fun fileBackedResumeRestoresDifficultyPuzzleAndProgressAfterDatabaseReopen() = runBlocking {
        val name = "daily-haven-resume-test.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, CluehavenDatabase::class.java, name).build()
        try {
            val repo = DailyHavenRepository(db)
            repo.open(date)
            val started = repo.start(date, DailyGame.PIPES, DailyDifficulty.HARD)
            val initial = DailyCodec.decode(DailyGame.PIPES, started.currentPayload) as DailyState.Pipes
            val moved = DailyState.Pipes(PipeEngine.turn(initial.value, 0))
            val saved = repo.save(date, moved).entry
            db.close()
            db = Room.databaseBuilder(context, CluehavenDatabase::class.java, name).build()
            val reopened = DailyHavenRepository(db)
            assertEquals(listOf(saved), reopened.open(date))
            assertEquals(saved, reopened.start(date, DailyGame.PIPES, DailyDifficulty.EASY))
            val retry = reopened.enqueue(date, DailyGame.PIPES, null).await().entry
            assertEquals(started.initialPayload, retry.currentPayload)
            assertEquals(DailyDifficulty.HARD, retry.spec.difficulty)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun migrationFromPopulatedV4PreservesSnapshotsGoalsAndAwards() = runBlocking {
        val name = "daily-haven-v4-migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val original = DailyGame.entries.map { game ->
            val difficulty = if (game == DailyGame.NONOGRAM) DailyDifficulty.EASY else DailyDifficulty.MEDIUM
            val current = DailyChallengeFactory.spec(date, game, difficulty)
            val legacy = when (game) {
                DailyGame.TILE_MERGE -> current.copy(target = 256, objectiveKind = DailyObjective.TILE_VALUE, version = 1)
                DailyGame.BLOCKS -> current.copy(target = 10, objectiveKind = DailyObjective.LINES, version = 1)
                else -> current.copy(version = 1)
            }
            val payload = DailyCodec.encode(DailyChallengeFactory.initial(legacy))
            DailyHavenEntry(date.toString(), game.key, legacy.seed, legacy.target, 1, payload, payload, 123,
                completedAt = if (game == DailyGame.SUDOKU) 456 else null,
                difficulty = difficulty.name, objectiveKind = legacy.objectiveKind.name)
        }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE puzzle_sessions (gameId TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE sudoku_completions (puzzleId TEXT NOT NULL PRIMARY KEY, difficulty TEXT NOT NULL, mode TEXT NOT NULL, completedAt INTEGER NOT NULL, hintsUsed INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE tile_merge_milestones (runId TEXT NOT NULL PRIMARY KEY, score INTEGER NOT NULL, achievedAt INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE play_records (id TEXT NOT NULL PRIMARY KEY, game TEXT NOT NULL, category TEXT NOT NULL, title TEXT NOT NULL, elapsedMillis INTEGER NOT NULL, timeKnown INTEGER NOT NULL, completedAt INTEGER, hintsUsed INTEGER NOT NULL, score INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE daily_haven_days (date TEXT NOT NULL PRIMARY KEY, bonusAwardedAt INTEGER)")
            old.execSQL("CREATE TABLE daily_haven_entries (date TEXT NOT NULL, gameId TEXT NOT NULL, seed INTEGER NOT NULL, target INTEGER NOT NULL, generatorVersion INTEGER NOT NULL, initialPayload TEXT NOT NULL, currentPayload TEXT NOT NULL, updatedAt INTEGER NOT NULL, completedAt INTEGER, PRIMARY KEY(date, gameId))")
            old.execSQL("INSERT INTO puzzle_sessions VALUES ('block_garden', 'existing-9140-run', 123)")
            old.execSQL("INSERT INTO sudoku_completions VALUES ('daily-2026-10-30', 'MEDIUM', 'DAILY', 123, 0)")
            old.execSQL("INSERT INTO play_records VALUES ('existing-record', 'Sudoku', 'Medium', 'Old solve', 60000, 1, 456, 0, 0)")
            old.execSQL("INSERT INTO daily_haven_days VALUES (?, NULL)", arrayOf(date.toString()))
            old.execSQL("INSERT INTO daily_haven_days VALUES ('2026-10-30', 999)")
            original.forEach { row ->
                old.execSQL("INSERT INTO daily_haven_entries VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>(
                    row.date, row.gameId, row.seed, row.target, row.generatorVersion,
                    row.initialPayload, row.currentPayload, row.updatedAt, row.completedAt))
            }
            old.version = 4
        }
        val db = Room.databaseBuilder(context, CluehavenDatabase::class.java, name)
            .addMigrations(CluehavenDatabase.migration4to5).build()
        try {
            val repo = DailyHavenRepository(db)
            assertEquals(original.toSet(), repo.open(date).toSet())
            assertEquals(5, db.dailyHaven().day(date.toString())!!.requiredGamesForStreak)
            assertEquals(999L, db.dailyHaven().day("2026-10-30")!!.bonusAwardedAt)
            assertEquals("existing-9140-run", db.puzzleSessions().get("block_garden")!!.payload)
            assertEquals(1, db.progress().observeSudokuCompletions().first().size)
            assertEquals(60000L, db.playRecords().get("existing-record")!!.elapsedMillis)
            original.forEach { row -> assertEquals(row, repo.start(date, row.spec.game, DailyDifficulty.HARD)) }
            val tile = original.first { it.gameId == "tile_merge" }
            val state = DailyCodec.decode(DailyGame.TILE_MERGE, tile.currentPayload) as DailyState.Tile
            assertFalse(repo.save(date, DailyState.Tile(state.value.copy(score = 2000))).newCompletion)
            assertTrue(repo.save(date, DailyState.Tile(state.value.copy(board = List(16) { 256 }))).newCompletion)
            val garden = original.first { it.gameId == "blocks" }
            val blocks = DailyCodec.decode(DailyGame.BLOCKS, garden.currentPayload) as DailyState.Blocks
            assertFalse(repo.save(date, DailyState.Blocks(blocks.value.copy(score = 5000))).newCompletion)
            assertTrue(repo.save(date, DailyState.Blocks(blocks.value.copy(lines = 10))).newCompletion)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
