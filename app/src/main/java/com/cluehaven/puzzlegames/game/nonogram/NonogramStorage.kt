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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

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
                sessions.save(PuzzleSession("nonogram_${state.puzzle.id}", encode(state),
                    System.currentTimeMillis()))
            }
        }
    }

    suspend fun load(puzzle: NonogramPuzzle): NonogramState {
        latest[puzzle.id]?.let { return it }
        val loaded = sessions.get("nonogram_${puzzle.id}")?.payload?.let { decode(puzzle, it) }
            ?: NonogramState(puzzle)
        return latest.putIfAbsent(puzzle.id, loaded) ?: loaded
    }

    fun save(state: NonogramState) {
        latest[state.puzzle.id] = state
        check(saves.trySend(state).isSuccess)
    }

    private fun encode(state: NonogramState): String = JSONObject()
        .put("complete", state.isComplete)
        .put("cells", JSONArray(state.cells))
        .put("hintsUsed", state.hintsUsed)
        .put("history", JSONArray().apply { state.history.forEach { put(JSONArray(it)) } })
        .toString()

    private fun decode(puzzle: NonogramPuzzle, payload: String): NonogramState? = try {
        val json = JSONObject(payload)
        fun cells(array: JSONArray): List<Int> = (0 until array.length()).map { array.getInt(it) }
            .also { values -> require(values.size == puzzle.size * puzzle.size && values.all { it in 0..2 }) }
        val history = json.getJSONArray("history")
        require(history.length() <= 50)
        val hints = json.getInt("hintsUsed")
        require(hints in 0..1)
        NonogramState(puzzle, cells(json.getJSONArray("cells")),
            (0 until history.length()).map { cells(history.getJSONArray(it)) }, hints)
    } catch (_: Exception) { null }

    companion object {
        @Volatile private var instance: NonogramStorage? = null
        fun get(context: Context): NonogramStorage = instance ?: synchronized(this) {
            instance ?: NonogramStorage(context.applicationContext).also { instance = it }
        }
    }
}

data class NonogramProgress(val starterSolved: Int = 0, val dailySolved: Int = 0)
