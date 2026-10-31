package com.cluehaven.puzzlegames.data

import com.cluehaven.puzzlegames.game.blocks.BlockState
import com.cluehaven.puzzlegames.game.sudoku.SudokuState

fun sudokuBoardStatus(state: SudokuState?): String = when {
    state == null -> "No active board"
    state.isComplete -> "Solved board saved"
    state.values.none { it == 0 } -> "Board filled · Check entries"
    else -> "In progress · ${state.values.count { it == 0 }} empty cells"
}

fun blockRunStatus(state: BlockState?): String = when {
    state == null -> "No active run"
    state.isGameOver -> "Finished run saved"
    else -> "In progress · Score ${state.score}"
}
