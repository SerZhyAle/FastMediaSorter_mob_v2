package com.sza.fastmediasorter.data.stats

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks foreground session duration (S0473, group F).
 *
 * Registers a [DefaultLifecycleObserver] on [ProcessLifecycleOwner]: a monotonic start timestamp is
 * captured on `onStart` and, on `onStop`, the elapsed active millis are emitted as a
 * [StatsEvent.Session] (the sink no-ops when collection is disabled) and a [StatsSink.flushNow] is
 * triggered so the in-memory batch is persisted once when the app leaves the foreground (strategic
 * §3.2 / ADR-6). A monotonic clock is used, not wall-clock, so a clock change cannot skew the figure.
 */
@Singleton
class StatsSessionTracker @Inject constructor(
    private val statsSink: StatsSink,
    @ApplicationScope private val scope: CoroutineScope
) : DefaultLifecycleObserver {

    private val initialized = AtomicBoolean(false)
    private var foregroundStartMs: Long = 0L

    /** Registers the process-lifecycle observer once. Must be called on the main thread. */
    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        Timber.i("StatsSessionTracker: attached to ProcessLifecycleOwner")
    }

    override fun onStart(owner: LifecycleOwner) {
        foregroundStartMs = SystemClock.elapsedRealtime()
    }

    override fun onStop(owner: LifecycleOwner) {
        // foregroundStartMs is 0 only if onStop somehow precedes the first onStart; guard against a
        // bogus elapsed value equal to the full uptime.
        if (foregroundStartMs == 0L) return
        val elapsed = (SystemClock.elapsedRealtime() - foregroundStartMs).coerceAtLeast(0L)
        foregroundStartMs = 0L
        statsSink.record(StatsEvent.Session(activeMs = elapsed))
        scope.launch { statsSink.flushNow() }
    }
}
