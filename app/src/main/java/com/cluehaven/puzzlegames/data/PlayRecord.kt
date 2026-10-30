package com.cluehaven.puzzlegames.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Locale

@Entity(tableName = "play_records")
data class PlayRecord(
    @PrimaryKey val id: String,
    val game: String,
    val category: String,
    val title: String,
    val elapsedMillis: Long = 0,
    val timeKnown: Boolean = true,
    val completedAt: Long? = null,
    val hintsUsed: Int = 0,
    val score: Int = 0,
)

@Dao
interface PlayRecordDao {
    @Query("SELECT * FROM play_records WHERE id = :id")
    suspend fun get(id: String): PlayRecord?

    @Query("SELECT * FROM play_records WHERE completedAt IS NOT NULL ORDER BY completedAt DESC, id ASC")
    fun observeHistory(): Flow<List<PlayRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(record: PlayRecord)
}

fun formatPlayTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}

/** Only complete, fully timed and unassisted solves compete in the same category. */
fun personalBests(history: List<PlayRecord>): List<PlayRecord> = history
    .filter { it.completedAt != null && it.timeKnown && it.hintsUsed == 0 && it.game != "Tile Merge" }
    .groupBy { it.game to it.category }
    .values.map { records -> records.minBy { it.elapsedMillis } }
    .sortedWith(compareBy({ it.game }, { it.category }))

/** Monotonic time is independent of date, time zone and wall-clock changes. */
class ActivePlayClock(initialMillis: Long = 0, private val now: () -> Long) {
    private var total = initialMillis.coerceAtLeast(0)
    private var resumedAt: Long? = null
    fun resume() { if (resumedAt == null) resumedAt = now() }
    fun elapsed(): Long = total + (resumedAt?.let { (now() - it).coerceAtLeast(0) } ?: 0)
    fun pause() { total = elapsed(); resumedAt = null }
}
