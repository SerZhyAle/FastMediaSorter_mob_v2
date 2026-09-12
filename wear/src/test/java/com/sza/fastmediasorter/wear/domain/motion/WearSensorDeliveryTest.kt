package com.sza.fastmediasorter.wear.domain.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearSensorDeliveryTest {

    @Test
    fun `the first event of a session is counted and timestamped`() {
        val folded = accumulate(
            previous = WearSensorDelivery.EMPTY,
            eventAtMillis = SESSION_START + 500L,
            sessionStartMillis = SESSION_START
        )

        assertEquals(1, folded.eventCount)
        assertEquals(SESSION_START + 500L, folded.lastEventAtMillis)
        assertEquals(2.0, folded.hertz, TOLERANCE)
    }

    @Test
    fun `the rate is the whole session's average, not the gap to the previous event`() {
        var folded = WearSensorDelivery.EMPTY
        repeat(times = 10) { index ->
            folded = accumulate(
                previous = folded,
                eventAtMillis = SESSION_START + (index + 1) * 100L,
                sessionStartMillis = SESSION_START
            )
        }

        assertEquals(10, folded.eventCount)
        assertEquals(10.0, folded.hertz, TOLERANCE)
    }

    @Test
    fun `an event in the session's own millisecond reports no rate instead of infinity`() {
        val folded = accumulate(
            previous = WearSensorDelivery.EMPTY,
            eventAtMillis = SESSION_START,
            sessionStartMillis = SESSION_START
        )

        assertEquals(1, folded.eventCount)
        assertEquals(0.0, folded.hertz, TOLERANCE)
    }

    @Test
    fun `a stream that delivered nothing has no age at all, which is not an age of zero`() {
        assertNull(deliveryAgeMillis(WearSensorDelivery.EMPTY, SESSION_START + 5000L))
    }

    @Test
    fun `the age grows while a registered stream stays silent`() {
        val folded = accumulate(
            previous = WearSensorDelivery.EMPTY,
            eventAtMillis = SESSION_START,
            sessionStartMillis = SESSION_START
        )

        assertEquals(3000L, deliveryAgeMillis(folded, SESSION_START + 3000L))
    }

    @Test
    fun `a clock that stepped backwards reports no negative age`() {
        val folded = accumulate(
            previous = WearSensorDelivery.EMPTY,
            eventAtMillis = SESSION_START + 1000L,
            sessionStartMillis = SESSION_START
        )

        assertEquals(0L, deliveryAgeMillis(folded, SESSION_START))
    }

    private companion object {
        const val SESSION_START = 1_700_000_000_000L
        const val TOLERANCE = 0.0001
    }
}
