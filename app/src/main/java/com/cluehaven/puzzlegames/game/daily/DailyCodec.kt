package com.cluehaven.puzzlegames.game.daily

import com.cluehaven.puzzlegames.game.sudoku.SudokuCodec
import com.cluehaven.puzzlegames.game.nonogram.*
import com.cluehaven.puzzlegames.game.pipes.PipeCodec
import com.cluehaven.puzzlegames.game.tilemerge.TileMergeCodec
import com.cluehaven.puzzlegames.game.blocks.BlockCodec
import org.json.JSONArray
import org.json.JSONObject

internal object DailyCodec {
    fun encode(state: DailyState): String = JSONObject().apply {
        put("payload", when (state) {
            is DailyState.Sudoku -> SudokuCodec.encode(state.value)
            is DailyState.Tile -> TileMergeCodec.encode(state.value)
            is DailyState.Pipes -> PipeCodec.encode(state.value)
            is DailyState.Blocks -> BlockCodec.encode(state.value)
            is DailyState.Nonogram -> {
                val puzzle = state.value.puzzle
                put("puzzle", JSONObject().put("id", puzzle.id).put("size", puzzle.size)
                    .put("solution", JSONArray(puzzle.solution)).put("title", puzzle.title)
                    .put("difficulty", puzzle.difficulty.name).put("sourceId", puzzle.sourceId))
                NonogramCodec.encode(state.value)
            }
        })
    }.toString()

    fun decode(game: DailyGame, payload: String): DailyState = JSONObject(payload).let { root ->
        val saved = root.getString("payload")
        when (game) {
            DailyGame.SUDOKU -> DailyState.Sudoku(requireNotNull(SudokuCodec.decode(saved)))
            DailyGame.TILE_MERGE -> DailyState.Tile(requireNotNull(TileMergeCodec.decode(saved)))
            DailyGame.BLOCKS -> DailyState.Blocks(requireNotNull(BlockCodec.decode(saved)))
            DailyGame.PIPES -> {
                val size = JSONObject(saved).getInt("size")
                DailyState.Pipes(requireNotNull(PipeCodec.decode(size, saved)))
            }
            DailyGame.NONOGRAM -> {
                val p = root.getJSONObject("puzzle")
                val array = p.getJSONArray("solution")
                val puzzle = NonogramPuzzle(p.getString("id"), p.getInt("size"), List(array.length()) { array.getInt(it) },
                    p.getString("title"), NonogramDifficulty.valueOf(p.getString("difficulty")), p.getString("sourceId"))
                DailyState.Nonogram(requireNotNull(NonogramCodec.decode(puzzle, saved)))
            }
        }
    }
}
