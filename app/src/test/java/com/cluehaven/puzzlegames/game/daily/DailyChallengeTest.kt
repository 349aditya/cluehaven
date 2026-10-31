package com.cluehaven.puzzlegames.game.daily

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import com.cluehaven.puzzlegames.game.nonogram.NonogramEngine

class DailyChallengeTest {
    private val date = LocalDate.of(2026, 10, 31)

    @Test fun sameDateProducesIdenticalChallengesForEveryGame() {
        DailyGame.entries.forEach { game -> DailyDifficulty.entries.forEach { difficulty ->
            val spec = DailyChallengeFactory.spec(date, game, difficulty)
            assertEquals(spec, DailyChallengeFactory.spec(date, game, difficulty))
            assertEquals(DailyChallengeFactory.initial(spec), DailyChallengeFactory.initial(spec))
            assertNotEquals(spec.key, DailyChallengeFactory.spec(date.plusDays(1), game, difficulty).key)
            assertNotEquals(spec.seed, DailyChallengeFactory.spec(date.plusDays(1), game, difficulty).seed)
        } }
    }

    @Test fun eachDifficultyUsesItsOwnSeedButSharesOneCreditKeyPerGameAndDate() {
        DailyGame.entries.forEach { game ->
            val specs = DailyDifficulty.entries.map { DailyChallengeFactory.spec(date, game, it) }
            assertEquals(3, specs.map { it.seed }.distinct().size)
            assertEquals(1, specs.map { it.key }.distinct().size)
        }
        DailyDifficulty.entries.forEach { difficulty ->
            val state = DailyChallengeFactory.initial(DailyChallengeFactory.spec(date, DailyGame.SUDOKU, difficulty)) as DailyState.Sudoku
            assertEquals(difficulty.name, state.value.puzzle.difficulty.name)
            assertEquals(if (difficulty == DailyDifficulty.EASY) 6 else 9, state.value.puzzle.size)
        }
    }

    @Test fun nonogramRotationUsesEveryAuthoredPictureInTheChosenDifficultyBeforeRepeating() {
        DailyDifficulty.entries.forEach { difficulty ->
            val pool = NonogramEngine.puzzles.filter { it.difficulty.name == difficulty.name }
            val selected = (0..pool.size).map { offset ->
                val spec = DailyChallengeFactory.spec(date.plusDays(offset.toLong()), DailyGame.NONOGRAM, difficulty)
                val picture = DailyChallengeFactory.initial(spec) as DailyState.Nonogram
                assertEquals(difficulty.name, picture.value.puzzle.difficulty.name)
                assertEquals(if (difficulty == DailyDifficulty.EASY) 5 else 10, picture.value.puzzle.size)
                picture.value.puzzle.id
            }
            assertEquals(pool.map { it.id }.toSet(), selected.take(pool.size).toSet())
            assertEquals(selected.first(), selected.last())
        }
    }

    @Test fun pipeDifficultiesUseFourFiveAndSixAndWinOnlyOnConnection() {
        DailyDifficulty.entries.zip(listOf(4, 5, 6)).forEach { (difficulty, size) ->
            val spec = DailyChallengeFactory.spec(date, DailyGame.PIPES, difficulty)
            val state = DailyChallengeFactory.initial(spec) as DailyState.Pipes
            assertEquals(size, state.value.size)
            assertFalse(DailyRules.complete(spec, state))
            assertTrue(DailyRules.complete(spec, DailyState.Pipes(state.value.copy(tiles = state.value.solution))))
        }
    }

    @Test fun scoreGamesWinAtTheirChosenTargetsWithoutWaitingForGameOver() {
        DailyDifficulty.entries.zip(listOf(500 to 1000, 1000 to 3000, 2000 to 5000)).forEach { (difficulty, targets) ->
            for ((game, target) in listOf(DailyGame.TILE_MERGE to targets.first, DailyGame.BLOCKS to targets.second)) {
                val spec = DailyChallengeFactory.spec(date, game, difficulty)
                assertEquals(target, spec.target)
                assertEquals(DailyObjective.SCORE, spec.objectiveKind)
                when (val initial = DailyChallengeFactory.initial(spec)) {
                    is DailyState.Tile -> {
                        assertFalse(DailyRules.complete(spec, DailyState.Tile(initial.value.copy(score = target - 1))))
                        assertTrue(DailyRules.complete(spec, DailyState.Tile(initial.value.copy(score = target))))
                        assertTrue(DailyRules.complete(spec, DailyState.Tile(initial.value.copy(score = target + 4))))
                        assertFalse(initial.value.isGameOver)
                        assertFalse(DailyRules.complete(spec, DailyState.Tile(initial.value.copy(board = List(16) { 2048 }))))
                    }
                    is DailyState.Blocks -> {
                        assertFalse(DailyRules.complete(spec, DailyState.Blocks(initial.value.copy(score = target - 1, lines = 10))))
                        assertTrue(DailyRules.complete(spec, DailyState.Blocks(initial.value.copy(score = target))))
                        assertTrue(DailyRules.complete(spec, DailyState.Blocks(initial.value.copy(score = target + 100))))
                        assertFalse(initial.value.isGameOver)
                    }
                    else -> error("Expected a score game")
                }
            }
        }
    }

    @Test fun solvedFiniteBoardsWinAndUnsolvedBoardsDoNot() {
        for (game in listOf(DailyGame.SUDOKU, DailyGame.NONOGRAM)) { DailyDifficulty.entries.forEach { difficulty ->
            val spec = DailyChallengeFactory.spec(date, game, difficulty)
            val initial = DailyChallengeFactory.initial(spec)
            assertFalse(DailyRules.complete(spec, initial))
            val solved = when (initial) {
                is DailyState.Sudoku -> DailyState.Sudoku(initial.value.copy(values = initial.value.puzzle.solution))
                is DailyState.Nonogram -> DailyState.Nonogram(initial.value.copy(cells = initial.value.puzzle.solution))
                else -> error("Unexpected game")
            }
            assertTrue(DailyRules.complete(spec, solved))
        } }
    }

    @Test fun otherDatesCannotReplaceTheOpenedChallenge() {
        for (game in listOf(DailyGame.SUDOKU, DailyGame.TILE_MERGE, DailyGame.PIPES, DailyGame.BLOCKS)) {
            val initial = DailyChallengeFactory.initial(DailyChallengeFactory.spec(date, game))
            val other = DailyChallengeFactory.initial(DailyChallengeFactory.spec(date.plusDays(1), game))
            assertFalse(DailyRules.sameChallenge(initial, other))
        }
    }

    @Test fun savedLegacyTileAndLineGoalsKeepTheirOriginalMeaning() {
        val tile = DailyChallengeFactory.spec(date, DailyGame.TILE_MERGE).copy(target = 256, objectiveKind = DailyObjective.TILE_VALUE, version = 1)
        val garden = DailyChallengeFactory.spec(date, DailyGame.BLOCKS).copy(target = 10, objectiveKind = DailyObjective.LINES, version = 1)
        val t = DailyChallengeFactory.initial(tile) as DailyState.Tile
        val b = DailyChallengeFactory.initial(garden) as DailyState.Blocks
        assertFalse(DailyRules.complete(tile, DailyState.Tile(t.value.copy(score = 9140))))
        assertTrue(DailyRules.complete(tile, DailyState.Tile(t.value.copy(board = List(16) { 256 }))))
        assertFalse(DailyRules.complete(garden, DailyState.Blocks(b.value.copy(score = 9140, lines = 9))))
        assertTrue(DailyRules.complete(garden, DailyState.Blocks(b.value.copy(lines = 10))))
    }

    @Test fun streakThresholdIsSeparateFromAllFiveAndAcceptsThreeWithoutLogicChanges() {
        val three = DailyGame.entries.take(3).toSet()
        assertEquals(5, DailyFiveConfig.requiredGamesForStreak)
        assertFalse(DailyRules.qualifiesForStreak(three, 5))
        assertTrue(DailyRules.qualifiesForStreak(three, 3))
        assertTrue(DailyRules.qualifiesForStreak(DailyGame.entries.toSet(), 5))
        assertThrows(IllegalArgumentException::class.java) { DailyRules.qualifiesForStreak(three, 0) }
        assertThrows(IllegalArgumentException::class.java) { DailyRules.qualifiesForStreak(three, 6) }
    }
}
