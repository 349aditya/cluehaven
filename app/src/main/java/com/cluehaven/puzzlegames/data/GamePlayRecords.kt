package com.cluehaven.puzzlegames.data

import com.cluehaven.puzzlegames.game.sudoku.*
import com.cluehaven.puzzlegames.game.nonogram.*
import com.cluehaven.puzzlegames.game.pipes.*
import com.cluehaven.puzzlegames.game.tilemerge.*

fun SudokuState.playRecord() = PlayRecord(
    "sudoku_${puzzle.id}", "Sudoku", "${puzzle.difficulty.name.lowercase().replaceFirstChar { it.uppercase() }} · ${puzzle.size}×${puzzle.size}",
    if (mode == SudokuMode.DAILY) "Daily · ${puzzle.id.removePrefix("daily-")}" else "Free play",
    hintsUsed = hintsUsed,
)

fun NonogramState.playRecord() = PlayRecord(
    "nonogram_${puzzle.id}", "Nonogram", "${puzzle.size}×${puzzle.size} · ${puzzle.difficulty.label}",
    if (puzzle.id.startsWith("daily-")) "Daily · ${puzzle.id.removePrefix("daily-")} · ${puzzle.title}" else puzzle.title,
    hintsUsed = hintsUsed,
)

fun PipeState.playRecord() = PlayRecord("pipes_${size}_$seed", "Pipe Connect", "$size×$size", "Network", hintsUsed = hintsUsed)

fun TileMergeState.playRecord() = PlayRecord("tile_${runId.ifBlank { "legacy-$rngState" }}", "Tile Merge", "4×4", "Run ended", score = score)
