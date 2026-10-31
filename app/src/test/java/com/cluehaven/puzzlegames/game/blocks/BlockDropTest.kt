package com.cluehaven.puzzlegames.game.blocks

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class BlockDropTest {
    private val bounds = Rect(35f, 120f, 435f, 520f)
    private val lift = 48f

    @Test fun pointerAndPreviewUseTheSameLiftAndBoardCoordinatesForEveryShape() {
        BlockEngine.shapes.forEach { shape ->
            for (row in 0..8 - shape.height) for (column in 0..8 - shape.width) {
                val pointer = Offset(bounds.left + (column + shape.width / 2f) * 50,
                    bounds.top + (row + shape.height / 2f) * 50 + lift)
                assertEquals(BlockCell(row, column), blockDropCell(shape, bounds, pointer, lift))
            }
        }
    }

    @Test fun offBoardDropsAndAnUnmeasuredBoardNeverProduceAPlacement() {
        val shape = BlockEngine.shape("dot")
        listOf(Offset(34f, 200f + lift), Offset(435f, 200f + lift),
            Offset(100f, 119f + lift), Offset(100f, 520f + lift)).forEach {
            assertNull(blockDropCell(shape, bounds, it, lift))
        }
        assertNull(blockDropCell(shape, Rect.Zero, Offset.Zero, lift))
    }

    @Test fun edgePreviewDoesNotClampAnOversizedPieceIntoAValidDrop() {
        val shape = BlockEngine.shape("square3")
        val cell = blockDropCell(shape, bounds, Offset(60f, 145f + lift), lift)!!
        assertEquals(BlockCell(-1, -1), cell)
        assertFalse(BlockEngine.canPlace(List(64) { 0 }, shape, cell.row, cell.column))
    }
}
