package com.cluehaven.puzzlegames.game.blocks

import android.content.Context
import com.cluehaven.puzzlegames.data.CluehavenDatabase
import com.cluehaven.puzzlegames.data.PuzzleSession
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import org.json.JSONObject

internal object BlockCodec {
    fun encode(state: BlockState): String = JSONObject().apply {
        put("version", 1); put("runId", state.runId)
        put("board", JSONArray(state.board)); put("tray", JSONArray(state.tray.map { it ?: JSONObject.NULL }))
        put("rngState", state.rngState); put("score", state.score); put("bestScore", state.bestScore)
        put("combo", state.combo); put("lines", state.lines); put("moves", state.moves); put("undoUsed", state.undoUsed)
        state.previous?.let { old -> put("previous", JSONObject().apply {
            put("board", JSONArray(old.board)); put("tray", JSONArray(old.tray.map { it ?: JSONObject.NULL }))
            put("score", old.score); put("combo", old.combo); put("lines", old.lines)
            put("moves", old.moves); put("rngState", old.rngState)
        }) }
    }.toString()

    fun decode(payload: String): BlockState? = runCatching {
        val json = JSONObject(payload)
        require(json.getInt("version") == 1)
        fun board(root: JSONObject): List<Int> = root.getJSONArray("board").let { array ->
            require(array.length() == 64)
            List(64) { array.getInt(it).also { value -> require(value in 0..4) } }
        }
        fun tray(root: JSONObject): List<String?> = root.getJSONArray("tray").let { array ->
            require(array.length() == 3)
            List(3) { if (array.isNull(it)) null else array.getString(it).also { id -> BlockEngine.shape(id) } }
                .also { require(it.any { id -> id != null }) }
        }
        fun count(root: JSONObject, key: String) = root.getInt(key).also { require(it >= 0) }
        val old = json.optJSONObject("previous")?.let {
            BlockSnapshot(board(it), tray(it), count(it, "score"), count(it, "combo"), count(it, "lines"),
                count(it, "moves"), it.getLong("rngState"))
        }
        BlockState(json.getString("runId").also { require(it.isNotBlank()) }, board(json), tray(json),
            json.getLong("rngState"), count(json, "score"), count(json, "bestScore"), count(json, "combo"),
            count(json, "lines"), count(json, "moves"), json.getBoolean("undoUsed"), old).also {
            require(it.bestScore >= it.score && (!it.undoUsed || it.previous == null))
        }
    }.getOrNull()
}

class BlockStorage private constructor(context: Context) {
    private val sessions = CluehavenDatabase.get(context).puzzleSessions()
    private val writes = Channel<BlockState>(Channel.UNLIMITED)
    @Volatile private var latest: BlockState? = null
    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (state in writes) sessions.save(PuzzleSession("block_garden", BlockCodec.encode(state), System.currentTimeMillis()))
        }
    }
    suspend fun saved(): BlockState? {
        latest?.let { return it }
        return sessions.get("block_garden")?.payload?.let(BlockCodec::decode)?.also { latest = it }
    }
    suspend fun load(): BlockState {
        val restored = saved()
        val loaded = restored ?: BlockEngine.newGame()
        latest = loaded
        if (restored == null) save(loaded)
        return loaded
    }
    fun save(state: BlockState) { latest = state; check(writes.trySend(state).isSuccess) }
    companion object {
        @Volatile private var instance: BlockStorage? = null
        fun get(context: Context): BlockStorage = instance ?: synchronized(this) {
            instance ?: BlockStorage(context.applicationContext).also { instance = it }
        }
    }
}
