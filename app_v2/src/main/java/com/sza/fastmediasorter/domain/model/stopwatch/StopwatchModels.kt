package com.sza.fastmediasorter.domain.model.stopwatch

/** One recorded split, numbered from 1 within its participant. */
data class StopwatchLap(
    val ordinal: Int,
    val atElapsedMillis: Long,
)

/**
 * One participant's independent measurement.
 *
 * Elapsed time is never stored. It is derived from [accumulatedMillis] plus the open segment since
 * [startMark], so a rotation, a process pause or a missed repaint cannot lose or double-count it
 * (S1411 §6.6). [startMark] is an [com.sza.fastmediasorter.core.util.ElapsedClock] reading and carries
 * no meaning while [running] is false.
 *
 * [startedAtEpochMillis] is the wall-clock reading taken when this participant last started, and it
 * deliberately survives a stop: the result text reports when the measurement was performed, not when
 * it was halted (S2792). It is null while the participant has never started, and `reset` clears it.
 */
data class StopwatchParticipant(
    val id: Int,
    val running: Boolean = false,
    val startMark: Long = 0L,
    val startedAtEpochMillis: Long? = null,
    val accumulatedMillis: Long = 0L,
    val laps: List<StopwatchLap> = emptyList(),
) {

    fun elapsedAt(nowMillis: Long): Long =
        if (running) accumulatedMillis + (nowMillis - startMark) else accumulatedMillis

    val hasProgress: Boolean
        get() = running || accumulatedMillis > 0L || laps.isNotEmpty()
}

/**
 * The whole screen: always [MAX_PARTICIPANTS] participants, of which the first [participantCount] are
 * shown. Keeping the hidden ones rather than trimming the list means switching the count back does not
 * discard a measurement the user may still want.
 */
data class StopwatchScreenState(
    val participants: List<StopwatchParticipant> = defaultParticipants(),
    val participantCount: Int = SINGLE_PARTICIPANT,
) {

    val visibleParticipants: List<StopwatchParticipant>
        get() = participants.take(participantCount)

    val anyRunning: Boolean
        get() = visibleParticipants.any { it.running }

    companion object {
        const val SINGLE_PARTICIPANT = 1
        const val PAIR_PARTICIPANTS = 2
        const val MAX_PARTICIPANTS = 4

        /** The only counts the split screen is designed for (S1411 §2.4). */
        val ALLOWED_COUNTS = listOf(SINGLE_PARTICIPANT, PAIR_PARTICIPANTS, MAX_PARTICIPANTS)

        fun defaultParticipants(): List<StopwatchParticipant> =
            (0 until MAX_PARTICIPANTS).map { StopwatchParticipant(id = it) }
    }
}
