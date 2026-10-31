package com.cluehaven.puzzlegames.game.pipes

import org.json.JSONArray
import org.json.JSONObject

internal object PipeCodec {
    fun encode(state: PipeState): String = JSONObject().put("size", state.size).put("seed", state.seed)
        .put("solution", JSONArray(state.solution)).put("tiles", JSONArray(state.tiles))
        .put("moves", state.moves).put("hints", state.hintsUsed)
        .put("history", JSONArray().apply { state.history.forEach { put(JSONArray(it)) } }).toString()

    fun decode(size: Int, payload: String): PipeState? = runCatching {
        val json = JSONObject(payload)
        fun values(array: JSONArray) = List(array.length()) { array.getInt(it) }
        val solution = json.optJSONArray("solution")?.let(::values)
        val base = if (solution == null) PipeEngine.newGame(size, json.getLong("seed"))
            else PipeState(size, json.getLong("seed"), solution, solution).also {
                require(solution.size == size * size && solution.all { mask -> mask in 1..15 })
                require(it.isComplete)
            }
        fun read(array: JSONArray) = values(array).also { cells ->
            require(cells.size == size * size)
            require(cells.indices.all { index ->
                var mask = base.solution[index]
                val rotations = mutableSetOf<Int>()
                repeat(4) { rotations.add(mask); mask = PipeEngine.rotate(mask) }
                cells[index] in rotations
            })
        }
        val history = json.getJSONArray("history")
        require(history.length() <= 100)
        val moves = json.getInt("moves")
        val hints = json.getInt("hints")
        require(moves >= 0 && hints in 0..1)
        base.copy(tiles = read(json.getJSONArray("tiles")), moves = moves, hintsUsed = hints,
            history = List(history.length()) { read(history.getJSONArray(it)) })
    }.getOrNull()
}
