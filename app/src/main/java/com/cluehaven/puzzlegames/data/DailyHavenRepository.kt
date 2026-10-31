package com.cluehaven.puzzlegames.data

import android.content.Context
import androidx.room.*
import com.cluehaven.puzzlegames.game.daily.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate

@Entity(tableName = "daily_haven_days")
data class DailyHavenDay(
    @PrimaryKey val date: String,
    val bonusAwardedAt: Long? = null,
    @ColumnInfo(defaultValue = "5") val requiredGamesForStreak: Int = DailyFiveConfig.requiredGamesForStreak,
)

@Entity(tableName = "daily_haven_entries", primaryKeys = ["date", "gameId"])
data class DailyHavenEntry(
    val date: String, val gameId: String, val seed: Long, val target: Int, val generatorVersion: Int,
    val initialPayload: String, val currentPayload: String, val updatedAt: Long, val completedAt: Long? = null,
    @ColumnInfo(defaultValue = "'MEDIUM'") val difficulty: String = DailyDifficulty.MEDIUM.name,
    @ColumnInfo(defaultValue = "'SOLVE'") val objectiveKind: String = DailyObjective.SOLVE.name,
) {
    val spec: DailySpec get() = DailySpec(LocalDate.parse(date), DailyGame.fromKey(gameId), seed, target,
        generatorVersion, DailyDifficulty.valueOf(difficulty), DailyObjective.valueOf(objectiveKind))
}

data class DailySaveResult(val entry: DailyHavenEntry, val newCompletion: Boolean, val newBonus: Boolean)

data class HavenDailyResultRow(val date: String, val completedGames: Int, val requiredGamesForStreak: Int)
data class SavedHavenResults(val entries: List<DailyHavenEntry>, val dailyResults: List<HavenDailyResult>)

@Dao
interface DailyHavenDao {
    @Query("""SELECT d.date, d.requiredGamesForStreak, COUNT(e.gameId) AS completedGames
        FROM daily_haven_days d LEFT JOIN daily_haven_entries e
        ON e.date = d.date AND e.completedAt IS NOT NULL AND e.gameId IN (:knownGames)
        GROUP BY d.date, d.requiredGamesForStreak ORDER BY d.date""")
    suspend fun streakResults(knownGames: List<String>): List<HavenDailyResultRow>
    @Query("SELECT * FROM daily_haven_entries WHERE date = :date")
    fun observeEntries(date: String): Flow<List<DailyHavenEntry>>
    @Query("SELECT * FROM daily_haven_days WHERE date = :date")
    fun observeDay(date: String): Flow<DailyHavenDay?>
    @Query("SELECT * FROM daily_haven_entries WHERE date = :date AND gameId = :gameId")
    suspend fun entry(date: String, gameId: String): DailyHavenEntry?
    @Query("SELECT * FROM daily_haven_entries WHERE date = :date")
    suspend fun entries(date: String): List<DailyHavenEntry>
    @Query("SELECT * FROM daily_haven_days WHERE date = :date")
    suspend fun day(date: String): DailyHavenDay?
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDay(day: DailyHavenDay)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(entry: DailyHavenEntry)
    @Query("UPDATE daily_haven_entries SET currentPayload = :payload, updatedAt = :now, completedAt = :completedAt WHERE date = :date AND gameId = :gameId AND completedAt IS NULL")
    suspend fun updateUnfinished(date: String, gameId: String, payload: String, now: Long, completedAt: Long?): Int
    @Query("SELECT COUNT(*) FROM daily_haven_entries WHERE date = :date AND completedAt IS NOT NULL")
    suspend fun completedCount(date: String): Int
    @Query("UPDATE daily_haven_days SET bonusAwardedAt = :now WHERE date = :date AND bonusAwardedAt IS NULL")
    suspend fun awardBonus(date: String, now: Long): Int
}

/** Daily progress has its own table and write queue; regular game saves never award it. */
class DailyHavenRepository internal constructor(private val database: CluehavenDatabase) {
    private val dao = database.dailyHaven()
    fun observeEntries(date: LocalDate) = dao.observeEntries(date.toString())
    fun observeDay(date: LocalDate) = dao.observeDay(date.toString())
    /** One read transaction keeps the garden and streak consistent with the same completion. */
    fun observeHaven(date: LocalDate): Flow<SavedHavenResults> = database.invalidationTracker
        .createFlow("daily_haven_days", "daily_haven_entries")
        .map {
            database.withTransaction {
                SavedHavenResults(dao.entries(date.toString()), dao.streakResults(DailyGame.entries.map { it.key })
                    .map { row -> HavenDailyResult(LocalDate.parse(row.date), row.completedGames, row.requiredGamesForStreak) })
            }
        }.distinctUntilChanged()
    private data class Write(val date: String, val game: DailyGame, val state: DailyState?, val result: CompletableDeferred<DailySaveResult>)
    private val writes = Channel<Write>(Channel.UNLIMITED)
    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (write in writes) {
                runCatching { saveInternal(write.date, write.game, write.state) }
                    .onSuccess { write.result.complete(it) }.onFailure { write.result.completeExceptionally(it) }
            }
        }
    }

    /** Opening the list never chooses a difficulty or generates a board. */
    suspend fun open(date: LocalDate): List<DailyHavenEntry> = database.withTransaction {
        val key = date.toString()
        require(DailyFiveConfig.requiredGamesForStreak in 1..DailyGame.entries.size)
        dao.insertDay(DailyHavenDay(key))
        dao.entries(key)
    }

    /** The first Start wins, even when two difficulty requests arrive concurrently. */
    suspend fun start(date: LocalDate, game: DailyGame, difficulty: DailyDifficulty): DailyHavenEntry = database.withTransaction {
        val key = date.toString()
        requireNotNull(dao.day(key)) { "Open the Daily Five flow before starting a challenge" }
        dao.entry(key, game.key)?.let { return@withTransaction it }
        val spec = DailyChallengeFactory.spec(date, game, difficulty)
        val payload = DailyCodec.encode(DailyChallengeFactory.initial(spec))
        dao.insertEntry(DailyHavenEntry(key, game.key, spec.seed, spec.target, spec.version,
            payload, payload, System.currentTimeMillis(), difficulty = difficulty.name, objectiveKind = spec.objectiveKind.name))
        requireNotNull(dao.entry(key, game.key))
    }

    internal fun enqueue(date: LocalDate, game: DailyGame, state: DailyState?): Deferred<DailySaveResult> {
        val result = CompletableDeferred<DailySaveResult>()
        check(writes.trySend(Write(date.toString(), game, state, result)).isSuccess)
        return result
    }
    internal suspend fun save(date: LocalDate, state: DailyState): DailySaveResult = enqueue(date, state.game, state).await()

    private suspend fun saveInternal(date: String, game: DailyGame, state: DailyState?): DailySaveResult = database.withTransaction {
        val old = requireNotNull(dao.entry(date, game.key)) { "Open the Daily Five flow before saving a challenge" }
        // Earned growth is permanent, including after undo, retry, reload, and duplicate callbacks.
        if (old.completedAt != null) return@withTransaction DailySaveResult(old, false, false)
        val initial = DailyCodec.decode(game, old.initialPayload)
        val next = state ?: initial // A retry restores the saved starting board, never a new seed.
        require(DailyRules.sameChallenge(initial, next)) { "This state belongs to a different challenge" }
        val won = state != null && DailyRules.complete(old.spec, next)
        val now = System.currentTimeMillis()
        val completedAt = if (won) now else null
        val changed = dao.updateUnfinished(date, game.key, DailyCodec.encode(next), now, completedAt) != 0
        val newCompletion = won && changed
        val newBonus = newCompletion && dao.completedCount(date) == DailyGame.entries.size && dao.awardBonus(date, now) != 0
        DailySaveResult(requireNotNull(dao.entry(date, game.key)), newCompletion, newBonus)
    }

    companion object {
        @Volatile private var instance: DailyHavenRepository? = null
        fun get(context: Context): DailyHavenRepository = instance ?: synchronized(this) {
            instance ?: DailyHavenRepository(CluehavenDatabase.get(context)).also { instance = it }
        }
    }
}
