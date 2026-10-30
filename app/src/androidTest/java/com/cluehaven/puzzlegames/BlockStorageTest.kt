package com.cluehaven.puzzlegames

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cluehaven.puzzlegames.game.blocks.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlockStorageTest {
    @Test fun saveRestoresUndoAndDeterministicTrayAfterRefill() {
        var game = BlockEngine.newGame(53).copy(tray = listOf("dot", "dot", "dot"))
        game = BlockEngine.place(game, 0, 0, 0)
        game = BlockEngine.place(game, 1, 0, 1)
        val dealt = BlockEngine.place(game, 2, 0, 2)
        val restored = BlockCodec.decode(BlockCodec.encode(dealt))!!
        assertEquals(dealt.copy(lastCleared = emptySet()), restored)
        val undo = BlockEngine.undo(restored)
        assertEquals(game.board, undo.board)
        assertEquals(game.tray, undo.tray)
        assertEquals(dealt.tray, BlockEngine.place(undo, 2, 0, 2).tray)
        assertEquals(undo, BlockCodec.decode(BlockCodec.encode(undo)))
    }

    @Test fun corruptOrUnsupportedSavesAreRejected() {
        val saved = BlockCodec.encode(BlockEngine.newGame(2))
        assertNull(BlockCodec.decode("not json"))
        assertNull(BlockCodec.decode(JSONObject(saved).put("version", 99).toString()))
        assertNull(BlockCodec.decode(JSONObject(saved).put("board", JSONArray(listOf(1, 2))).toString()))
        assertNull(BlockCodec.decode(JSONObject(saved).put("tray", JSONArray(listOf("unknown", "dot", "dot"))).toString()))
        assertNull(BlockCodec.decode(JSONObject(saved).put("score", -1).toString()))
    }
}
