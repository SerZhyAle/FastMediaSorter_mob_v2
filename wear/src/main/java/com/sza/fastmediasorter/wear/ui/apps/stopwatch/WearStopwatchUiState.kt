package com.sza.fastmediasorter.wear.ui.apps.stopwatch

import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchState

/**
 * What the stopwatch screen draws.
 *
 * [nowMillis] is the repaint clock rather than a formatted reading: one ticking value drives every
 * region, and the formatting happens where the text is drawn, so the view model is not rebuilding four
 * strings twenty times a second.
 */
data class WearStopwatchUiState(
    val state: WearStopwatchState = WearStopwatchState.initial(WearStopwatchState.DEFAULT_COUNT),
    val nowMillis: Long = 0L,
    val lastResult: String? = null
) {

    val participantCount: Int get() = state.participants.size

    val anyRunning: Boolean get() = state.anyRunning
}
