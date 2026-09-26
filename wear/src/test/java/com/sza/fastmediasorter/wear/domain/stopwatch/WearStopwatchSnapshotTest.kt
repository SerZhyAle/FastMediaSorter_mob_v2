package com.sza.fastmediasorter.wear.domain.stopwatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearStopwatchSnapshotTest {

    private val saved = WearStopwatchClockReading(
        elapsedRealtimeMillis = 50_000L,
        wallClockMillis = 1_000_000L,
        bootCount = 7
    )

    private val mixed = WearStopwatchState(
        listOf(
            WearStopwatchParticipant(
                index = 0,
                startedAtMillis = 40_000L,
                accumulatedMillis = 1_500L,
                laps = listOf(WearStopwatchLap(1, 3_000L, 3_000L), WearStopwatchLap(2, 5_500L, 2_500L))
            ),
            WearStopwatchParticipant(index = 1, startedAtMillis = null, accumulatedMillis = 9_000L),
            WearStopwatchParticipant(index = 2),
            WearStopwatchParticipant(index = 3, startedAtMillis = 45_000L)
        )
    )

    @Test
    fun `a mixed measurement survives the round trip on the same boot`() {
        val later = saved.copy(elapsedRealtimeMillis = 90_000L, wallClockMillis = 1_040_000L)

        assertEquals(mixed, WearStopwatchSnapshot.decode(WearStopwatchSnapshot.encode(mixed, saved), later))
    }

    @Test
    fun `after a reboot a running participant keeps counting from its real start`() {
        val encoded = WearStopwatchSnapshot.encode(mixed, saved)
        // The watch restarted: the monotonic clock begins again, the wall clock moved on by 60 s.
        val afterReboot = WearStopwatchClockReading(
            elapsedRealtimeMillis = 8_000L,
            wallClockMillis = 1_060_000L,
            bootCount = 8
        )

        val restored = requireNotNull(WearStopwatchSnapshot.decode(encoded, afterReboot))

        // Participant 0 had run 10 s before the save, 60 s passed since: 70 s running plus 1.5 s banked.
        assertEquals(71_500L, restored.participants[0].elapsedAt(afterReboot.elapsedRealtimeMillis))
        assertEquals(65_000L, restored.participants[3].elapsedAt(afterReboot.elapsedRealtimeMillis))
        assertEquals(mixed.participants[1], restored.participants[1])
        assertEquals(mixed.participants[0].laps, restored.participants[0].laps)
    }

    @Test
    fun `nothing stored decodes to nothing`() {
        assertNull(WearStopwatchSnapshot.decode(null, saved))
        assertNull(WearStopwatchSnapshot.decode("", saved))
    }

    @Test
    fun `a garbled record decodes to nothing rather than to a guess`() {
        val encoded = WearStopwatchSnapshot.encode(mixed, saved)

        assertNull(WearStopwatchSnapshot.decode(encoded.replace("9000", "9x00"), saved))
        assertNull(WearStopwatchSnapshot.decode(encoded.replace("1:3000:3000", "1:3000"), saved))
        assertNull(WearStopwatchSnapshot.decode("not a snapshot", saved))
    }

    @Test
    fun `an unknown version decodes to nothing`() {
        val encoded = WearStopwatchSnapshot.encode(mixed, saved)

        assertNull(WearStopwatchSnapshot.decode("9" + encoded.drop(1), saved))
    }
}
