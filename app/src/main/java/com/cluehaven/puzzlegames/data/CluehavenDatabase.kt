package com.cluehaven.puzzlegames.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "puzzle_sessions")
data class PuzzleSession(
    @PrimaryKey val gameId: String,
    val payload: String,
    val updatedAt: Long,
)

@Dao
interface PuzzleSessionDao {
    @Query("SELECT * FROM puzzle_sessions")
    suspend fun all(): List<PuzzleSession>

    @Query("SELECT * FROM puzzle_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<PuzzleSession>>
    @Query("SELECT * FROM puzzle_sessions WHERE gameId = :gameId LIMIT 1")
    suspend fun get(gameId: String): PuzzleSession?

    @Query("SELECT * FROM puzzle_sessions WHERE gameId GLOB 'sudoku_daily-*'")
    suspend fun dailySudokuSessions(): List<PuzzleSession>

    @Query("SELECT * FROM puzzle_sessions WHERE gameId GLOB 'nonogram_*'")
    fun observeNonogramSessions(): Flow<List<PuzzleSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(session: PuzzleSession)
}

@Entity(tableName = "sudoku_completions")
data class SudokuCompletion(
    @PrimaryKey val puzzleId: String,
    val difficulty: String,
    val mode: String,
    val completedAt: Long,
    val hintsUsed: Int,
)

@Entity(tableName = "tile_merge_milestones")
data class TileMergeMilestone(
    @PrimaryKey val runId: String,
    val score: Int,
    val achievedAt: Long,
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM sudoku_completions ORDER BY completedAt DESC")
    fun observeSudokuCompletions(): Flow<List<SudokuCompletion>>

    @Query("SELECT * FROM tile_merge_milestones ORDER BY achievedAt DESC")
    fun observeTileMilestones(): Flow<List<TileMergeMilestone>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSudokuCompletion(completion: SudokuCompletion)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTileMilestone(milestone: TileMergeMilestone)
}

@Database(
    entities = [PuzzleSession::class, SudokuCompletion::class, TileMergeMilestone::class, PlayRecord::class],
    version = 3,
    exportSchema = false,
)
abstract class CluehavenDatabase : RoomDatabase() {
    abstract fun puzzleSessions(): PuzzleSessionDao
    abstract fun progress(): ProgressDao
    abstract fun playRecords(): PlayRecordDao

    companion object {
        @Volatile private var instance: CluehavenDatabase? = null

        internal val migration2to3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `play_records` (
                    `id` TEXT NOT NULL, `game` TEXT NOT NULL, `category` TEXT NOT NULL,
                    `title` TEXT NOT NULL, `elapsedMillis` INTEGER NOT NULL,
                    `timeKnown` INTEGER NOT NULL, `completedAt` INTEGER,
                    `hintsUsed` INTEGER NOT NULL, `score` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                db.execSQL("""INSERT OR IGNORE INTO play_records
                    SELECT 'sudoku_' || puzzleId, 'Sudoku',
                    CASE difficulty WHEN 'EASY' THEN 'Easy · 6×6' WHEN 'HARD' THEN 'Hard · 9×9' ELSE 'Medium · 9×9' END,
                    CASE mode WHEN 'DAILY' THEN 'Daily · ' || SUBSTR(puzzleId, 7) ELSE 'Free play' END,
                    0, 0, completedAt, hintsUsed, 0 FROM sudoku_completions""")
                db.execSQL("""INSERT OR IGNORE INTO play_records
                    SELECT 'tile_milestone_' || runId, 'Tile Merge', '2048 milestone', 'Reached 2048',
                    0, 0, achievedAt, 0, score FROM tile_merge_milestones""")
            }
        }

        private val migration1to2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sudoku_completions` (
                        `puzzleId` TEXT NOT NULL,
                        `difficulty` TEXT NOT NULL,
                        `mode` TEXT NOT NULL,
                        `completedAt` INTEGER NOT NULL,
                        `hintsUsed` INTEGER NOT NULL,
                        PRIMARY KEY(`puzzleId`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tile_merge_milestones` (
                        `runId` TEXT NOT NULL,
                        `score` INTEGER NOT NULL,
                        `achievedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`runId`)
                    )
                """.trimIndent())
            }
        }

        fun get(context: Context): CluehavenDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CluehavenDatabase::class.java,
                "cluehaven.db",
            ).addMigrations(migration1to2, migration2to3).build().also { instance = it }
        }
    }
}
