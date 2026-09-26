package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchState
import kotlinx.coroutines.flow.StateFlow

/**
 * S3555: the one owner of the stopwatch measurement, for the whole process.
 *
 * The screen, the ongoing-activity notification and the programs tile all read this, because the last
 * two outlive the screen - a measurement held by the screen would make them open a reset stopwatch.
 */
interface WearStopwatchSessionRepository {

    /** The measurement; before the stored one is restored it holds a clear stopwatch. */
    val session: StateFlow<WearStopwatchState>

    /** The measurement once the stored one has been restored. */
    suspend fun current(): WearStopwatchState

    /**
     * Applies [transform] at [nowMillis] (the elapsedRealtime of the tap), stores the result and answers
     * the state before and after it, so a caller can tell whether anything started or stopped running.
     */
    suspend fun update(
        nowMillis: Long,
        transform: (WearStopwatchState, Long) -> WearStopwatchState
    ): Pair<WearStopwatchState, WearStopwatchState>
}
