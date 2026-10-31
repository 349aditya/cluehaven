package com.cluehaven.puzzlegames.data

import com.cluehaven.puzzlegames.game.sudoku.*
import com.cluehaven.puzzlegames.game.nonogram.*
import com.cluehaven.puzzlegames.game.pipes.*
import com.cluehaven.puzzlegames.game.tilemerge.*
import com.cluehaven.puzzlegames.game.blocks.BlockState
import com.cluehaven.puzzlegames.game.daily.DailySpec

fun SudokuState.playRecord() = PlayRecord(
    "sudoku_${puzzle.id}", "Sudoku", "${puzzle.difficulty.name.lowercase().replaceFirstChar { it.uppercase() }} · ${puzzle.size}×${puzzle.size}",
    if (mode == SudokuMode.DAILY) "Daily · ${puzzle.id.removePrefix("daily-")}" else "Free play",
    hintsUsed = hintsUsed,
)

fun NonogramState.playRecord() = PlayRecord(
    "nonogram_${puzzle.id}", "Nonogram", (if (puzzle.endless != null) "Endless · " else "") + "${puzzle.size}×${puzzle.size} · ${puzzle.difficulty.label}",
    if (puzzle.id.startsWith("daily-")) "Daily · ${puzzle.id.removePrefix("daily-")} · ${puzzle.title}" else puzzle.title,
    hintsUsed = hintsUsed,
)

fun PipeState.playRecord() = PlayRecord("pipes_${size}_$seed", "Pipe Connect", "$size×$size", "Network", hintsUsed = hintsUsed)

fun TileMergeState.playRecord() = PlayRecord("tile_${runId.ifBlank { "legacy-$rngState" }}", "Tile Merge", "4×4", "Run ended", score = score)

fun BlockState.playRecord() = PlayRecord("blocks_$runId", "Block Garden", "8×8", "Run ended · $lines lines cleared", score = score)

fun PlayRecord.forDailyFive(spec: DailySpec): PlayRecord = copy(id = spec.key, game = "Daily Five",
    category = "${spec.game.title} · ${spec.difficulty.label} · ${spec.date}", title = spec.objective)
