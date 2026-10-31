package com.cluehaven.puzzlegames.game.sudoku

import android.content.Context
import com.cluehaven.puzzlegames.data.CluehavenDatabase
import com.cluehaven.puzzlegames.data.PuzzleSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class SudokuStorage private constructor(context: Context) {
    private val sessions = CluehavenDatabase.get(context).puzzleSessions()
    private val latest = ConcurrentHashMap<String, SudokuState>()
    private val saves = Channel<Pair<String, SudokuState>>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            for ((key, state) in saves) {
                sessions.save(PuzzleSession(key, SudokuCodec.encode(state), System.currentTimeMillis()))
            }
        }
    }

    suspend fun load(key: String): SudokuState? {
        latest[key]?.let { return it }
        val state = sessions.get(key)?.payload?.let(SudokuCodec::decode) ?: return null
        latest[key] = state
        return state
    }

    suspend fun completedDailyPuzzles(): List<SudokuState> =
        sessions.dailySudokuSessions().mapNotNull { SudokuCodec.decode(it.payload) }
            .filter { it.isComplete }

    fun save(state: SudokuState) {
        val key = keyFor(state)
        latest[key] = state
        check(saves.trySend(key to state).isSuccess) { "Sudoku save queue is unavailable" }
    }

    private fun keyFor(state: SudokuState): String = when (state.mode) {
        SudokuMode.FREE -> FREE_KEY
        SudokuMode.DAILY -> "sudoku_${state.puzzle.id}"
    }

    companion object {
        const val FREE_KEY = "sudoku_free"
        @Volatile private var instance: SudokuStorage? = null
        fun get(context: Context): SudokuStorage = instance ?: synchronized(this) {
            instance ?: SudokuStorage(context.applicationContext).also { instance = it }
        }
    }
}
