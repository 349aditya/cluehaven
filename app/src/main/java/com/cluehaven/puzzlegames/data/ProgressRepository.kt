package com.cluehaven.puzzlegames.data

import android.content.Context
import com.cluehaven.puzzlegames.game.sudoku.SudokuState
import com.cluehaven.puzzlegames.game.tilemerge.TileMergeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate

class ProgressRepository private constructor(context: Context) {
    private val dao = CluehavenDatabase.get(context).progress()
    private val writes = Channel<ProgressEvent>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sudokuCompletions: Flow<List<SudokuCompletion>> = dao.observeSudokuCompletions()
    val tileMilestones: Flow<List<TileMergeMilestone>> = dao.observeTileMilestones()

    init {
        scope.launch {
            for (event in writes) {
                when (event) {
                    is ProgressEvent.Sudoku -> dao.addSudokuCompletion(event.value)
                    is ProgressEvent.Tile -> dao.addTileMilestone(event.value)
                }
            }
        }
    }

    fun recordSudoku(state: SudokuState) {
        if (!state.isComplete) return
        writes.trySend(ProgressEvent.Sudoku(SudokuCompletion(
            puzzleId = state.puzzle.id,
            difficulty = state.puzzle.difficulty.name,
            mode = state.mode.name,
            completedAt = System.currentTimeMillis(),
            hintsUsed = state.hintsUsed,
        )))
    }

    fun recordTile2048(state: TileMergeState) {
        if (!state.reached2048) return
        writes.trySend(ProgressEvent.Tile(TileMergeMilestone(
            runId = state.runId.ifBlank { "legacy-${state.rngState}" },
            score = state.score,
            achievedAt = System.currentTimeMillis(),
        )))
    }

    private sealed interface ProgressEvent {
        data class Sudoku(val value: SudokuCompletion) : ProgressEvent
        data class Tile(val value: TileMergeMilestone) : ProgressEvent
    }

    companion object {
        @Volatile private var instance: ProgressRepository? = null
        fun get(context: Context): ProgressRepository = instance ?: synchronized(this) {
            instance ?: ProgressRepository(context.applicationContext).also { instance = it }
        }
    }
}

data class SudokuStats(
    val total: Int,
    val easy: Int,
    val medium: Int,
    val hard: Int,
    val dailySolved: Int,
    val dailyStreak: Int,
    val todayComplete: Boolean,
    val solvedWithHints: Int,
)

fun summarizeSudoku(completions: List<SudokuCompletion>, today: LocalDate): SudokuStats {
    val dailyDates = completions.asSequence()
        .filter { it.mode == "DAILY" && it.puzzleId.startsWith("daily-") }
        .mapNotNull { runCatching { LocalDate.parse(it.puzzleId.removePrefix("daily-")) }.getOrNull() }
        .toSet()
    var day = if (today in dailyDates) today else today.minusDays(1)
    var streak = 0
    while (day in dailyDates) {
        streak++
        day = day.minusDays(1)
    }
    return SudokuStats(
        total = completions.size,
        easy = completions.count { it.difficulty == "EASY" },
        medium = completions.count { it.difficulty == "MEDIUM" },
        hard = completions.count { it.difficulty == "HARD" },
        dailySolved = dailyDates.size,
        dailyStreak = streak,
        todayComplete = today in dailyDates,
        solvedWithHints = completions.count { it.hintsUsed > 0 },
    )
}
