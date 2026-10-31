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
        .apply {
            state.puzzle.endless?.let { info ->
                put("generatorVersion", info.generatorVersion)
                put("generatedPuzzle", JSONObject().put("id", state.puzzle.id).put("size", state.puzzle.size)
                    .put("solution", JSONArray(state.puzzle.solution)).put("title", state.puzzle.title)
                    .put("difficulty", state.puzzle.difficulty.name).put("seed", info.seed)
                    .put("generatorVersion", info.generatorVersion).put("family", info.family).put("attempt", info.attempt)
                    .put("passes", info.report.passes).put("overlapCells", info.report.overlapCells)
                    .put("completionCells", info.report.completionCells).put("constrainedCells", info.report.constrainedCells)
                    .put("solverWork", info.report.work))
            }
        }
        .toString()

    /** Restore the full snapshot, including versions the current generator cannot create. */
    fun restoreEndless(payload: String): NonogramState? = runCatching {
        val json = JSONObject(payload)
        val p = json.getJSONObject("generatedPuzzle")
        val size = p.getInt("size")
        require(size in setOf(5, 10))
        val version = p.getInt("generatorVersion")
        require(version > 0 && json.getInt("generatorVersion") == version)
        val id = p.getString("id")
        val difficulty = NonogramDifficulty.valueOf(p.getString("difficulty"))
        val seed = p.getLong("seed")
        require(id == "endless-v$version-$size-${difficulty.name.lowercase()}-${java.lang.Long.toUnsignedString(seed, 16)}")
        val array = p.getJSONArray("solution")
        val solution = List(array.length()) { array.getInt(it) }
        val passes = p.getInt("passes")
        val overlap = p.getInt("overlapCells")
        val completion = p.getInt("completionCells")
        val constrained = p.getInt("constrainedCells")
        val work = p.getInt("solverWork")
        require(passes > 0 && work >= 0 && overlap >= 0 && completion >= 0 && constrained >= 0)
        require(overlap.toLong() + completion + constrained == size.toLong() * size)
        val report = NonogramSolveReport(NonogramSolveStatus.SOLVED, passes, overlap, completion, constrained, work, solution)
        val info = EndlessNonogramInfo(seed, version, p.getString("family"), p.getInt("attempt"), report)
        require(info.attempt > 0 && info.family.isNotBlank())
        val puzzle = NonogramPuzzle(id, size, solution, p.getString("title"),
            difficulty, endless = info)
        decode(puzzle, payload)
    }.getOrNull()

    fun decode(puzzle: NonogramPuzzle, payload: String): NonogramState? = try {
        val json = JSONObject(payload)
        val savedPuzzle = if (puzzle.endless != null) {
            require(json.getString("sourceId") == puzzle.id && json.getInt("generatorVersion") == puzzle.endless.generatorVersion)
            puzzle
        } else {
            require(!json.has("generatedPuzzle"))
            NonogramEngine.puzzleForSavedSession(puzzle, if (json.has("sourceId")) json.getString("sourceId") else null)
        }
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
