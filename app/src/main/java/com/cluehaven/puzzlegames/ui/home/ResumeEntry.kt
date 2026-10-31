package com.cluehaven.puzzlegames.ui.home

import com.cluehaven.puzzlegames.data.PuzzleSession
import com.cluehaven.puzzlegames.game.pipes.PipeEngine
import com.cluehaven.puzzlegames.game.tilemerge.TileMergeEngine
import com.cluehaven.puzzlegames.game.blocks.BlockEngine
import com.cluehaven.puzzlegames.game.nonogram.NonogramCodec
import org.json.JSONObject
import java.time.LocalDate

data class ResumeEntry(val title: String, val route: String)

fun resumeEntry(session: PuzzleSession, today: LocalDate = LocalDate.now()): ResumeEntry? = runCatching {
    val json = JSONObject(session.payload)
    fun values(key: String): List<Int> = json.getJSONArray(key).let { array ->
        (0 until array.length()).map { array.getInt(it) }
    }
    when {
        session.gameId.startsWith("sudoku_") -> {
            if (values("values") == values("solution")) null
            else if (session.gameId == "sudoku_free") ResumeEntry("Sudoku · Your saved board", "sudoku|free")
            else if (session.gameId == "sudoku_daily-$today")
                ResumeEntry("Today's Sudoku", "sudoku_daily") else null
        }
        session.gameId == "tile_merge" -> if (TileMergeEngine.canMove(values("board")))
            ResumeEntry("Tile Merge · Score ${json.getInt("score")}", "tile_merge") else null
        session.gameId == "block_garden" -> {
            val board = values("board")
            val tray = json.getJSONArray("tray")
            val playable = (0 until tray.length()).any {
                !tray.isNull(it) && BlockEngine.placements(board, BlockEngine.shape(tray.getString(it))).isNotEmpty()
            }
            if (playable) ResumeEntry("Block Garden · Score ${json.getInt("score")}", "blocks") else null
        }
        session.gameId.startsWith("nonogram_endless-") -> NonogramCodec.restoreEndless(session.payload)?.takeIf { !it.isComplete }?.let {
            ResumeEntry("Nonogram · Endless · ${it.puzzle.size}×${it.puzzle.size} · ${it.puzzle.difficulty.label}",
                "nonogram|${session.gameId.removePrefix("nonogram_")}")
        }
        session.gameId.startsWith("nonogram_") -> if (!json.optBoolean("complete"))
            ResumeEntry("Nonogram · Your saved picture", "nonogram|${session.gameId.removePrefix("nonogram_")}") else null
        session.gameId.startsWith("pipes_") -> {
            val size = session.gameId.removePrefix("pipes_").toInt()
            val tiles = values("tiles")
            if (PipeEngine.noLeaks(size, tiles) && PipeEngine.connected(size, tiles).size == tiles.size) null
            else ResumeEntry("Pipe Connect · $size×$size network", "pipes|$size")
        }
        else -> null
    }
}.getOrNull()
