package com.sza.fastmediasorter.wear.domain.stopwatch

/** S3555: the measurement after a change, and whether the missing notification permission hides its indicator. */
data class WearStopwatchUpdate(
    val state: WearStopwatchState,
    val indicatorBlocked: Boolean
)
