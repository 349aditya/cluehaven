package com.cluehaven.puzzlegames.game.nonogram

import android.content.Context
import com.cluehaven.puzzlegames.data.CluehavenDatabase
import com.cluehaven.puzzlegames.data.PuzzleSession
import com.cluehaven.puzzlegames.data.PuzzleSessionDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

class NonogramStorage internal constructor(private val sessions: PuzzleSessionDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
    private val latest = ConcurrentHashMap<String, NonogramState>()
    private data class Write(val state: NonogramState, val ack: CompletableDeferred<Unit>? = null)
    private val saves = Channel<Write>(Channel.UNLIMITED)

    val progress = sessions.observeNonogramSessions().map { records ->
        val solved = records.filter { record ->
            runCatching { JSONObject(record.payload).getBoolean("complete") }.getOrDefault(false)
        }
        NonogramProgress(
            starterSolved = solved.count { it.gameId.startsWith("nonogram_pack-") },
            dailySolved = solved.count { it.gameId.startsWith("nonogram_daily-") },
        )
    }

    init {
        scope.launch {
            for (write in saves) {
                try {
                    sessions.save(PuzzleSession(sessionKey(write.state.puzzle), NonogramCodec.encode(write.state), System.currentTimeMillis()))
                    write.ack?.complete(Unit)
                } catch (error: Exception) {
                    write.ack?.completeExceptionally(error)
                    android.util.Log.e("NonogramStorage", "Could not save Nonogram progress", error)
                }
            }
        }
    }

    suspend fun load(puzzle: NonogramPuzzle): NonogramState {
        latest[puzzle.id]?.let { return it }
        val loaded = sessions.get("nonogram_${puzzle.id}")?.payload?.let { NonogramCodec.decode(puzzle, it) }
            ?: NonogramState(puzzle)
        return latest.putIfAbsent(puzzle.id, loaded) ?: loaded
    }

    fun save(state: NonogramState) {
        latest[cacheKey(state.puzzle)] = state
        check(saves.trySend(Write(state)).isSuccess)
    }

    suspend fun loadEndless(size: Int, difficulty: NonogramDifficulty): NonogramState? = withContext(Dispatchers.IO) {
        val key = endlessSlot(size, difficulty)
        latest[key]?.let { return@withContext it }
        sessions.get("nonogram_$key")?.payload?.let(NonogramCodec::restoreEndless)
            ?.takeIf { it.puzzle.size == size && it.puzzle.difficulty == difficulty }
            ?.also { latest.putIfAbsent(key, it) }
    }

    /** Await the queued initial snapshot before showing a generated board. */
    suspend fun beginEndless(puzzle: NonogramPuzzle): NonogramState {
        require(puzzle.endless != null)
        val state = NonogramState(puzzle)
        val ack = CompletableDeferred<Unit>()
        saves.send(Write(state, ack))
        ack.await()
        latest[cacheKey(puzzle)] = state
        return state
    }

    private fun cacheKey(puzzle: NonogramPuzzle) = if (puzzle.endless == null) puzzle.id else endlessSlot(puzzle.size, puzzle.difficulty)

    companion object {
        fun endlessSlot(size: Int, difficulty: NonogramDifficulty): String {
            require(size in setOf(5, 10))
            return "endless-$size-${difficulty.name}"
        }
        fun sessionKey(puzzle: NonogramPuzzle) = "nonogram_" + if (puzzle.endless == null) puzzle.id else endlessSlot(puzzle.size, puzzle.difficulty)
        @Volatile private var instance: NonogramStorage? = null
        fun get(context: Context): NonogramStorage = instance ?: synchronized(this) {
            instance ?: NonogramStorage(CluehavenDatabase.get(context.applicationContext).puzzleSessions()).also { instance = it }
        }
    }
}

data class NonogramProgress(val starterSolved: Int = 0, val dailySolved: Int = 0)
