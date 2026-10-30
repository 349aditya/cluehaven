package com.cluehaven.puzzlegames

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cluehaven.puzzlegames.data.CluehavenDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayRecordMigrationTest {
    @Test fun upgradePreservesBoardsAndImportsHistoricalResultsWithoutInventingTimes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "timing-migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE puzzle_sessions (gameId TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE sudoku_completions (puzzleId TEXT NOT NULL PRIMARY KEY, difficulty TEXT NOT NULL, mode TEXT NOT NULL, completedAt INTEGER NOT NULL, hintsUsed INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE tile_merge_milestones (runId TEXT NOT NULL PRIMARY KEY, score INTEGER NOT NULL, achievedAt INTEGER NOT NULL)")
            db.execSQL("INSERT INTO puzzle_sessions VALUES ('nonogram_pack-5-0', 'saved-board', 10)")
            db.execSQL("INSERT INTO sudoku_completions VALUES ('daily-2026-10-29', 'MEDIUM', 'DAILY', 1000, 1)")
            db.execSQL("INSERT INTO tile_merge_milestones VALUES ('run-1', 4200, 2000)")
            db.version = 2
        }
        val room = Room.databaseBuilder(context, CluehavenDatabase::class.java, name)
            .addMigrations(CluehavenDatabase.migration2to3).build()
        try {
            val db = room.openHelper.writableDatabase
            db.query("SELECT payload FROM puzzle_sessions").use { assertTrue(it.moveToFirst()); assertEquals("saved-board", it.getString(0)) }
            db.query("SELECT COUNT(*) FROM play_records").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
            db.query("SELECT elapsedMillis, timeKnown, completedAt, hintsUsed FROM play_records WHERE game = 'Sudoku'").use {
                assertTrue(it.moveToFirst()); assertEquals(0L, it.getLong(0)); assertEquals(0, it.getInt(1))
                assertEquals(1000L, it.getLong(2)); assertEquals(1, it.getInt(3))
            }
        } finally { room.close(); context.deleteDatabase(name) }
    }
}
