package com.cluehaven.puzzlegames.game.sudoku

import java.time.LocalDate

internal fun resolveSudokuSession(
    current: SudokuState?,
    activeMode: String,
    freeSave: SudokuState?,
    dailySave: SudokuState?,
    today: LocalDate,
): SudokuState? = current ?: when (activeMode) {
    "free" -> freeSave
    "daily" -> dailySave?.takeIf { it.puzzle.id == "daily-$today" }
        ?: SudokuState(SudokuEngine.generate(SudokuDifficulty.MEDIUM,
            today.toEpochDay(), "daily-$today"), SudokuMode.DAILY)
    else -> null
}
