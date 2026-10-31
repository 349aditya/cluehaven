package com.cluehaven.puzzlegames.game.tilemerge

import org.json.JSONArray
import org.json.JSONObject

internal object TileMergeCodec {
    fun decode(saved: String?): TileMergeState? {
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
                runId = root.optString("runId").ifBlank {
                    "legacy-${root.getLong("rngState")}"
                },
                moveCount = root.getInt("moveCount"),
                undosUsed = root.getInt("undosUsed"),
                reached2048 = root.getBoolean("reached2048"),
                isGameOver = !TileMergeEngine.canMove(board),
                history = history,
            )
        } catch (_: Exception) { null }
    }

    fun encode(state: TileMergeState): String = JSONObject()
            .put("board", JSONArray(state.board))
            .put("score", state.score)
            .put("bestScore", state.bestScore)
            .put("rngState", state.rngState)
            .put("runId", state.runId)
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

}
