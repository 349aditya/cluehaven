package com.cluehaven.puzzlegames.game.tilemerge

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

class TileMergeStorage private constructor(context: Context) {
    private val legacyPreferences = context.applicationContext.getSharedPreferences(
        "tile_merge_progress", Context.MODE_PRIVATE,
    )
    private val sessions = CluehavenDatabase.get(context).puzzleSessions()
    private val saves = Channel<TileMergeState>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var latest: TileMergeState? = null

    init {
        scope.launch {
            for (state in saves) {
                sessions.save(PuzzleSession("tile_merge", encode(state), System.currentTimeMillis()))
            }
        }
    }

    suspend fun load(): TileMergeState {
        latest?.let { return it }
        val existing = sessions.get("tile_merge")
        val saved = existing?.payload ?: legacyPreferences.getString("current_run", null)
        val loaded = decode(saved) ?: TileMergeEngine.newGame()
        latest = loaded
        if (existing == null && saved != null) {
            sessions.save(PuzzleSession("tile_merge", encode(loaded), System.currentTimeMillis()))
            legacyPreferences.edit().remove("current_run").apply()
        }
        return loaded
    }

    fun save(state: TileMergeState) {
        latest = state
        check(saves.trySend(state).isSuccess) { "Tile Merge save queue is unavailable" }
    }

    private fun decode(saved: String?): TileMergeState? {
        if (saved == null) return null
        return try {
            val root = JSONObject(saved)
            val board = root.getJSONArray("board").toIntList()
            val history = root.getJSONArray("history").let { items ->
                (0 until items.length()).map { index ->
                    val item = items.getJSONObject(index)
                    TileMergeSnapshot(
                        board = item.getJSONArray("board").toIntList(),
                        score = item.getInt("score"),
                        rngState = item.getLong("rngState"),
                        moveCount = item.getInt("moveCount"),
                        reached2048 = item.getBoolean("reached2048"),
                    )
                }
            }
            TileMergeState(
                board = board,
                score = root.getInt("score"),
                bestScore = root.getInt("bestScore"),
                rngState = root.getLong("rngState"),
                moveCount = root.getInt("moveCount"),
                undosUsed = root.getInt("undosUsed"),
                reached2048 = root.getBoolean("reached2048"),
                isGameOver = !TileMergeEngine.canMove(board),
                history = history,
            )
        } catch (_: Exception) { null }
    }

    private fun encode(state: TileMergeState): String = JSONObject()
            .put("board", JSONArray(state.board))
            .put("score", state.score)
            .put("bestScore", state.bestScore)
            .put("rngState", state.rngState)
            .put("moveCount", state.moveCount)
            .put("undosUsed", state.undosUsed)
            .put("reached2048", state.reached2048)
            .put("history", JSONArray().apply {
                state.history.forEach { snapshot ->
                    put(JSONObject()
                        .put("board", JSONArray(snapshot.board))
                        .put("score", snapshot.score)
                        .put("rngState", snapshot.rngState)
                        .put("moveCount", snapshot.moveCount)
                        .put("reached2048", snapshot.reached2048))
                }
            })
        .toString()

    private fun JSONArray.toIntList(): List<Int> = (0 until length()).map { getInt(it) }

    companion object {
        @Volatile private var instance: TileMergeStorage? = null

        fun get(context: Context): TileMergeStorage = instance ?: synchronized(this) {
            instance ?: TileMergeStorage(context.applicationContext).also { instance = it }
        }
    }
}
