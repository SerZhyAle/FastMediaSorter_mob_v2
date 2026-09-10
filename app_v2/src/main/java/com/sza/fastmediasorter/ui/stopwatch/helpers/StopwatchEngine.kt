package com.sza.fastmediasorter.ui.stopwatch.helpers

import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchLap
import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchParticipant
import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchScreenState

/**
 * Pure state transitions for the stopwatch.
 *
 * Every function takes the clock reading as an argument rather than reading a clock itself, which is
 * what makes the timing testable without waiting, and every one of them rewrites exactly the addressed
 * participant - the independence strategic §11.4 demands is a property of these transitions, not of the
 * layout that draws them.
 */
object StopwatchEngine {

    fun start(
        state: StopwatchScreenState,
        participantId: Int,
        nowMillis: Long,
        startedAtEpochMillis: Long,
    ): StopwatchScreenState =
        state.mapParticipant(participantId) { participant ->
            if (participant.running) {
                participant
            } else {
                participant.copy(
                    running = true,
                    startMark = nowMillis,
                    startedAtEpochMillis = startedAtEpochMillis,
                )
            }
        }

    fun stop(state: StopwatchScreenState, participantId: Int, nowMillis: Long): StopwatchScreenState =
        state.mapParticipant(participantId) { participant ->
            if (participant.running) {
                participant.copy(
                    running = false,
                    accumulatedMillis = participant.elapsedAt(nowMillis),
                    startMark = 0L,
                )
            } else {
                participant
            }
        }

    /** Records a split without interrupting the count; a stopped participant records nothing. */
    fun lap(state: StopwatchScreenState, participantId: Int, nowMillis: Long): StopwatchScreenState =
        state.mapParticipant(participantId) { participant ->
            if (participant.running) {
                val lap = StopwatchLap(
                    ordinal = participant.laps.size + 1,
                    atElapsedMillis = participant.elapsedAt(nowMillis),
                )
                participant.copy(laps = participant.laps + lap)
            } else {
                participant
            }
        }

    fun reset(state: StopwatchScreenState, participantId: Int): StopwatchScreenState =
        state.mapParticipant(participantId) { participant -> StopwatchParticipant(id = participant.id) }

    fun resetAll(state: StopwatchScreenState): StopwatchScreenState =
        state.copy(participants = state.participants.map { StopwatchParticipant(id = it.id) })

    /**
     * Starts every visible participant that is not running in one transition (S2792). A participant
     * already running keeps its original marks, so a repeated Start all never restarts a live clock.
     */
    fun startAll(
        state: StopwatchScreenState,
        nowMillis: Long,
        startedAtEpochMillis: Long,
    ): StopwatchScreenState = state.copy(
        participants = state.participants.mapIndexed { index, participant ->
            val visible = index < state.participantCount
            when {
                !visible || participant.running -> participant
                else -> participant.copy(
                    running = true,
                    startMark = nowMillis,
                    startedAtEpochMillis = startedAtEpochMillis,
                )
            }
        },
    )

    /** Stops every visible running participant in one transition (S2792); laps and hidden ones stay. */
    fun stopAll(state: StopwatchScreenState, nowMillis: Long): StopwatchScreenState = state.copy(
        participants = state.participants.mapIndexed { index, participant ->
            val visible = index < state.participantCount
            when {
                !visible || !participant.running -> participant
                else -> participant.copy(
                    running = false,
                    accumulatedMillis = participant.elapsedAt(nowMillis),
                    startMark = 0L,
                )
            }
        },
    )

    /**
     * Switches how many regions the screen shows. A count the split layout has no shape for is coerced
     * to the nearest one it does, so an out-of-range persisted value cannot render an empty screen.
     */
    fun withParticipantCount(state: StopwatchScreenState, count: Int): StopwatchScreenState {
        val allowed = StopwatchScreenState.ALLOWED_COUNTS
            .minByOrNull { kotlin.math.abs(it - count) }
            ?: StopwatchScreenState.SINGLE_PARTICIPANT
        return state.copy(participantCount = allowed)
    }

    private inline fun StopwatchScreenState.mapParticipant(
        participantId: Int,
        transform: (StopwatchParticipant) -> StopwatchParticipant,
    ): StopwatchScreenState = copy(
        participants = participants.map { participant ->
            if (participant.id == participantId) transform(participant) else participant
        },
    )
}
