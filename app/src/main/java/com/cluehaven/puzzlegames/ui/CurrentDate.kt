package com.cluehaven.puzzlegames.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

internal fun localDates(
    now: () -> ZonedDateTime = { ZonedDateTime.now() },
    wait: suspend (Long) -> Unit = { delay(it) },
) = flow {
    while (currentCoroutineContext().isActive) {
        val time = now()
        emit(time.toLocalDate())
        val midnight = time.toLocalDate().plusDays(1).atStartOfDay(time.zone)
        // Recheck periodically for clock changes, and wake precisely at midnight.
        wait(Duration.between(time, midnight).toMillis().coerceIn(1L, 30_000L))
    }
}.distinctUntilChanged()

@Composable
fun rememberCurrentDate(): LocalDate {
    val context = LocalContext.current
    var date by remember { mutableStateOf(LocalDate.now()) }
    var refresh by remember { mutableIntStateOf(0) }
    val owner = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<LifecycleOwner>().firstOrNull()
    }
    DisposableEffect(context, owner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                date = LocalDate.now()
                refresh++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { date = LocalDate.now(); refresh++ }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose {
            context.unregisterReceiver(receiver)
            owner?.lifecycle?.removeObserver(observer)
        }
    }
    LaunchedEffect(refresh) { localDates().collect { date = it } }
    return date
}
