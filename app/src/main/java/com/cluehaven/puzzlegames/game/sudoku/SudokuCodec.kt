package com.cluehaven.puzzlegames.game.sudoku

import org.json.JSONArray
import org.json.JSONObject

internal object SudokuCodec {
    fun encode(state: SudokuState): String = JSONObject()
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

    fun decode(payload: String): SudokuState? = try {
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
        val cells = puzzle.givens.size
        if (cells in setOf(36, 81) &&
            listOf(state.values, state.notes, puzzle.solution).all { it.size == cells } &&
            state.selected in 0 until cells && state.hintsUsed in 0..3 &&
            history.all { it.values.size == cells && it.notes.size == cells }
        ) state else null
    } catch (_: Exception) { null }

    private fun JSONArray.toIntList(): List<Int> = (0 until length()).map { getInt(it) }

}
