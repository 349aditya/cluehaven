package com.cluehaven.puzzlegames.game.tilemerge

import android.content.Context
import com.cluehaven.puzzlegames.data.CluehavenDatabase
import com.cluehaven.puzzlegames.data.PuzzleSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class TileMergeStorage private constructor(context: Context) {
    private val legacyPreferences = context.applicationContext.getSharedPreferences(
        "tile_merge_progress", Context.MODE_PRIVATE,
    )
    private val sessions = CluehavenDatabase.get(context).puzzleSessions()
    private val saves = Channel<TileMergeState>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var latest: TileMergeState? = null

    init {
        scope.launch {
            for (state in saves) {
                sessions.save(PuzzleSession("tile_merge", TileMergeCodec.encode(state), System.currentTimeMillis()))
            }
        }
    }

    suspend fun load(): TileMergeState {
        latest?.let { return it }
        val existing = sessions.get("tile_merge")
        val saved = existing?.payload ?: legacyPreferences.getString("current_run", null)
        val loaded = TileMergeCodec.decode(saved) ?: TileMergeEngine.newGame()
        latest = loaded
        if (existing == null && saved != null) {
            sessions.save(PuzzleSession("tile_merge", TileMergeCodec.encode(loaded), System.currentTimeMillis()))
            legacyPreferences.edit().remove("current_run").apply()
        }
        return loaded
    }

    fun save(state: TileMergeState) {
        latest = state
        check(saves.trySend(state).isSuccess) { "Tile Merge save queue is unavailable" }
    }

    companion object {
        @Volatile private var instance: TileMergeStorage? = null

        fun get(context: Context): TileMergeStorage = instance ?: synchronized(this) {
            instance ?: TileMergeStorage(context.applicationContext).also { instance = it }
        }
    }
}
