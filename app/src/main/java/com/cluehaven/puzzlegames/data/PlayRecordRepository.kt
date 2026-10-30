package com.cluehaven.puzzlegames.data

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap
import com.cluehaven.puzzlegames.game.nonogram.*
import com.cluehaven.puzzlegames.game.pipes.*
import com.cluehaven.puzzlegames.game.tilemerge.*
import com.cluehaven.puzzlegames.game.sudoku.SudokuStorage
import java.time.LocalDate

class PlayRecordRepository private constructor(context: Context) {
    private val dao = CluehavenDatabase.get(context).playRecords()
    private val latest = ConcurrentHashMap<String, PlayRecord>()
    private val writes = Channel<PlayRecord>(Channel.UNLIMITED)
    private val ready = CompletableDeferred<Unit>()
    val history = dao.observeHistory()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // Import surviving old completed boards once, with no invented elapsed time.
            for (session in CluehavenDatabase.get(context).puzzleSessions().all()) {
                val old = runCatching {
                    when {
                        session.gameId.startsWith("sudoku_") -> SudokuStorage.get(context)
                            .load(session.gameId)?.takeIf { it.isComplete }?.playRecord()
                        session.gameId.startsWith("nonogram_") -> {
                            val id = session.gameId.removePrefix("nonogram_")
                            val puzzle = if (id.startsWith("daily-")) NonogramEngine.daily(LocalDate.parse(id.removePrefix("daily-")))
                                else NonogramEngine.puzzles.first { it.id == id }
                            NonogramStorage.get(context).load(puzzle).takeIf { it.isComplete }?.playRecord()
                        }
                        session.gameId.startsWith("pipes_") -> PipeStorage.get(context)
                            .load(session.gameId.removePrefix("pipes_").toInt()).takeIf { it.isComplete }?.playRecord()
                        session.gameId == "tile_merge" -> TileMergeStorage.get(context).load().takeIf { it.isGameOver }?.playRecord()
                        else -> null
                    }
                }.getOrNull()
                if (old != null && dao.get(old.id) == null) dao.save(old.copy(timeKnown = false, completedAt = session.updatedAt))
            }
            ready.complete(Unit)
            for (record in writes) dao.save(record)
        }
    }

    suspend fun load(initial: PlayRecord): PlayRecord {
        ready.await()
        latest[initial.id]?.let { return it }
        val saved = dao.get(initial.id) ?: initial
        return latest.putIfAbsent(initial.id, saved) ?: saved
    }

    fun save(record: PlayRecord) {
        latest[record.id] = record
        check(writes.trySend(record).isSuccess)
    }

    companion object {
        @Volatile private var instance: PlayRecordRepository? = null
        fun get(context: Context): PlayRecordRepository = instance ?: synchronized(this) {
            instance ?: PlayRecordRepository(context.applicationContext).also { instance = it }
        }
    }
}
