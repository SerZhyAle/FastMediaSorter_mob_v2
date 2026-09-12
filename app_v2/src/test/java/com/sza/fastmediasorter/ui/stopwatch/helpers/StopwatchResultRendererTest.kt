package com.sza.fastmediasorter.ui.stopwatch.helpers

import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchLap
import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchParticipant
import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S1411 phase 07 - the exported text, and its agreement with the reading on screen. */
class StopwatchResultRendererTest {

    private val labels = StopwatchResultLabels(
        participant = { index -> "Participant ${index + 1}" },
        laps = "laps",
    )

    private val labelsWithStamp = StopwatchResultLabels(
        participant = { index -> "Participant ${index + 1}" },
        laps = "laps",
        measuredAt = { stamp -> "Measured at $stamp" },
    )

    @Test
    fun `a started participant carries the measured-at line between note and readings`() {
        val state = stateOf(
            StopwatchParticipant(id = 0, accumulatedMillis = 1_000L, startedAtEpochMillis = 1_700_000_000_000L),
        )

        val rendered = StopwatchResultRenderer.render(
            state,
            nowMillis = 0L,
            description = "Morning run",
            note = "Cold and windy",
            labels = labelsWithStamp,
        )

        assertEquals(
            listOf(
                "Morning run",
                "Cold and windy",
                "Measured at 1700000000000",
                "Participant 1: 0:01.00",
            ),
            rendered.lines(),
        )
    }

    @Test
    fun `a participant that never started produces no measured-at line`() {
        val state = stateOf(participantOf(id = 0, accumulatedMillis = 1_000L))

        val rendered = StopwatchResultRenderer.render(state, 0L, "", "", labelsWithStamp)

        assertEquals(listOf("Participant 1: 0:01.00"), rendered.lines())
    }

    @Test
    fun `the earliest visible start wins the stamp`() {
        val state = stateOf(
            StopwatchParticipant(id = 0, accumulatedMillis = 1_000L, startedAtEpochMillis = 2_000L),
            StopwatchParticipant(id = 1, accumulatedMillis = 1_000L, startedAtEpochMillis = 1_000L),
            participantCount = StopwatchScreenState.PAIR_PARTICIPANTS,
        )

        val rendered = StopwatchResultRenderer.render(state, 0L, "", "", labelsWithStamp)

        assertEquals("Measured at 1000", rendered.lines().first())
    }

    @Test
    fun `a single participant with no laps renders one line`() {
        val state = stateOf(participantOf(id = 0, accumulatedMillis = 83_450L))

        val rendered = render(state, description = "Morning run", note = "Cold and windy")

        assertEquals(
            listOf("Morning run", "Cold and windy", "Participant 1: 1:23.45"),
            rendered.lines(),
        )
    }

    @Test
    fun `four participants render their own lap counts independently`() {
        val state = stateOf(
            participantOf(id = 0, accumulatedMillis = 5_000L, lapMarks = listOf(2_000L)),
            participantOf(id = 1, accumulatedMillis = 6_000L),
            participantOf(id = 2, accumulatedMillis = 7_000L, lapMarks = listOf(1_000L, 4_000L, 6_500L)),
            participantOf(id = 3, accumulatedMillis = 8_000L, lapMarks = listOf(3_000L, 7_000L)),
            participantCount = StopwatchScreenState.MAX_PARTICIPANTS,
        )

        val lines = render(state, description = "", note = "").lines()

        assertEquals(StopwatchScreenState.MAX_PARTICIPANTS, lines.size)
        assertEquals("Participant 1: 0:05.00 (laps 0:02.00)", lines[0])
        assertEquals("Participant 2: 0:06.00", lines[1])
        assertEquals("Participant 3: 0:07.00 (laps 0:01.00, 0:04.00, 0:06.50)", lines[2])
        assertEquals("Participant 4: 0:08.00 (laps 0:03.00, 0:07.00)", lines[3])
    }

    @Test
    fun `an empty description and note are omitted rather than left blank`() {
        val state = stateOf(participantOf(id = 0, accumulatedMillis = 1_000L))

        val lines = render(state, description = "   ", note = "").lines()

        assertEquals(listOf("Participant 1: 0:01.00"), lines)
        assertFalse("no line may be blank", lines.any { it.isBlank() })
    }

    @Test
    fun `a hidden participant is never exported`() {
        val state = stateOf(
            participantOf(id = 0, accumulatedMillis = 1_000L),
            participantOf(id = 1, accumulatedMillis = 2_000L),
            participantCount = StopwatchScreenState.SINGLE_PARTICIPANT,
        )

        val lines = render(state, description = "", note = "").lines()

        assertEquals(listOf("Participant 1: 0:01.00"), lines)
    }

    @Test
    fun `a running participant is rendered from the clock reading it was given`() {
        val state = stateOf(
            StopwatchParticipant(id = 0, running = true, startMark = 1_000L, accumulatedMillis = 500L),
        )

        val rendered = render(state, description = "", note = "", nowMillis = 3_000L)

        assertEquals("Participant 1: 0:02.50", rendered)
    }

    @Test
    fun `the exported total is the string the screen paints`() {
        val elapsed = 3_723_456L
        val state = stateOf(participantOf(id = 0, accumulatedMillis = elapsed))

        val rendered = render(state, description = "", note = "")

        // The file and the large reading share one formatter, so an hour-band change cannot make the two
        // disagree without this failing first (§11.7).
        assertTrue(rendered.endsWith(StopwatchTimeFormatter.format(elapsed)))
    }

    private fun render(
        state: StopwatchScreenState,
        description: String,
        note: String,
        nowMillis: Long = 0L,
    ): String = StopwatchResultRenderer.render(state, nowMillis, description, note, labels)

    private fun participantOf(
        id: Int,
        accumulatedMillis: Long,
        lapMarks: List<Long> = emptyList(),
    ): StopwatchParticipant = StopwatchParticipant(
        id = id,
        accumulatedMillis = accumulatedMillis,
        laps = lapMarks.mapIndexed { index, mark -> StopwatchLap(ordinal = index + 1, atElapsedMillis = mark) },
    )

    private fun stateOf(
        vararg participants: StopwatchParticipant,
        participantCount: Int = StopwatchScreenState.SINGLE_PARTICIPANT,
    ): StopwatchScreenState {
        val filled = StopwatchScreenState.defaultParticipants().toMutableList()
        participants.forEach { filled[it.id] = it }
        return StopwatchScreenState(participants = filled, participantCount = participantCount)
    }
}
