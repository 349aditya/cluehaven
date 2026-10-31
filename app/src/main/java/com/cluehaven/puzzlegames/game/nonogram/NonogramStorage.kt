package com.cluehaven.puzzlegames.game.nonogram

import android.content.Context
import com.cluehaven.puzzlegames.data.CluehavenDatabase
import com.cluehaven.puzzlegames.data.PuzzleSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

class NonogramStorage private constructor(context: Context) {
    private val sessions = CluehavenDatabase.get(context).puzzleSessions()
    private val latest = ConcurrentHashMap<String, NonogramState>()
    private val saves = Channel<NonogramState>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            for (state in saves) {
                sessions.save(PuzzleSession("nonogram_${state.puzzle.id}", NonogramCodec.encode(state),
                    System.currentTimeMillis()))
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
        latest[state.puzzle.id] = state
        check(saves.trySend(state).isSuccess)
    }

    companion object {
        @Volatile private var instance: NonogramStorage? = null
        fun get(context: Context): NonogramStorage = instance ?: synchronized(this) {
            instance ?: NonogramStorage(context.applicationContext).also { instance = it }
        }
    }
}

data class NonogramProgress(val starterSolved: Int = 0, val dailySolved: Int = 0)
