package com.sza.fastmediasorter.widget.helpers

import android.content.Context

/**
 * S1411 phase 08 - the home-screen stopwatch widget's own measurement, keyed by `appWidgetId`.
 *
 * Read and written synchronously. An `AppWidgetProvider` is a broadcast receiver: it has no scope to
 * wait on, and its process may be torn down once `onReceive` returns.
 *
 * The cell's measurement is deliberately not the screen's. Strategic section 0 (addendum captured
 * 2026-09-03) asks the cell for a primitive start-stop, and a cell mirroring the screen would need the
 * screen's participant model to say which of one, two or four readings it is showing.
 *
 * Marks are `SystemClock.elapsedRealtime()` values (ADR-8), which is also the base
 * `RemoteViews.setChronometer` expects, so the stored mark reaches the system chronometer without a
 * conversion between two time bases.
 */
object StopwatchWidgetStateStore {

    /** The preferences file every widget in this package shares; see `StreamLaunchWidgetStore`. */
    private const val PREFS_NAME = "widget_prefs"

    private const val KEY_RUNNING_PREFIX = "stopwatch_running_"
    private const val KEY_START_MARK_PREFIX = "stopwatch_start_mark_"
    private const val KEY_ACCUMULATED_PREFIX = "stopwatch_accumulated_"

    /** One cell's measurement: whether it runs, when the current run began, what earlier runs added. */
    data class State(
        val running: Boolean = false,
        val startMark: Long = 0L,
        val accumulatedMillis: Long = 0L,
    )

    fun read(context: Context, appWidgetId: Int): State {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return State(
            running = prefs.getBoolean(KEY_RUNNING_PREFIX + appWidgetId, false),
            startMark = prefs.getLong(KEY_START_MARK_PREFIX + appWidgetId, 0L),
            accumulatedMillis = prefs.getLong(KEY_ACCUMULATED_PREFIX + appWidgetId, 0L),
        )
    }

    fun write(context: Context, appWidgetId: Int, state: State) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_RUNNING_PREFIX + appWidgetId, state.running)
            .putLong(KEY_START_MARK_PREFIX + appWidgetId, state.startMark)
            .putLong(KEY_ACCUMULATED_PREFIX + appWidgetId, state.accumulatedMillis)
            .apply()
    }

    fun delete(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_RUNNING_PREFIX + appWidgetId)
            .remove(KEY_START_MARK_PREFIX + appWidgetId)
            .remove(KEY_ACCUMULATED_PREFIX + appWidgetId)
            .apply()
    }

    /**
     * Flips the cell between running and stopped at [nowMillis].
     *
     * A reboot restarts the elapsed-realtime clock while the stored mark survives it, so a mark in the
     * future belongs to a start that no longer exists: that cell restarts from zero rather than
     * reporting a negative reading.
     */
    fun toggled(state: State, nowMillis: Long): State = when {
        state.startMark > nowMillis -> State()
        !state.running -> state.copy(running = true, startMark = nowMillis)
        else -> State(
            running = false,
            accumulatedMillis = state.accumulatedMillis + (nowMillis - state.startMark),
        )
    }

    /**
     * The state a cell holds after the elapsed-realtime clock restarted, which is what a reboot does.
     *
     * A run that was in flight has lost its start and cannot be measured any more, so it is dropped;
     * whatever earlier runs had already banked is a real duration and is kept.
     */
    fun afterClockRestart(state: State): State =
        if (state.running) State(accumulatedMillis = state.accumulatedMillis) else state

    /**
     * The `base` a `Chronometer` needs to display this measurement: the system draws
     * `elapsedRealtime() - base`, so a running cell counts up from its start and a stopped one holds at
     * what it has accumulated.
     */
    fun chronometerBase(state: State, nowMillis: Long): Long = when {
        state.running && state.startMark <= nowMillis -> state.startMark - state.accumulatedMillis
        state.running -> nowMillis
        else -> nowMillis - state.accumulatedMillis
    }
}
