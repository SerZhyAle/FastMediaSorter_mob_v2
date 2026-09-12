package com.sza.fastmediasorter.domain.model.stopwatch

/**
 * The four values the stopwatch tool reads out of the shared settings (S1411 ADR-7).
 *
 * A narrow projection rather than the whole `AppSettings`: the screen reacts to these four and to
 * nothing else, which is what lets its observer discard the other couple of hundred fields before they
 * reach a collector.
 */
data class StopwatchSettings(
    val participantCount: Int,
    val musicEnabled: Boolean,
    val musicUri: String,
    val volumeKeysDriveMeasurement: Boolean,
)
