package com.sza.fastmediasorter.wear.domain.stopwatch

/**
 * Every state change of the watch stopwatch, as pure functions over [WearStopwatchState].
 *
 * The clock is always a parameter and never read here, so a test names the instant instead of waiting
 * for it, and the screen stays the only place that knows what time it is.
 *
 * S2825: this repeats the phone's engine rather than reusing it - the watch module depends on no shared
 * module and cannot see `app_v2` at all, the same reason the watch calculator carries its own arithmetic.
 */
object WearStopwatchEngine {

    /** A stopped participant starts; a running one records a lap. One button, both meanings. */
    fun startOrLap(state: WearStopwatchState, index: Int, nowMillis: Long): WearStopwatchState =
        state.mapParticipant(index) { participant ->
            if (participant.isRunning) {
                participant.withLap(nowMillis)
            } else {
                participant.started(nowMillis)
            }
        }

    /** A running participant stops; a stopped one that holds anything resets. */
    fun stopOrReset(state: WearStopwatchState, index: Int, nowMillis: Long): WearStopwatchState =
        state.mapParticipant(index) { participant ->
            if (participant.isRunning) {
                participant.stopped(nowMillis)
            } else {
                participant.reset()
            }
        }

    /** Already-running participants are left alone, so this cannot restart a measurement in progress. */
    fun startAll(state: WearStopwatchState, nowMillis: Long): WearStopwatchState =
        state.mapAll { participant ->
            if (participant.isRunning) participant else participant.started(nowMillis)
        }

    fun stopAll(state: WearStopwatchState, nowMillis: Long): WearStopwatchState =
        state.mapAll { participant -> participant.stopped(nowMillis) }

    fun resetAll(state: WearStopwatchState): WearStopwatchState =
        state.mapAll { participant -> participant.reset() }

    /**
     * Grows with fresh participants and shrinks from the tail.
     *
     * Shrinking drops the last regions rather than the first because the user counts regions from the
     * top: after a change from four to two, the two that remain are the two that were on top.
     */
    fun resize(state: WearStopwatchState, count: Int): WearStopwatchState {
        val target = WearStopwatchState.snapCount(count)
        if (state.participants.size == target) return state
        val kept = state.participants.take(target)
        val added = (kept.size until target).map { WearStopwatchParticipant(index = it) }
        return WearStopwatchState(kept + added)
    }
}

private fun WearStopwatchParticipant.started(nowMillis: Long): WearStopwatchParticipant =
    copy(startedAtMillis = nowMillis)

private fun WearStopwatchParticipant.stopped(nowMillis: Long): WearStopwatchParticipant {
    val started = startedAtMillis ?: return this
    val accumulated = (accumulatedMillis + (nowMillis - started)).coerceAtLeast(0L)
    return copy(startedAtMillis = null, accumulatedMillis = accumulated)
}

private fun WearStopwatchParticipant.withLap(nowMillis: Long): WearStopwatchParticipant {
    val total = elapsedAt(nowMillis)
    val previousTotal = laps.lastOrNull()?.totalMillis ?: 0L
    val lap = WearStopwatchLap(
        index = laps.size + 1,
        totalMillis = total,
        splitMillis = (total - previousTotal).coerceAtLeast(0L)
    )
    return copy(laps = laps + lap)
}

private fun WearStopwatchParticipant.reset(): WearStopwatchParticipant =
    WearStopwatchParticipant(index = index)

private fun WearStopwatchState.mapParticipant(
    index: Int,
    transform: (WearStopwatchParticipant) -> WearStopwatchParticipant
): WearStopwatchState = WearStopwatchState(
    participants.map { participant ->
        if (participant.index == index) transform(participant) else participant
    }
)

private fun WearStopwatchState.mapAll(
    transform: (WearStopwatchParticipant) -> WearStopwatchParticipant
): WearStopwatchState = WearStopwatchState(participants.map(transform))
