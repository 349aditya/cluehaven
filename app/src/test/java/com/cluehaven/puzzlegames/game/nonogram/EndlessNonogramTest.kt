package com.cluehaven.puzzlegames.game.nonogram

import org.junit.Assert.*
import org.junit.Test

class EndlessNonogramTest {
    private fun solve(picture: List<String>, maxWork: Int = 50_000) = NonogramLineSolver.solve(
        picture.map { NonogramEngine.clues(it.map(Char::digitToInt)) },
        picture.indices.map { column -> NonogramEngine.clues(picture.map { it[column].digitToInt() }) }, maxWork)

    @Test fun lineLogicSolvesACompletePictureAndRecordsItsTechniques() {
        val picture = listOf("00100", "01110", "11111", "01110", "00100")
        val report = solve(picture)
        assertEquals(NonogramSolveStatus.SOLVED, report.status)
        assertEquals(picture.joinToString("").map(Char::digitToInt), report.cells)
        assertTrue(report.passes > 0)
        assertTrue(report.techniques.isNotEmpty())
        assertEquals(25, report.overlapCells + report.completionCells + report.constrainedCells)
    }

    @Test fun ambiguousLinesStallWithoutGuessingAndContradictoryCluesAreRejected() {
        val ambiguous = NonogramLineSolver.solve(List(5) { listOf(1) }, List(5) { listOf(1) })
        assertEquals(NonogramSolveStatus.STALLED, ambiguous.status)
        assertTrue(ambiguous.cells.all { it == -1 })
        val contradiction = NonogramLineSolver.solve(List(5) { listOf(0) }, List(5) { listOf(5) })
        assertEquals(NonogramSolveStatus.CONTRADICTION, contradiction.status)
    }

    @Test fun everySizeAndDifficultyIsDeterministicAndFullySolvedByLineLogic() {
        for (size in listOf(5, 10)) for (difficulty in NonogramDifficulty.entries) for (seed in 0L..7L) {
            val first = EndlessNonogramGenerator.generate(seed, size, difficulty)
            assertTrue("$size $difficulty seed $seed: $first", first is EndlessGenerationResult.Ready)
            val puzzle = (first as EndlessGenerationResult.Ready).puzzle
            assertEquals(first, EndlessNonogramGenerator.generate(seed, size, difficulty))
            assertTrue(EndlessNonogramGenerator.acceptsPicture(size, puzzle.solution))
            val report = NonogramLineSolver.solve(puzzle.rows, puzzle.columns)
            assertEquals(NonogramSolveStatus.SOLVED, report.status)
            assertEquals(puzzle.solution, report.cells)
            assertEquals(difficulty, report.difficulty(size))
            assertEquals(report, puzzle.endless!!.report)
            assertEquals(1, puzzle.endless.generatorVersion)
        }
    }

    @Test fun acceptanceRejectsEmptyFullFlatAsymmetricAndNoisyPictures() {
        assertFalse(EndlessNonogramGenerator.acceptsPicture(5, List(25) { 0 }))
        assertFalse(EndlessNonogramGenerator.acceptsPicture(5, List(25) { 1 }))
        assertFalse(EndlessNonogramGenerator.acceptsPicture(5, List(25) { if (it % 5 in 1..3) 1 else 0 }))
        assertFalse(EndlessNonogramGenerator.acceptsPicture(5, List(25) { if ((it / 5 + it % 5) % 2 == 0) 1 else 0 }))
        assertFalse(EndlessNonogramGenerator.acceptsPicture(5, listOf(1)))
        val picture = listOf("00100", "01110", "11111", "01110", "00100").joinToString("").map(Char::digitToInt)
        assertTrue(EndlessNonogramGenerator.acceptsPicture(5, picture))
        val asymmetric = picture.toMutableList().also { it[1] = 1; it[5] = 1 }
        assertFalse(EndlessNonogramGenerator.acceptsPicture(5, asymmetric))
    }

    @Test fun solverBudgetsAndGeneratorTimeoutsNeverReturnAnUnverifiedPuzzle() {
        assertEquals(NonogramSolveStatus.LIMIT_REACHED, solve(List(5) { "11111" }, 0).status)
        assertEquals(NonogramSolveStatus.CANCELLED,
            NonogramLineSolver.solve(List(5) { listOf(1) }, List(5) { listOf(1) }, stopRequested = { true }).status)
        val zero = EndlessNonogramGenerator.generate(7, 10, NonogramDifficulty.HARD,
            limits = EndlessGenerationLimits(maxAttempts = 0))
        assertEquals(EndlessGenerationFailure.EXHAUSTED, (zero as EndlessGenerationResult.Failed).reason)
        val timedOut = EndlessNonogramGenerator.generate(7, 10, NonogramDifficulty.HARD, expired = { true })
        assertEquals(EndlessGenerationFailure.TIMEOUT, (timedOut as EndlessGenerationResult.Failed).reason)
        var checks = 0
        val interrupted = EndlessNonogramGenerator.generate(7, 10, NonogramDifficulty.HARD, expired = { ++checks > 20 })
        assertEquals(EndlessGenerationFailure.TIMEOUT, (interrupted as EndlessGenerationResult.Failed).reason)
        val cancelled = EndlessNonogramGenerator.generate(7, 5, NonogramDifficulty.EASY, stopRequested = { true })
        assertEquals(EndlessGenerationFailure.CANCELLED, (cancelled as EndlessGenerationResult.Failed).reason)
        assertEquals(EndlessNonogramGenerator.generate(7, 5, NonogramDifficulty.EASY),
            EndlessNonogramGenerator.generate(7, 5, NonogramDifficulty.EASY))
    }

    @Test fun seededMotifVariationsProduceDifferentPictures() {
        val pictures = (0L..15L).map { seed ->
            (EndlessNonogramGenerator.generate(seed, 5, NonogramDifficulty.MEDIUM) as EndlessGenerationResult.Ready).puzzle.solution
        }
        assertTrue("Only ${pictures.distinct().size} different pictures", pictures.distinct().size >= 8)
    }

    @Test fun versionOneGoldenPicturesStayFrozen() {
        val pictures = listOf(
            Triple(5, NonogramDifficulty.EASY, "0111000100111111111110101"),
            Triple(5, NonogramDifficulty.MEDIUM, "0111000010100110001001110"),
            Triple(5, NonogramDifficulty.HARD, "0111000100001001111110001"),
            Triple(10, NonogramDifficulty.EASY, "0000000000100001110010001111011111111101110111101111011110111111111101100011110110000111000000000000"),
            Triple(10, NonogramDifficulty.MEDIUM, "0000000000101011000110111111001011111110111111111111111111111011111110101111110010101100010000000000"),
            Triple(10, NonogramDifficulty.HARD, "0001100000011111000011111100101111111010011111111101111111111111111010111111001001111100000001100000"),
        )
        pictures.forEach { (size, difficulty, expected) ->
            val puzzle = (EndlessNonogramGenerator.generate(0, size, difficulty, 1) as EndlessGenerationResult.Ready).puzzle
            assertEquals(expected, puzzle.solution.joinToString(""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EndlessNonogramGenerator.generate(0, 5, NonogramDifficulty.EASY, generatorVersion = 2)
        }
    }

    @Test fun aBudgetMayRejectButMustNeverSelectADifferentPuzzleForTheSameSeed() {
        val normal = EndlessNonogramGenerator.generate(18, 10, NonogramDifficulty.HARD)
        assertTrue(normal is EndlessGenerationResult.Ready)
        for (budget in listOf(0, 1, 10, 100, 500, 1_000, 50_000)) {
            val bounded = EndlessNonogramGenerator.generate(18, 10, NonogramDifficulty.HARD,
                limits = EndlessGenerationLimits(maxSolverWork = budget))
            if (bounded is EndlessGenerationResult.Ready) assertEquals(normal, bounded)
            else assertEquals(EndlessGenerationFailure.EXHAUSTED, (bounded as EndlessGenerationResult.Failed).reason)
        }
    }

    @Test fun signedSeedExtremesKeepTheSameVerifiedPicture() {
        for (seed in listOf(Long.MIN_VALUE, Long.MAX_VALUE, -1L)) for (size in listOf(5, 10)) {
            val result = EndlessNonogramGenerator.generate(seed, size, NonogramDifficulty.MEDIUM)
            assertTrue(result is EndlessGenerationResult.Ready)
            assertEquals(result, EndlessNonogramGenerator.generate(seed, size, NonogramDifficulty.MEDIUM))
            val puzzle = (result as EndlessGenerationResult.Ready).puzzle
            assertEquals(seed, puzzle.endless!!.seed)
            assertEquals(puzzle.solution, NonogramLineSolver.solve(puzzle.rows, puzzle.columns).cells)
        }
    }
}
