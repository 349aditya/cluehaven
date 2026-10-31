package com.cluehaven.puzzlegames

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cluehaven.puzzlegames.data.*
import com.cluehaven.puzzlegames.game.nonogram.*
import com.cluehaven.puzzlegames.ui.LocalPlayRecords
import com.cluehaven.puzzlegames.ui.LocalReducedMotion
import com.cluehaven.puzzlegames.ui.home.resumeEntry
import com.cluehaven.puzzlegames.ui.theme.CluehavenTheme
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EndlessNonogramDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: CluehavenDatabase
    private lateinit var worker: CoroutineScope
    private lateinit var storage: NonogramStorage
    private lateinit var records: FixtureRecords
    private var dark by mutableStateOf(false)
    private var reduced by mutableStateOf(false)

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, CluehavenDatabase::class.java).build()
        worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        storage = NonogramStorage(database.puzzleSessions(), worker)
        records = FixtureRecords(database.playRecords(), worker)
    }
    @After fun close() {
        runBlocking { worker.coroutineContext[Job]?.cancelAndJoin() }
        database.close()
    }
    private fun generated(seed: Long, size: Int = 5, difficulty: NonogramDifficulty = NonogramDifficulty.EASY) =
        (EndlessNonogramGenerator.generate(seed, size, difficulty) as EndlessGenerationResult.Ready).puzzle
    private fun show(slot: String? = null,
        prepare: suspend (Long, Int, NonogramDifficulty) -> EndlessGenerationResult = ::prepareEndless) {
        compose.setContent {
            CluehavenTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalPlayRecords provides records, LocalReducedMotion provides reduced) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        EndlessNonogramScreen(PaddingValues(), slot, storage, prepare) {}
                    }
                }
            }
        }
    }
    private fun awaitBoard(size: Int, difficulty: NonogramDifficulty) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("$size×$size · Endless · ${difficulty.label}").fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun saved(size: Int = 5, difficulty: NonogramDifficulty = NonogramDifficulty.EASY): NonogramState = runBlocking {
        NonogramCodec.restoreEndless(database.puzzleSessions().get("nonogram_${NonogramStorage.endlessSlot(size, difficulty)}")!!.payload)!!
    }
    private fun capture(name: String) {
        val folder = File(context.getExternalFilesDir(null), "endless-review").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun fullSnapshotAndReportSurviveReopenEvenForAnUnavailableGeneratorVersion() = runBlocking {
        val name = "endless-frozen-review.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, CluehavenDatabase::class.java, name).build()
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val original = generated(0)
            val info = original.endless!!
            val futureId = original.id.replace("endless-v1-", "endless-v99-")
            val frozen = original.copy(id = futureId, sourceId = futureId, endless = info.copy(generatorVersion = 99))
            val store = NonogramStorage(db.puzzleSessions(), scope)
            val initial = store.beginEndless(frozen)
            val partial = NonogramEngine.hint(NonogramEngine.mark(initial, 0, true))
            store.save(partial)
            withTimeout(5_000) { db.puzzleSessions().observeNonogramSessions().first { rows ->
                rows.any { NonogramCodec.restoreEndless(it.payload) == partial }
            } }
            scope.coroutineContext[Job]!!.cancelAndJoin()
            db.close()
            db = Room.databaseBuilder(context, CluehavenDatabase::class.java, name).build()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val reopened = NonogramStorage(db.puzzleSessions(), scope).loadEndless(5, NonogramDifficulty.EASY)
            assertEquals(partial, reopened)
            assertEquals(99, reopened!!.puzzle.endless!!.generatorVersion)
            assertEquals(original.solution, reopened.puzzle.solution)
            assertEquals(original.endless.report, reopened.puzzle.endless!!.report)
            val row = db.puzzleSessions().get("nonogram_endless-5-EASY")!!
            assertEquals("nonogram|endless-5-EASY", resumeEntry(row)!!.route)
            assertTrue(resumeEntry(row)!!.title.contains("Endless"))
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); db.close(); context.deleteDatabase(name) }
    }

    @Test fun malformedSnapshotsAreRejectedAndAuthoredAndDailySavesStayCompatible() {
        val snapshot = NonogramCodec.encode(NonogramState(generated(0)))
        assertEquals(1, JSONObject(snapshot).getInt("generatorVersion"))
        assertNotNull(NonogramCodec.restoreEndless(snapshot))
        assertNull(NonogramCodec.restoreEndless(JSONObject(snapshot).apply { remove("generatedPuzzle") }.toString()))
        assertNull(NonogramCodec.restoreEndless(JSONObject(snapshot).put("generatorVersion", 2).toString()))
        assertNull(NonogramCodec.restoreEndless(JSONObject(snapshot).apply {
            getJSONObject("generatedPuzzle").remove("seed")
        }.toString()))
        assertNull(NonogramCodec.restoreEndless(JSONObject(snapshot).apply {
            getJSONObject("generatedPuzzle").put("completionCells", -1)
        }.toString()))
        val authored = NonogramState(NonogramEngine.puzzles.first())
        assertEquals(authored, NonogramCodec.decode(authored.puzzle, NonogramCodec.encode(authored)))
        assertNull(NonogramCodec.restoreEndless(NonogramCodec.encode(authored)))
        val day = NonogramState(NonogramEngine.daily(java.time.LocalDate.of(2026, 10, 31)))
        assertEquals(day, NonogramCodec.decode(day.puzzle, NonogramCodec.encode(day)))
    }

    @Test fun savesAreBoundedBySizeAndDifficultyAndNeverReplaceThePicturePack() = runBlocking {
        val pack = NonogramState(NonogramEngine.puzzles.first())
        val packRow = PuzzleSession("nonogram_${pack.puzzle.id}", NonogramCodec.encode(pack), 1)
        database.puzzleSessions().save(packRow)
        for (size in listOf(5, 10)) for (difficulty in NonogramDifficulty.entries) {
            storage.beginEndless(generated(0, size, difficulty))
            storage.beginEndless(generated(1, size, difficulty))
        }
        assertEquals(7, database.puzzleSessions().all().size)
        assertEquals(packRow, database.puzzleSessions().get(packRow.gameId))
        assertEquals(NonogramProgress(), storage.progress.first())
    }

    @Test fun flowCompletesRecordsHistoryAndNextKeepsTheFinishedResult() {
        show()
        compose.onNodeWithText("Play").performScrollTo().performClick()
        awaitBoard(5, NonogramDifficulty.EASY)
        val initial = saved()
        capture("endless-play-light")
        val first = initial.puzzle.solution.indexOf(1)
        compose.onNodeWithContentDescription("Row ${first / 5 + 1}, column ${first % 5 + 1}, unknown").performClick()
        compose.onNodeWithText("Undo").performClick()
        initial.puzzle.solution.indices.filter { initial.puzzle.solution[it] == 1 }.forEach { index ->
            compose.onNodeWithContentDescription("Row ${index / 5 + 1}, column ${index % 5 + 1}, unknown").performClick()
        }
        compose.onNodeWithText("Next puzzle").performScrollTo().assertIsDisplayed()
        compose.waitUntil(5_000) { records.latest[initial.playRecord().id]?.completedAt != null }
        capture("endless-complete")
        compose.onNodeWithText("Next puzzle").performClick()
        compose.waitUntil(10_000) { saved().puzzle.id != initial.puzzle.id }
        compose.waitForIdle()
        val next = saved()
        assertNotEquals(initial.puzzle.solution, next.puzzle.solution)
        assertTrue(next.cells.all { it == 0 })
        assertNotNull(records.latest[initial.playRecord().id]!!.completedAt)
        assertEquals(EndlessNonogramStats(1, 1, 0), runBlocking { endlessNonogramStats(records.history.first()) })
        assertEquals(1, runBlocking { database.puzzleSessions().all().size })
    }

    @Test fun resumeUsesTheSavedPictureAndThemesAndHintRemainAvailable() = runBlocking {
        val initial = storage.beginEndless(generated(0, 10, NonogramDifficulty.HARD))
        val partial = NonogramEngine.mark(initial, 0, true)
        storage.save(partial)
        withTimeout(5_000) { database.puzzleSessions().observeNonogramSessions().first { rows ->
            rows.any { NonogramCodec.restoreEndless(it.payload) == partial }
        } }
        show(NonogramStorage.endlessSlot(10, NonogramDifficulty.HARD))
        awaitBoard(10, NonogramDifficulty.HARD)
        compose.onNodeWithContentDescription("Row 1, column 1, crossed out").assertIsDisplayed()
        compose.runOnIdle { dark = true; reduced = true }
        capture("endless-resume-dark-reduced")
        compose.onNodeWithText("Hint").performClick()
        compose.waitUntil(5_000) { saved(10, NonogramDifficulty.HARD).hintsUsed == 1 }
        assertEquals(initial.puzzle, saved(10, NonogramDifficulty.HARD).puzzle)
        compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        compose.onNodeWithText("Resume").performScrollTo().performClick()
        awaitBoard(10, NonogramDifficulty.HARD)
        assertEquals(initial.puzzle, saved(10, NonogramDifficulty.HARD).puzzle)
    }

    @Test fun timeoutReturnsToAUsableChooserWithoutSavingAnUnverifiedPuzzle() {
        show(prepare = { _, _, _ -> delay(20); EndlessGenerationResult.Failed(EndlessGenerationFailure.TIMEOUT, 0) })
        compose.onNodeWithText("Play").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Couldn't prepare that picture in time. Try another.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Play").assertIsEnabled()
        assertTrue(runBlocking { database.puzzleSessions().all().isEmpty() })
    }

    @Test fun backCancelsGenerationWithoutCreatingASave() {
        val started = CompletableDeferred<Unit>()
        show(prepare = { _, _, _ -> started.complete(Unit); awaitCancellation() })
        compose.onNodeWithText("Play").performScrollTo().performClick()
        compose.waitUntil(5_000) { started.isCompleted }
        compose.onNodeWithContentDescription("Back").performScrollTo().performClick()
        compose.onNodeWithText("Play").assertIsEnabled()
        assertTrue(runBlocking { database.puzzleSessions().all().isEmpty() })
    }

    /** Real Room history/timers, isolated from the installed app's repository. */
    private class FixtureRecords(private val dao: PlayRecordDao, scope: CoroutineScope) : PlayRecords {
        val latest = ConcurrentHashMap<String, PlayRecord>()
        private val writes = Channel<PlayRecord>(Channel.UNLIMITED)
        override val history = dao.observeHistory()
        init { scope.launch { for (record in writes) dao.save(record) } }
        override suspend fun load(initial: PlayRecord) = latest[initial.id] ?: dao.get(initial.id) ?: initial
        override fun save(record: PlayRecord) { latest[record.id] = record; check(writes.trySend(record).isSuccess) }
    }
}
