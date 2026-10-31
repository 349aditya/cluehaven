package com.cluehaven.puzzlegames.game.daily

import com.cluehaven.puzzlegames.game.sudoku.*
import com.cluehaven.puzzlegames.game.nonogram.*
import com.cluehaven.puzzlegames.game.pipes.*
import com.cluehaven.puzzlegames.game.tilemerge.*
import com.cluehaven.puzzlegames.game.blocks.*
import java.time.LocalDate

enum class DailyDifficulty(val label: String, val seedSalt: Long) {
    EASY("Easy", 101), MEDIUM("Medium", 211), HARD("Hard", 307)
}

// TILE_VALUE and LINES preserve goals of already-opened v4 development challenges only.
enum class DailyObjective { SOLVE, SCORE, TILE_VALUE, LINES }

enum class DailyGame(val key: String, val title: String, val growth: String, val seedSalt: Long) {
    SUDOKU("sudoku", "Sudoku", "Tree", 11),
    TILE_MERGE("tile_merge", "Tile Merge", "Lanterns", 23),
    NONOGRAM("nonogram", "Nonogram", "Flowers", 37),
    PIPES("pipes", "Pipe Connect", "Water channel", 53),
    BLOCKS("blocks", "Block Garden", "Garden bed", 71);

    companion object { fun fromKey(key: String) = entries.first { it.key == key } }
}

sealed interface DailyState {
    val game: DailyGame
    data class Sudoku(val value: SudokuState) : DailyState { override val game = DailyGame.SUDOKU }
    data class Tile(val value: TileMergeState) : DailyState { override val game = DailyGame.TILE_MERGE }
    data class Nonogram(val value: NonogramState) : DailyState { override val game = DailyGame.NONOGRAM }
    data class Pipes(val value: PipeState) : DailyState { override val game = DailyGame.PIPES }
    data class Blocks(val value: BlockState) : DailyState { override val game = DailyGame.BLOCKS }
}

data class DailySpec(val date: LocalDate, val game: DailyGame, val seed: Long, val target: Int,
    val version: Int = DailyFiveConfig.GENERATOR_VERSION,
    val difficulty: DailyDifficulty = DailyDifficulty.MEDIUM,
    val objectiveKind: DailyObjective = DailyObjective.SOLVE) {
    val key: String get() = "dailyfive_${date}_${game.key}"
    val objective: String get() = when (objectiveKind) {
        DailyObjective.SCORE -> "${difficulty.label} · Score $target points in ${game.title}"
        DailyObjective.TILE_VALUE -> "Reach the $target tile"
        DailyObjective.LINES -> "Clear $target lines"
        DailyObjective.SOLVE -> when (game) {
            DailyGame.SUDOKU -> "Solve the ${difficulty.label} ${target}×$target Sudoku"
            DailyGame.NONOGRAM -> "Solve the ${difficulty.label} ${target}×$target picture"
            DailyGame.PIPES -> "${difficulty.label} · Connect every pipe in the ${target}×$target network"
            else -> error("Score games need a score objective")
        }
    }
}

data class DailyGameSession<T>(val state: T, val update: (T) -> Unit, val retry: () -> Unit)

object DailyChallengeFactory {
    fun spec(date: LocalDate, game: DailyGame, difficulty: DailyDifficulty = DailyDifficulty.MEDIUM): DailySpec {
        // Numeric salts and epoch days avoid wall-clock time and process-dependent hashes.
        val seed = date.toEpochDay() * 104729L + game.seedSalt * 1009L + difficulty.seedSalt
        val target = when (game) {
            DailyGame.SUDOKU -> DailyFiveConfig.sudokuSize(difficulty)
            DailyGame.NONOGRAM -> DailyFiveConfig.nonogramSize(difficulty)
            DailyGame.PIPES -> DailyFiveConfig.pipeSize(difficulty)
            DailyGame.TILE_MERGE -> DailyFiveConfig.tileMergeScoreTargets.getValue(difficulty)
            DailyGame.BLOCKS -> DailyFiveConfig.blockGardenScoreTargets.getValue(difficulty)
        }
        val objective = if (game in setOf(DailyGame.TILE_MERGE, DailyGame.BLOCKS)) DailyObjective.SCORE else DailyObjective.SOLVE
        return DailySpec(date, game, seed, target, difficulty = difficulty, objectiveKind = objective)
    }

    fun initial(spec: DailySpec): DailyState = when (spec.game) {
        DailyGame.SUDOKU -> DailyState.Sudoku(SudokuState(
            SudokuEngine.generate(SudokuDifficulty.valueOf(spec.difficulty.name), spec.seed, "daily-${spec.date}"), SudokuMode.DAILY))
        DailyGame.NONOGRAM -> {
            val puzzles = NonogramEngine.puzzles.filter {
                it.size == spec.target && it.difficulty.name == spec.difficulty.name
            }
            require(puzzles.isNotEmpty()) { "No authored Nonogram puzzles for ${spec.difficulty}" }
            // A date rotation visits every picture in this difficulty before repeating.
            val index = Math.floorMod(spec.date.toEpochDay() + spec.difficulty.seedSalt, puzzles.size.toLong()).toInt()
            DailyState.Nonogram(NonogramState(puzzles[index]))
        }
        DailyGame.PIPES -> DailyState.Pipes(PipeEngine.newGame(spec.target, spec.seed))
        DailyGame.TILE_MERGE -> DailyState.Tile(TileMergeEngine.newGame(spec.seed))
        DailyGame.BLOCKS -> DailyState.Blocks(BlockEngine.newGame(spec.seed))
    }
}

object DailyRules {
    /** Contract for the later Haven streak; this does not process missed/rest dates. */
    fun qualifiesForStreak(completedGames: Set<DailyGame>, requiredGames: Int): Boolean {
        require(requiredGames in 1..DailyGame.entries.size)
        return completedGames.size >= requiredGames
    }

    fun complete(spec: DailySpec, state: DailyState): Boolean {
        require(spec.game == state.game)
        when (spec.objectiveKind) {
            DailyObjective.SCORE -> return when (state) {
                is DailyState.Tile -> state.value.score >= spec.target
                is DailyState.Blocks -> state.value.score >= spec.target
                else -> error("Only score games support score objectives")
            }
            DailyObjective.TILE_VALUE -> return state is DailyState.Tile && state.value.board.any { it >= spec.target }
            DailyObjective.LINES -> return state is DailyState.Blocks && state.value.lines >= spec.target
            DailyObjective.SOLVE -> Unit
        }
        return when (state) {
            is DailyState.Sudoku -> state.value.isComplete
            is DailyState.Nonogram -> state.value.isComplete
            is DailyState.Pipes -> state.value.isComplete
            else -> error("Score games need a score objective")
        }
    }

    fun sameChallenge(initial: DailyState, next: DailyState): Boolean = when {
        initial is DailyState.Sudoku && next is DailyState.Sudoku -> initial.value.puzzle == next.value.puzzle && initial.value.mode == next.value.mode
        initial is DailyState.Nonogram && next is DailyState.Nonogram -> initial.value.puzzle == next.value.puzzle
        initial is DailyState.Pipes && next is DailyState.Pipes -> initial.value.size == next.value.size && initial.value.seed == next.value.seed && initial.value.solution == next.value.solution
        initial is DailyState.Tile && next is DailyState.Tile -> initial.value.runId == next.value.runId
        initial is DailyState.Blocks && next is DailyState.Blocks -> initial.value.runId == next.value.runId
        else -> false
    }
}
