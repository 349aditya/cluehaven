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

@Entity(tableName = "puzzle_sessions")
data class PuzzleSession(
    @PrimaryKey val gameId: String,
    val payload: String,
    val updatedAt: Long,
)

@Dao
interface PuzzleSessionDao {
    @Query("SELECT * FROM puzzle_sessions WHERE gameId = :gameId LIMIT 1")
    suspend fun get(gameId: String): PuzzleSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(session: PuzzleSession)
}

@Database(entities = [PuzzleSession::class], version = 1, exportSchema = false)
abstract class CluehavenDatabase : RoomDatabase() {
    abstract fun puzzleSessions(): PuzzleSessionDao

    companion object {
        @Volatile private var instance: CluehavenDatabase? = null

        fun get(context: Context): CluehavenDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CluehavenDatabase::class.java,
                "cluehaven.db",
            ).build().also { instance = it }
        }
    }
}
