package com.cluehaven.puzzlegames.game.pipes

import android.content.Context
import com.cluehaven.puzzlegames.data.CluehavenDatabase
import com.cluehaven.puzzlegames.data.PuzzleSession
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap

class PipeStorage private constructor(context: Context) {
    private val dao = CluehavenDatabase.get(context).puzzleSessions()
    private val latest = ConcurrentHashMap<Int, PipeState>()
    private val writes = Channel<PipeState>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init {
        scope.launch {
            for (state in writes) dao.save(PuzzleSession("pipes_${state.size}",
                PipeCodec.encode(state), System.currentTimeMillis()))
        }
    }
    suspend fun load(size: Int): PipeState {
        latest[size]?.let { return it }
        val loaded = dao.get("pipes_$size")?.payload?.let { PipeCodec.decode(size, it) }
            ?: PipeEngine.newGame(size)
        return latest.putIfAbsent(size, loaded) ?: loaded
    }
    fun save(state: PipeState) {
        latest[state.size] = state
        check(writes.trySend(state).isSuccess)
    }
    companion object {
        @Volatile private var instance: PipeStorage? = null
        fun get(context: Context): PipeStorage = instance ?: synchronized(this) {
            instance ?: PipeStorage(context.applicationContext).also { instance = it }
        }
    }
}
