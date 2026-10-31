package com.cluehaven.puzzlegames.game.daily

/** New dates snapshot the streak requirement; Start snapshots a game's difficulty and target. */
object DailyFiveConfig {
    const val GENERATOR_VERSION = 2
    const val requiredGamesForStreak = 5

    val tileMergeScoreTargets = mapOf(
        DailyDifficulty.EASY to 500,
        DailyDifficulty.MEDIUM to 1_000,
        DailyDifficulty.HARD to 2_000,
    )
    val blockGardenScoreTargets = mapOf(
        DailyDifficulty.EASY to 1_000,
        DailyDifficulty.MEDIUM to 3_000,
        DailyDifficulty.HARD to 5_000,
    )

    fun sudokuSize(difficulty: DailyDifficulty) = if (difficulty == DailyDifficulty.EASY) 6 else 9
    fun nonogramSize(difficulty: DailyDifficulty) = if (difficulty == DailyDifficulty.EASY) 5 else 10
    fun pipeSize(difficulty: DailyDifficulty) = when (difficulty) {
        DailyDifficulty.EASY -> 4
        DailyDifficulty.MEDIUM -> 5
        DailyDifficulty.HARD -> 6
    }
}
