package com.cluehaven.puzzlegames

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cluehaven.puzzlegames.game.blocks.*
import com.cluehaven.puzzlegames.ui.LocalReducedMotion
import com.cluehaven.puzzlegames.ui.theme.CluehavenTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BlockPlacementUiTest {
    @get:Rule val compose = createComposeRule()
    private val interaction = BlockInteraction()
    private var game by mutableStateOf(BlockState("drag-fixture", tray = listOf("dot", "h2", "square2"), rngState = 1))
    private var selected by mutableIntStateOf(-1)
    private var screenCompositions = 0
    private var drops = 0
    private var reducedMotion by mutableStateOf(false)
    private var clear by mutableStateOf<BlockClearEffect?>(null)
    private var rejection by mutableStateOf<BlockRejection?>(null)
    private fun capture(name: String): Bitmap {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "block-motion").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
    private fun show(reduced: Boolean = false) {
        reducedMotion = reduced
        compose.setContent {
            SideEffect { screenCompositions++ }
            CluehavenTheme(darkTheme = true) {
                CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize().onGloballyPositioned { interaction.rootOrigin = it.boundsInRoot().topLeft }) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                BlockBoard(game, interaction, clear, rejection, Modifier.fillMaxWidth()) { row, column ->
                                    if (selected >= 0) game = BlockEngine.place(game, selected, row, column)
                                }
                                BlockTray(game, selected, interaction, { selected = it }) { slot, cell ->
                                    drops++
                                    game = BlockEngine.place(game, slot, cell.row, cell.column)
                                }
                            }
                            BlockDragOverlay(game, interaction, Modifier.matchParentSize())
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }
    private fun target(row: Int, column: Int): Offset {
        val unit = interaction.boardBounds.width / 8
        // Use measured slot height so the gesture works at any display density.
        val density = compose.onNodeWithContentDescription("Block 1, 1 by 1, 1 squares", substring = true)
            .fetchSemanticsNode().boundsInRoot.height / 100f
        return interaction.boardBounds.topLeft + Offset((column + .5f) * unit, (row + .5f) * unit + 48 * density)
    }
    private fun beginDrag(destination: Offset) {
        val tray = compose.onNodeWithContentDescription("Block 1, 1 by 1, 1 squares", substring = true)
            .fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(tray); moveTo(destination, delayMillis = 250) }
        compose.waitForIdle()
    }

    @Test fun movingAcrossCellsDoesNotRecomposeTheScreenOrCommitUntilRelease() {
        show()
        val destination = target(2, 3)
        beginDrag(destination)
        capture("continuous-drag")
        val count = screenCompositions
        compose.onRoot().performTouchInput {
            repeat(30) { index -> moveTo(destination + Offset(index.toFloat(), index / 2f), delayMillis = 16) }
        }
        compose.runOnIdle {
            assertEquals(count, screenCompositions)
            assertEquals(0, drops)
            assertEquals(0, game.moves)
            assertNotNull(interaction.drag)
        }
        compose.onRoot().performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(1, drops)
            assertEquals(1, game.moves)
            assertEquals(1, game.board.count { it != 0 })
            assertNull(interaction.drag)
        }
    }

    @Test fun occupiedAndOutsideDropsDoNotChangeTheGameAndCancelCleansUp() {
        game = game.copy(board = List(64) { if (it == 19) 2 else 0 })
        show()
        val before = game
        beginDrag(target(2, 3))
        capture("blocked-drag")
        compose.onRoot().performTouchInput { up() }
        compose.runOnIdle { assertEquals(before, game); assertNull(interaction.drag) }
        beginDrag(Offset(5f, 5f))
        compose.onRoot().performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, drops); assertEquals(before, game) }
        beginDrag(target(3, 3))
        compose.onRoot().performTouchInput { cancel() }
        compose.runOnIdle { assertNull(interaction.drag); assertEquals(before, game); assertEquals(1, drops) }
    }

    @Test fun reducedMotionStillSupportsDragAndTapToPlace() {
        show(reduced = true)
        beginDrag(target(0, 0))
        compose.onRoot().performTouchInput { up() }
        compose.onNodeWithContentDescription("Block 2, 2 by 1, 2 squares").performClick()
        compose.onNodeWithContentDescription("Row 5, column 3, empty").performClick()
        compose.runOnIdle {
            assertEquals(2, game.moves)
            assertEquals(3, game.board.count { it != 0 })
        }
    }

    @Test fun clearAndRejectedDropEffectsFinishAndReducedMotionSkipsTheClearAnimation() {
        show()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { clear = BlockClearEffect(1, (0..7).associateWith { 3 }) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        val during = capture("clear-during")
        compose.mainClock.advanceTimeBy(400)
        val after = capture("clear-after")
        val unit = interaction.boardBounds.width / 8
        val x = (interaction.boardBounds.left + unit / 2).toInt()
        val y = (interaction.boardBounds.top + unit / 2).toInt()
        assertNotEquals(during.getPixel(x, y), after.getPixel(x, y))
        compose.runOnIdle {
            reducedMotion = true
            clear = BlockClearEffect(2, (0..7).associateWith { 3 })
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val reduced = capture("clear-reduced-motion")
        assertEquals(after.getPixel(x, y), reduced.getPixel(x, y))
        compose.runOnIdle {
            reducedMotion = false
            rejection = BlockRejection(1, BlockEngine.shape("h2"), BlockCell(2, 2))
        }
        compose.mainClock.advanceTimeByFrame()
        val rejected = capture("rejected-drop")
        // Turning animations back on must not replay the previous line clear.
        assertEquals(after.getPixel(x, y), rejected.getPixel(x, y))
        compose.mainClock.advanceTimeBy(450)
        val finished = capture("rejected-drop-finished")
        val errorX = (interaction.boardBounds.left + unit * 2.5f).toInt()
        val errorY = (interaction.boardBounds.top + unit * 2.5f).toInt()
        assertNotEquals(rejected.getPixel(errorX, errorY), finished.getPixel(errorX, errorY))
    }
}
