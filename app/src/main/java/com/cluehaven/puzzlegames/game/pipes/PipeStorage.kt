package com.cluehaven.puzzlegames.game.pipes

import android.content.Context
import com.cluehaven.puzzlegames.data.CluehavenDatabase
import com.cluehaven.puzzlegames.data.PuzzleSession
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class PipeStorage private constructor(context: Context) {
    private val dao = CluehavenDatabase.get(context).puzzleSessions()
    private val latest = ConcurrentHashMap<Int, PipeState>()
    private val writes = Channel<PipeState>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init {
        scope.launch {
            for (state in writes) dao.save(PuzzleSession("pipes_${state.size}",
                JSONObject().put("seed", state.seed).put("tiles", JSONArray(state.tiles))
                    .put("moves", state.moves).put("hints", state.hintsUsed)
                    .put("history", JSONArray().apply { state.history.forEach { put(JSONArray(it)) } })
                    .toString(), System.currentTimeMillis()))
        }
    }
    suspend fun load(size: Int): PipeState {
        latest[size]?.let { return it }
        val loaded = dao.get("pipes_$size")?.payload?.let { payload ->
            runCatching {
                val json = JSONObject(payload)
                val base = PipeEngine.newGame(size, json.getLong("seed"))
                fun read(array: JSONArray): List<Int> = (0 until array.length()).map { array.getInt(it) }
                    .also { values ->
                        require(values.size == size * size)
                        require(values.indices.all { index ->
                            var mask = base.solution[index]
                            val rotations = mutableSetOf<Int>()
                            repeat(4) { rotations.add(mask); mask = PipeEngine.rotate(mask) }
                            values[index] in rotations
                        })
                    }
                val history = json.getJSONArray("history")
                require(history.length() <= 100)
                val moves = json.getInt("moves")
                val hints = json.getInt("hints")
                require(moves >= 0 && hints in 0..1)
                base.copy(tiles = read(json.getJSONArray("tiles")), moves = moves, hintsUsed = hints,
                    history = (0 until history.length()).map { read(history.getJSONArray(it)) })
            }.getOrNull()
        } ?: PipeEngine.newGame(size)
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
