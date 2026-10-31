package com.cluehaven.puzzlegames.game.nonogram

import org.json.JSONArray
import org.json.JSONObject

internal object NonogramCodec {
    fun encode(state: NonogramState): String = JSONObject()
        .put("sourceId", state.puzzle.sourceId)
        .put("complete", state.isComplete)
        .put("cells", JSONArray(state.cells))
        .put("hintsUsed", state.hintsUsed)
        .put("history", JSONArray().apply { state.history.forEach { put(JSONArray(it)) } })
        .toString()

    fun decode(puzzle: NonogramPuzzle, payload: String): NonogramState? = try {
        val json = JSONObject(payload)
        val savedPuzzle = NonogramEngine.puzzleForSavedSession(puzzle,
            if (json.has("sourceId")) json.getString("sourceId") else null)
        fun cells(array: JSONArray): List<Int> = (0 until array.length()).map { array.getInt(it) }
            .also { values -> require(values.size == savedPuzzle.size * savedPuzzle.size && values.all { it in 0..2 }) }
        val history = json.getJSONArray("history")
        require(history.length() <= 50)
        val hints = json.getInt("hintsUsed")
        require(hints in 0..1)
        NonogramState(savedPuzzle, cells(json.getJSONArray("cells")),
            (0 until history.length()).map { cells(history.getJSONArray(it)) }, hints)
    } catch (_: Exception) { null }

}
