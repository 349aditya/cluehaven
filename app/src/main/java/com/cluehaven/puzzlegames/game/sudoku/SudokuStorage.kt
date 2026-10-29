package com.cluehaven.puzzlegames.game.sudoku

import android.content.Context
import com.cluehaven.puzzlegames.data.CluehavenDatabase
import com.cluehaven.puzzlegames.data.PuzzleSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class SudokuStorage private constructor(context: Context) {
    private val sessions = CluehavenDatabase.get(context).puzzleSessions()
    private val latest = ConcurrentHashMap<String, SudokuState>()
    private val saves = Channel<Pair<String, SudokuState>>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            for ((key, state) in saves) {
                sessions.save(PuzzleSession(key, encode(state), System.currentTimeMillis()))
            }
        }
    }

    suspend fun load(key: String): SudokuState? {
        latest[key]?.let { return it }
        val state = sessions.get(key)?.payload?.let(::decode) ?: return null
        latest[key] = state
        return state
    }

    fun save(state: SudokuState) {
        val key = keyFor(state)
        latest[key] = state
        check(saves.trySend(key to state).isSuccess) { "Sudoku save queue is unavailable" }
    }

    private fun keyFor(state: SudokuState): String = when (state.mode) {
        SudokuMode.FREE -> FREE_KEY
        SudokuMode.DAILY -> "sudoku_${state.puzzle.id}"
    }

    private fun encode(state: SudokuState): String = JSONObject()
        .put("id", state.puzzle.id)
        .put("difficulty", state.puzzle.difficulty.name)
        .put("mode", state.mode.name)
        .put("givens", JSONArray(state.puzzle.givens))
        .put("solution", JSONArray(state.puzzle.solution))
        .put("values", JSONArray(state.values))
        .put("notes", JSONArray(state.notes))
        .put("selected", state.selected)
        .put("noteMode", state.noteMode)
        .put("hintsUsed", state.hintsUsed)
        .put("history", JSONArray().apply {
            state.history.forEach { snapshot ->
                put(JSONObject().put("values", JSONArray(snapshot.values))
                    .put("notes", JSONArray(snapshot.notes)))
            }
        })
        .toString()

    private fun decode(payload: String): SudokuState? = try {
        val json = JSONObject(payload)
        val puzzle = SudokuPuzzle(
            id = json.getString("id"),
            difficulty = SudokuDifficulty.valueOf(json.getString("difficulty")),
            givens = json.getJSONArray("givens").toIntList(),
            solution = json.getJSONArray("solution").toIntList(),
        )
        val history = json.getJSONArray("history").let { array ->
            (0 until array.length()).map { index ->
                array.getJSONObject(index).let { item ->
                    SudokuSnapshot(item.getJSONArray("values").toIntList(),
                        item.getJSONArray("notes").toIntList())
                }
            }
        }
        val state = SudokuState(
            puzzle = puzzle,
            mode = SudokuMode.valueOf(json.getString("mode")),
            values = json.getJSONArray("values").toIntList(),
            notes = json.getJSONArray("notes").toIntList(),
            selected = json.getInt("selected"),
            noteMode = json.getBoolean("noteMode"),
            hintsUsed = json.getInt("hintsUsed"),
            history = history,
        )
        if (listOf(state.values, state.notes, puzzle.givens, puzzle.solution).all { it.size == 81 } &&
            state.selected in 0..80 && state.hintsUsed in 0..3 &&
            history.all { it.values.size == 81 && it.notes.size == 81 }
        ) state else null
    } catch (_: Exception) { null }

    private fun JSONArray.toIntList(): List<Int> = (0 until length()).map { getInt(it) }

    companion object {
        const val FREE_KEY = "sudoku_free"
        @Volatile private var instance: SudokuStorage? = null
        fun get(context: Context): SudokuStorage = instance ?: synchronized(this) {
            instance ?: SudokuStorage(context.applicationContext).also { instance = it }
        }
    }
}
