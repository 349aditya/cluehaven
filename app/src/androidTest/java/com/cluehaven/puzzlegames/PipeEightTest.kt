package com.cluehaven.puzzlegames

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cluehaven.puzzlegames.data.*
import com.cluehaven.puzzlegames.game.pipes.*
import com.cluehaven.puzzlegames.ui.LocalReducedMotion
import com.cluehaven.puzzlegames.ui.home.resumeEntry
import com.cluehaven.puzzlegames.ui.theme.CluehavenTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PipeEightTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun eightByEightSavesReopenWithoutChangingSmallerNetworks() = runBlocking {
        val name = "pipe-eight-review.db"
        context.deleteDatabase(name)
        val small = listOf(4, 6).map { PipeEngine.turn(PipeEngine.newGame(it, 71), 0) }
        val initial = PipeEngine.newGame(8, 91)
        val moved = PipeEngine.turn(PipeEngine.turn(initial, 0), 63)
        var db = Room.databaseBuilder(context, CluehavenDatabase::class.java, name).build()
        try {
            val rows = (small + moved).map { PuzzleSession("pipes_${it.size}", PipeCodec.encode(it), 100) }
            rows.forEach { db.puzzleSessions().save(it) }
            db.close()
            db = Room.databaseBuilder(context, CluehavenDatabase::class.java, name).build()
            rows.forEach { row -> assertEquals(row, db.puzzleSessions().get(row.gameId)) }
            val restored = PipeCodec.decode(8, db.puzzleSessions().get("pipes_8")!!.payload)!!
            assertEquals(moved, restored)
            assertEquals(initial.tiles, PipeEngine.undo(PipeEngine.undo(restored)).tiles)
            val hinted = PipeEngine.hint(restored)
            assertEquals(hinted, PipeCodec.decode(8, PipeCodec.encode(hinted)))
            assertEquals(1, hinted.hintsUsed)
            assertTrue(PipeCodec.decode(8, PipeCodec.encode(initial.copy(tiles = initial.solution)))!!.isComplete)
            assertEquals("pipes|8", resumeEntry(rows.last())?.route)
            assertEquals("8×8", restored.playRecord().category)
            assertNull(PipeCodec.decode(6, PipeCodec.encode(moved)))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun all64TilesAndActionsWorkInLightDarkAndReducedMotion() {
        val initial = PipeEngine.newGame(8, 182)
        var game by mutableStateOf(initial)
        var dark by mutableStateOf(false)
        var reduced by mutableStateOf(false)
        var newBoard = false
        compose.setContent {
            CluehavenTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalReducedMotion provides reduced) {
                    Surface {
                        PipePlay(game, PaddingValues(), false, {}, { game = it }, { newBoard = true })
                    }
                }
            }
        }
        compose.onAllNodes(hasContentDescription("ports ", substring = true)).assertCountEquals(64)
        compose.onNodeWithContentDescription("Row 8, column 8,", substring = true).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, game.moves) }
        compose.onNodeWithText("Undo").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(initial, game) }
        compose.onNodeWithText("Hint").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, game.hintsUsed) }
        capture("pipes-eight-light")
        compose.runOnIdle { dark = true }
        capture("pipes-eight-dark")
        compose.runOnIdle { reduced = true }
        capture("pipes-eight-reduced")
        compose.onNodeWithText("New").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(newBoard) }
    }

    private fun capture(name: String) {
        val folder = File(context.getExternalFilesDir(null), "pipe-eight").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

}
