package com.cluehaven.puzzlegames.game.nonogram

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cluehaven.puzzlegames.data.PlayRecordRepository
import com.cluehaven.puzzlegames.data.endlessNonogramStats
import com.cluehaven.puzzlegames.ui.GameHeader
import com.cluehaven.puzzlegames.ui.LocalGameFeedback
import com.cluehaven.puzzlegames.ui.LocalPlayRecords
import kotlinx.coroutines.*

/** Cancellation/timeout aborts the whole request; it never selects a later candidate. */
internal suspend fun prepareEndless(seed: Long, size: Int, difficulty: NonogramDifficulty): EndlessGenerationResult {
    val context = currentCoroutineContext()
    return withContext(Dispatchers.Default) {
        val deadline = System.nanoTime() + 4_000_000_000L
        EndlessNonogramGenerator.generate(seed, size, difficulty,
            stopRequested = { !context.isActive }, expired = { System.nanoTime() >= deadline })
    }.also { context.ensureActive() }
}

@Composable
internal fun EndlessNonogramScreen(padding: PaddingValues, initialSlot: String? = null,
    storageOverride: NonogramStorage? = null,
    prepare: suspend (Long, Int, NonogramDifficulty) -> EndlessGenerationResult = ::prepareEndless,
    back: () -> Unit) {
    val context = LocalContext.current
    val storage = storageOverride ?: remember(context) { NonogramStorage.get(context) }
    val records = LocalPlayRecords.current ?: remember(context) { PlayRecordRepository.get(context) }
    val history by records.history.collectAsState(initial = emptyList())
    val stats = remember(history) { endlessNonogramStats(history) }
    val scope = rememberCoroutineScope()
    val feedback = LocalGameFeedback.current
    var size by rememberSaveable { mutableIntStateOf(initialSlot?.split('-')?.getOrNull(1)?.toIntOrNull()?.takeIf { it in setOf(5, 10) } ?: 5) }
    var difficulty by rememberSaveable { mutableStateOf(runCatching {
        NonogramDifficulty.valueOf(initialSlot!!.substringAfterLast('-'))
    }.getOrDefault(NonogramDifficulty.EASY)) }
    var playing by rememberSaveable { mutableStateOf(initialSlot != null) }
    var state by remember { mutableStateOf<NonogramState?>(null) }
    var resumable by remember { mutableStateOf<NonogramState?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadedSlot by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableStateOf<Job?>(null) }
    var generationToken by remember { mutableIntStateOf(0) }
    var cross by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    var next by remember { mutableStateOf<NonogramPuzzle?>(null) }
    var nextError by remember { mutableStateOf(false) }
    var waitingNext by remember { mutableStateOf(false) }
    var nextRequest by remember { mutableIntStateOf(0) }
    val slot = NonogramStorage.endlessSlot(size, difficulty)

    LaunchedEffect(size, difficulty) {
        loading = true
        loadedSlot = null
        val saved = storage.loadEndless(size, difficulty)
        resumable = saved?.takeUnless { it.isComplete }
        if (playing && state == null) {
            state = saved
            if (state == null) { playing = false; error = "That saved picture is unavailable. You can start a new one." }
        }
        loading = false
        loadedSlot = slot
    }
    fun goBack() {
        generationToken++
        generation?.cancel()
        busy = false
        waitingNext = false
        if (playing) { playing = false; state = null; next = null; nextError = false }
        else back()
    }
    BackHandler(onBack = ::goBack)

    fun display(saved: NonogramState) {
        state = saved
        resumable = saved
        playing = true
        cross = false
        selected = -1
        error = null
        waitingNext = false
    }
    fun start() {
        if (busy || loading || loadedSlot != slot) return
        resumable?.takeUnless { it.isComplete }?.let { display(it); return }
        busy = true
        error = null
        val requestedSize = size
        val requestedDifficulty = difficulty
        val token = ++generationToken
        generation = scope.launch {
            try {
                when (val generated = prepare(System.nanoTime(), requestedSize, requestedDifficulty)) {
                    is EndlessGenerationResult.Ready -> display(storage.beginEndless(generated.puzzle))
                    is EndlessGenerationResult.Failed -> error = "Couldn't prepare that picture in time. Try another."
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Couldn't save your new picture. Please try again." }
            finally { if (generationToken == token) busy = false }
        }
    }

    val current = state
    // Prefetch only one board, with no database writes or history entries until Next.
    LaunchedEffect(playing, current?.puzzle?.id, nextRequest) {
        next = null
        nextError = false
        if (!playing || current == null) return@LaunchedEffect
        val picture = current.puzzle
        var prepared: NonogramPuzzle? = null
        repeat(4) {
            if (prepared == null) {
                val generated = prepare(System.nanoTime(), picture.size, picture.difficulty)
                if (generated is EndlessGenerationResult.Ready && generated.puzzle.solution != picture.solution)
                    prepared = generated.puzzle
            }
        }
        next = prepared
        nextError = prepared == null
    }
    LaunchedEffect(waitingNext, next, nextError) {
        val prepared = next
        if (waitingNext && prepared != null && current?.isComplete == true) {
            try { display(storage.beginEndless(prepared)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { waitingNext = false; error = "Couldn't save the next picture. Please try again." }
        } else if (waitingNext && nextError) {
            waitingNext = false
            error = "Couldn't prepare the next picture. Tap Next puzzle to try another."
        }
    }

    if (playing) {
        if (current == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                NonogramPlay(current, padding, cross, selected, back = ::goBack, selectMode = { cross = it },
                    mark = { index ->
                        selected = index
                        val changed = NonogramEngine.mark(current, index, cross)
                        if (changed != current) { feedback(); state = changed; resumable = changed.takeUnless { it.isComplete }; storage.save(changed) }
                    }, update = { changed -> feedback(); state = changed; resumable = changed.takeUnless { it.isComplete }; storage.save(changed) },
                    nextPuzzle = {
                        if (!waitingNext) {
                            error = null
                            if (nextError) { nextError = false; nextRequest++ }
                            waitingNext = true
                        }
                    }, preparingNext = waitingNext)
            }
            error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GameHeader("Endless Nonogram", ::goBack)
        Text("A little picture, a fresh start", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Every picture can be solved using the row and column clues, without guessing.", style = MaterialTheme.typography.bodyMedium)
        Text("Board size", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(5, 10).forEach { choice -> FilterChip(selected = size == choice, enabled = !busy,
                onClick = { size = choice; error = null }, label = { Text("$choice×$choice") }) }
        }
        Text("Difficulty", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NonogramDifficulty.entries.forEach { choice -> FilterChip(selected = difficulty == choice, enabled = !busy,
                onClick = { difficulty = choice; error = null }, label = { Text(choice.label) }) }
        }
        Button(onClick = ::start, enabled = !busy && !loading && loadedSlot == slot, modifier = Modifier.fillMaxWidth()) {
            Text(when { busy -> "Preparing picture…"; loading || loadedSlot != slot -> "Loading…"; resumable != null -> "Resume"; else -> "Play" })
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Card {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your Endless progress", fontWeight = FontWeight.Bold)
                Text("${stats.solved} pictures solved", color = MaterialTheme.colorScheme.primary)
                Text("5×5: ${stats.smallSolved} · 10×10: ${stats.largeSolved}", style = MaterialTheme.typography.bodySmall)
                Text("Each size and difficulty keeps its own unfinished picture.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
