package com.cluehaven.puzzlegames.ui

import android.content.ContextWrapper
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import com.cluehaven.puzzlegames.data.*
import kotlinx.coroutines.delay
import com.cluehaven.puzzlegames.ui.daily.LocalDailyTimer

@Composable
fun PlayTimerLine(initial: PlayRecord, previouslyPlayed: Boolean, finished: Boolean, modifier: Modifier = Modifier,
    paused: Boolean = false, alignment: Arrangement.Horizontal = Arrangement.Center) {
    val daily = LocalDailyTimer.current
    if (daily != null) {
        CompositionLocalProvider(LocalDailyTimer provides null) {
            PlayTimerLine(initial.forDailyFive(daily.spec),
                previouslyPlayed, daily.complete, modifier, paused, alignment)
        }
        return
    }
    val context = LocalContext.current
    val repository = remember(context) { PlayRecordRepository.get(context) }
    val owner = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<LifecycleOwner>().firstOrNull()
    }
    var tracker by remember(initial.id) { mutableStateOf<PlayTracker?>(null) }
    var elapsed by remember(initial.id) { mutableLongStateOf(0) }
    var foreground by remember(owner) { mutableStateOf(owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) }
    LaunchedEffect(initial.id) {
        tracker = PlayTracker(repository.load(initial.copy(timeKnown = !previouslyPlayed && !finished)),
            { SystemClock.elapsedRealtime() }, repository::save)
    }
    SideEffect {
        tracker?.update(initial, finished, foreground && !paused)
    }
    LaunchedEffect(tracker, finished) { elapsed = tracker?.elapsed() ?: 0 }
    DisposableEffect(tracker, owner) {
        val current = tracker
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> foreground = true
                Lifecycle.Event.ON_PAUSE -> { foreground = false; current?.pause() }
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer); current?.pause() }
    }
    LaunchedEffect(tracker) {
        val current = tracker ?: return@LaunchedEffect
        var ticks = 0
        while (true) {
            delay(1000)
            elapsed = current.elapsed()
            if (++ticks % 5 == 0) current.checkpoint()
        }
    }
    val record = tracker?.record
    val label = when {
        record == null -> "Time · …"
        !record.timeKnown && finished -> "Time unavailable · older save"
        !record.timeKnown -> "Tracked · ${formatPlayTime(elapsed)}"
        finished -> "Finished · ${formatPlayTime(elapsed)}"
        !foreground || paused -> "Paused · ${formatPlayTime(elapsed)}"
        else -> "Time · ${formatPlayTime(elapsed)}"
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = alignment, verticalAlignment = Alignment.CenterVertically) {
        Text(label + if (initial.hintsUsed > 0) " · Assisted" else "",
            Modifier.padding(vertical = 2.dp).semantics { contentDescription = label },
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal class PlayTracker(
    var record: PlayRecord,
    now: () -> Long,
    private val save: (PlayRecord) -> Unit,
    private val wallTime: () -> Long = { System.currentTimeMillis() },
) {
    private val clock = ActivePlayClock(record.elapsedMillis, now)
    fun elapsed() = clock.elapsed()
    fun update(metadata: PlayRecord, finished: Boolean, running: Boolean) {
        if (finished || !running) clock.pause() else clock.resume()
        val next = record.copy(title = metadata.title, hintsUsed = metadata.hintsUsed, score = metadata.score,
            completedAt = if (finished) record.completedAt ?: wallTime() else null)
        if (next != record) { record = next; checkpoint() }
    }
    fun checkpoint() { record = record.copy(elapsedMillis = elapsed()); save(record) }
    fun pause() { clock.pause(); checkpoint() }
}
